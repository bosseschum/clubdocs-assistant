package dev.bosse.clubdocs.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import dev.bosse.clubdocs.TestcontainersConfiguration;
import dev.bosse.clubdocs.chat.ChatAnswer;
import dev.bosse.clubdocs.chat.ChatService;
import dev.bosse.clubdocs.chat.ChatUnavailableException;
import dev.bosse.clubdocs.chat.RetrievedChunk;
import dev.bosse.clubdocs.chat.RetrievalService;
import dev.bosse.clubdocs.document.DocumentRepository;
import dev.bosse.clubdocs.document.DocumentService;
import dev.bosse.clubdocs.document.DocumentStatus;
import tools.jackson.databind.ObjectMapper;

/**
 * The evaluation harness for Milestone 5. It is NOT part of the normal test run (the class name does
 * not end in "Test", so Maven skips it). Run it on purpose:
 *
 *   ./mvnw test -Dtest=EvaluationRun retrieval only (fast, no language model needed)
 *   ./mvnw test -Dtest=EvaluationRun -Deval.answers=true also asks the model every question (Ollama must
 *                                                          be running; about 20 s per question)
 *
 * It starts a fresh PostgreSQL (Testcontainers, needs Docker), ingests the sample PDFs, and writes a
 * Markdown report to backend/target/eval/.
 *
 * Do NOT make this class @Transactional (see IngestionPipelineTest for why).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "clubdocs.storage.dir=target/eval-uploads")
@Timeout(value = 2, unit = TimeUnit.HOURS)
class EvaluationRun {

    private static final List<String> SAMPLE_FILES = List.of(
            "satzung-musterverein.pdf",
            "beitragsordnung.pdf",
            "hallenordnung.pdf",
            "protokoll-mitgliederversammlung-2025.pdf",
            "reisekostenordnung.pdf",
            "datenschutz.pdf");

    /** The largest k for the retrieval table (hit@1, hit@2, hit@4, hit@8 ...). */
    private static final int MAX_K = 8;

    @Autowired
    DocumentService documentService;

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    RetrievalService retrievalService;

    @Autowired
    ChatService chatService;

    @Test
    void evaluate() throws Exception {
        ingestSampleDocuments();

        List<EvalQuestion> questions = loadQuestions();

        String retrievalReport = retrievalStage(questions);
        writeReport("retrieval", retrievalReport);

        if (Boolean.getBoolean("eval.answers")) {
            writeReport("answers", answerStage(questions));
        }
    }

    /**
     * Reads src/test/resources/eval/questions.json into a list of EvalQuestion.
     * Uses Jackson 3 (tools.jackson.databind) as required by Spring Boot 4.
     */
    private List<EvalQuestion> loadQuestions() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        var type = mapper.getTypeFactory().constructCollectionType(List.class, EvalQuestion.class);
        try (var is = new ClassPathResource("eval/questions.json").getInputStream()) {
            return mapper.readValue(is, type);
        }
    }

    /**
     * For every question with evidence, retrieves chunks and computes hit@k metrics.
     * Returns a Markdown report with overall hit@k, per-category hit@4, and misses.
     */
    private String retrievalStage(List<EvalQuestion> questions) {
        List<EvalQuestion> withEvidence = questions.stream()
                .filter(EvalQuestion::hasEvidence)
                .toList();

        // Retrieve up to MAX_K chunks for each question
        record RetrievalResult(EvalQuestion q, List<String> texts, int rank) {}
        List<RetrievalResult> results = new ArrayList<>();
        for (EvalQuestion q : withEvidence) {
            List<RetrievedChunk> chunks = retrievalService.retrieve(q.question(), MAX_K);
            List<String> texts = chunks.stream().map(RetrievedChunk::text).toList();
            int rank = EvalChecks.firstHitRank(texts, q.evidence());
            results.add(new RetrievalResult(q, texts, rank));
        }

        int total = results.size();

        // 1. Overall hit@k table
        int[] ks = {1, 2, 3, 4, 6, 8};
        StringBuilder sb = new StringBuilder();
        sb.append("# Retrieval Evaluation\n\n");
        sb.append("## Overall Hit@K\n\n");
        sb.append("| K | Hits | Percentage |\n");
        sb.append("|---|------|------------|\n");
        for (int k : ks) {
            int hits = (int) results.stream()
                    .filter(r -> EvalChecks.hitAtK(r.texts, r.q.evidence(), k))
                    .count();
            double pct = total > 0 ? 100.0 * hits / total : 0;
            sb.append(String.format("| %d | %d | %.1f%% |\n", k, hits, pct));
        }

        // 2. Hit@4 by category
        sb.append("\n## Hit@4 by Category\n\n");
        sb.append("| Category | Hits | Total | Percentage |\n");
        sb.append("|----------|------|-------|------------|\n");
        for (String category : List.of("fact", "confusable", "conflict")) {
            List<RetrievalResult> catResults = results.stream()
                    .filter(r -> r.q.category().equals(category))
                    .toList();
            int catHits = (int) catResults.stream()
                    .filter(r -> EvalChecks.hitAtK(r.texts, r.q.evidence(), 4))
                    .count();
            int catTotal = catResults.size();
            double pct = catTotal > 0 ? 100.0 * catHits / catTotal : 0;
            sb.append(String.format("| %s | %d | %d | %.1f%% |\n", category, catHits, catTotal, pct));
        }

        // 3. Misses — questions where evidence was NOT found within MAX_K
        List<RetrievalResult> misses = results.stream()
                .filter(r -> r.rank == Integer.MAX_VALUE)
                .toList();
        if (!misses.isEmpty()) {
            sb.append("\n## Misses (evidence not found in top ").append(MAX_K).append(")\n\n");
            for (RetrievalResult r : misses) {
                sb.append("- **").append(r.q.id()).append("** ").append(r.q.question()).append("\n");
                for (int i = 0; i < Math.min(3, r.texts.size()); i++) {
                    String preview = r.texts.get(i).length() > 80
                            ? r.texts.get(i).substring(0, 80) + "..."
                            : r.texts.get(i);
                    sb.append("  - Chunk ").append(i + 1).append(": ").append(preview).append("\n");
                }
            }
        }

        String report = sb.toString();
        System.out.println(report);
        return report;
    }

    /**
     * Asks the model every question and grades each answer.
     * Returns a Markdown report with totals per category and per-question details for incorrect answers.
     */
    private String answerStage(List<EvalQuestion> questions) {
        record AnswerResult(EvalQuestion q, String answer, List<RetrievedChunk> sources,
                            boolean correct, boolean wrongFallback, int invalidCitations,
                            int niceToHaveCount, double seconds) {}

        List<AnswerResult> results = new ArrayList<>();
        for (EvalQuestion q : questions) {
            long start = System.nanoTime();
            ChatAnswer chatAnswer;
            try {
                chatAnswer = chatService.ask(q.question());
            } catch (ChatUnavailableException ex) {
                throw new RuntimeException("Model unavailable — is Ollama running?", ex);
            }
            double seconds = (System.nanoTime() - start) / 1e9;

            String answer = chatAnswer.answer();
            List<RetrievedChunk> sources = chatAnswer.sources();

            boolean correct;
            boolean wrongFallback = false;
            if (q.expectFallback()) {
                // For absent questions, correct means the model returned the fallback
                correct = EvalChecks.isFallback(answer);
            } else {
                // For answerable questions, correct means all mustContain strings are present
                correct = EvalChecks.containsAll(answer, q.mustContain());
                // A wrong fallback is when the model gives up on an answerable question
                wrongFallback = EvalChecks.isFallback(answer);
            }

            int invalidCitations = EvalChecks.invalidCitations(answer, sources.size());
            int niceToHaveCount = EvalChecks.niceToHaveCount(answer, q.niceToHave());

            results.add(new AnswerResult(q, answer, sources, correct, wrongFallback,
                    invalidCitations, niceToHaveCount, seconds));
        }

        // Build report
        StringBuilder sb = new StringBuilder();
        sb.append("# Answer Evaluation\n\n");

        // Totals
        int totalCorrect = (int) results.stream().filter(r -> r.correct).count();
        int totalWrongFallback = (int) results.stream().filter(r -> r.wrongFallback).count();
        int totalInvalidCitations = results.stream().mapToInt(r -> r.invalidCitations).sum();
        double totalSeconds = results.stream().mapToDouble(r -> r.seconds).sum();

        sb.append("## Summary\n\n");
        sb.append("- **Total questions:** ").append(results.size()).append("\n");
        sb.append("- **Correct:** ").append(totalCorrect).append(" / ").append(results.size()).append("\n");
        sb.append("- **Wrong fallbacks:** ").append(totalWrongFallback).append("\n");
        sb.append("- **Invalid citations:** ").append(totalInvalidCitations).append("\n");
        sb.append("- **Total time:** ").append(String.format("%.1f", totalSeconds)).append("s\n");

        // Per-category breakdown
        sb.append("\n## By Category\n\n");
        sb.append("| Category | Correct | Total | Percentage |\n");
        sb.append("|----------|---------|-------|------------|\n");
        for (String category : List.of("fact", "confusable", "conflict", "absent")) {
            List<AnswerResult> catResults = results.stream()
                    .filter(r -> r.q.category().equals(category))
                    .toList();
            if (catResults.isEmpty()) continue;
            int catCorrect = (int) catResults.stream().filter(r -> r.correct).count();
            double pct = 100.0 * catCorrect / catResults.size();
            sb.append(String.format("| %s | %d | %d | %.1f%% |\n", category, catCorrect, catResults.size(), pct));
        }

        // Incorrect answers detail
        List<AnswerResult> incorrect = results.stream()
                .filter(r -> !r.correct)
                .toList();
        if (!incorrect.isEmpty()) {
            sb.append("\n## Incorrect Answers\n\n");
            for (AnswerResult r : incorrect) {
                sb.append("---\n\n");
                sb.append("**").append(r.q.id()).append("** [").append(r.q.category()).append("] ")
                        .append(String.format("%.1f", r.seconds)).append("s\n");
                sb.append("- **Question:** ").append(r.q.question()).append("\n");
                sb.append("- **Answer:** ").append(r.answer).append("\n");
                if (r.wrongFallback) {
                    sb.append("- **Issue:** Wrong fallback (question was answerable)\n");
                } else if (r.q.expectFallback()) {
                    sb.append("- **Issue:** Did not fallback (question had no answer)\n");
                } else {
                    sb.append("- **Must contain:** ").append(r.q.mustContain()).append("\n");
                }
                if (!r.sources.isEmpty()) {
                    sb.append("- **Sources:** ");
                    sb.append(r.sources.stream()
                            .map(s -> s.filename() + " (p." + s.page() + ")")
                            .toList());
                    sb.append("\n");
                }
                if (r.invalidCitations > 0) {
                    sb.append("- **Invalid citations:** ").append(r.invalidCitations).append("\n");
                }
                if (r.niceToHaveCount > 0) {
                    sb.append("- **Nice-to-have hit:** ").append(r.niceToHaveCount)
                            .append("/").append(r.q.niceToHave().size()).append("\n");
                }
            }
        }

        String report = sb.toString();
        System.out.println(report);
        return report;
    }

    // ---- infrastructure ---------------------------------------------------------

    /** Uploads every sample PDF through the real service and waits until all of them are ingested. */
    private void ingestSampleDocuments() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (String filename : SAMPLE_FILES) {
            byte[] bytes = new ClassPathResource("sample/" + filename).getContentAsByteArray();
            ids.add(documentService.create(new MockMultipartFile("file", filename, "application/pdf", bytes)).getId());
        }

        waitUntil("all sample documents to be ingested", () -> ids.stream().allMatch(id -> {
            DocumentStatus status = statusOf(id);
            return status != DocumentStatus.UPLOADED && status != DocumentStatus.PROCESSING;
        }));

        for (UUID id : ids) {
            assertEquals(DocumentStatus.READY, statusOf(id), "ingestion failed for document " + id);
        }
    }

    private DocumentStatus statusOf(UUID id) {
        return documentRepository.findById(id).orElseThrow().getStatus();
    }

    private static void waitUntil(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(300);
        }
        fail("Timed out waiting for " + what);
    }

    /** Writes target/eval/<name>-<timestamp>.md (Git ignores the target folder). */
    private static void writeReport(String name, String markdown) throws IOException {
        Path dir = Path.of("target", "eval");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = dir.resolve(name + "-" + stamp + ".md");
        Files.writeString(file, markdown, StandardCharsets.UTF_8);
        System.out.println("Report written to " + file.toAbsolutePath());
    }
}
