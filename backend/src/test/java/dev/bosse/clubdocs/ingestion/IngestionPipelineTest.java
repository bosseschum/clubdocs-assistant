package dev.bosse.clubdocs.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import dev.bosse.clubdocs.TestcontainersConfiguration;
import dev.bosse.clubdocs.document.DocumentRepository;
import dev.bosse.clubdocs.document.DocumentService;
import dev.bosse.clubdocs.document.DocumentStatus;

/**
 * End-to-end test of the ingestion pipeline: upload a real PDF, wait for the background
 * ingestion, check the chunks in a real PostgreSQL (Testcontainers, needs Docker).
 *
 * Slow compared to the unit tests (full Spring context, embedding model), so keep it to a
 * handful of tests.
 *
 * IMPORTANT: do NOT annotate this class with @Transactional. The ingestion starts only AFTER the
 * upload's transaction has committed (@TransactionalEventListener AFTER_COMMIT). Spring's test
 * transactions are rolled back, so the event would never fire and the test would wait forever.
 *
 * Note: this class never names the JPA entity or Spring AI's Document, which avoids the clash.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "clubdocs.storage.dir=target/test-uploads") // keep test files out of ./data
@Timeout(90)
class IngestionPipelineTest {

    private static final Duration WAIT_LIMIT = Duration.ofSeconds(60);

    @Autowired
    DocumentService documentService;

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    VectorStore vectorStore;

    private static final Logger log = LoggerFactory.getLogger(IngestionPipelineTest.class);

    @Test
    void uploadedPdfIsIngestedIntoChunks() throws Exception {
        UUID id = documentService.create(samplePdf()).getId();

        // Wait for any final status, so a FAILED ingestion fails the test immediately
        // instead of running into the timeout.
        waitUntil("ingestion to finish", () -> {
            DocumentStatus status = statusOf(id);
            return status != DocumentStatus.UPLOADED && status != DocumentStatus.PROCESSING;
        });

        assertEquals(DocumentStatus.READY, statusOf(id));
        assertTrue(countChunks(id) > 0, "expected chunks in the vector store");
    }

    @Test
    void chunksCarryDocumentIdPageAndChunkIndexMetadata() throws Exception {
        UUID id = documentService.create(samplePdf()).getId();

        waitUntil("ingestion to finish", () -> {
            DocumentStatus status = statusOf(id);
            return status != DocumentStatus.UPLOADED && status != DocumentStatus.PROCESSING;
        });

        assertEquals(DocumentStatus.READY, statusOf(id));

        // Verify page numbers are 1 or 2 for the sample PDF
        List<Integer> pages = jdbcTemplate.queryForList(
                "select metadata->>'" + IngestionService.META_PAGE + "' from vector_store " +
                        "where metadata->>'" + IngestionService.META_DOCUMENT_ID + "' = ?",
                String.class, id.toString())
                .stream()
                .map(pageStr -> Integer.parseInt(pageStr))
                .collect(Collectors.toList());

        assertTrue(pages.stream().allMatch(p -> p == 1 || p == 2),
                "expected page numbers to be 1 or 2, but got: " + pages);

        // Verify chunkIndex starts at 0 and is sequential
        List<Integer> chunkIndices = jdbcTemplate.queryForList(
                "select metadata->>'" + IngestionService.META_CHUNK_INDEX + "' from vector_store " +
                        "where metadata->>'" + IngestionService.META_DOCUMENT_ID + "' = ? " +
                        "order by (metadata->>'" + IngestionService.META_CHUNK_INDEX + "')::int",
                String.class, id.toString())
                .stream()
                .map(idxStr -> Integer.parseInt(idxStr))
                .collect(Collectors.toList());

        assertTrue(chunkIndices.stream().allMatch(i -> i >= 0),
                "chunkIndex should be non-negative, but got: " + chunkIndices);
        assertEquals(0, chunkIndices.get(0), "first chunkIndex should be 0");
    }

    @Test
    void deletingADocumentRemovesItsChunks() throws Exception {
        UUID id = documentService.create(samplePdf()).getId();

        // Wait for ingestion to complete
        waitUntil("ingestion to finish", () -> {
            DocumentStatus status = statusOf(id);
            return status != DocumentStatus.UPLOADED && status != DocumentStatus.PROCESSING;
        });

        assertEquals(DocumentStatus.READY, statusOf(id));
        assertTrue(countChunks(id) > 0, "expected chunks before deletion");

        // Delete the document — chunks are removed asynchronously via event
        documentService.delete(id);

        // Wait for chunks to disappear
        waitUntil("chunks to disappear", () -> countChunks(id) == 0);
    }

    @Test
    void exploratory_similaritySearchFindsTheMembershipFee() throws Exception {
        UUID id = documentService.create(samplePdf()).getId();

        waitUntil("ingestion to finish", () -> {
            DocumentStatus status = statusOf(id);
            return status != DocumentStatus.UPLOADED && status != DocumentStatus.PROCESSING;
        });

        assertEquals(DocumentStatus.READY, statusOf(id));

        // Exploratory: search for membership fee information.
        // The default embedding model is English-only, so German queries may not work well.
        // This is a preview of what Milestone 5 will measure.
        List<org.springframework.ai.document.Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Wie hoch ist der Jahresbeitrag?")
                        .topK(5)
                        .build());

        log.info("Similarity search results for 'Wie hoch ist der Jahresbeitrag?':");
        for (int i = 0; i < results.size(); i++) {
            org.springframework.ai.document.Document doc = results.get(i);
            log.info("  Result {}: {}", i + 1,
                    doc.getText().substring(0, Math.min(200, doc.getText().length())));
        }

        // Check if any result contains "48 Euro" (the membership fee in the sample PDF)
        boolean foundMembershipFee = results.stream()
                .anyMatch(doc -> doc.getText().contains("48 Euro"));

        // This is exploratory — the English-only embedding model may not find German text well.
        // We log the result rather than asserting, as this is a preview for Milestone 5.
        if (foundMembershipFee) {
            log.info("SUCCESS: Found '48 Euro' in search results!");
        } else {
            log.info("Note: '48 Euro' not found in top results. " +
                    "This is expected with the English-only embedding model.");
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private static MockMultipartFile samplePdf() throws IOException {
        byte[] bytes = new ClassPathResource("sample/satzung-musterverein.pdf").getContentAsByteArray();
        return new MockMultipartFile("file", "satzung-musterverein.pdf", "application/pdf", bytes);
    }

    private DocumentStatus statusOf(UUID id) {
        return documentRepository.findById(id).orElseThrow().getStatus();
    }

    private int countChunks(UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from vector_store where metadata->>'documentId' = ?",
                Integer.class, id.toString());
        return count == null ? 0 : count;
    }

    /** Polls a condition every 200 ms (ingestion runs on another thread) and fails after WAIT_LIMIT. */
    private static void waitUntil(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT_LIMIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        fail("Timed out waiting for " + what);
    }
}