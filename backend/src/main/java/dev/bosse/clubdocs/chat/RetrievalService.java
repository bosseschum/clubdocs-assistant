package dev.bosse.clubdocs.chat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import dev.bosse.clubdocs.document.DocumentService;
import dev.bosse.clubdocs.ingestion.IngestionService;

/**
 * Finds the chunks that best match a question (vector similarity search) and enriches them
 * with the document's filename, so they can be shown as sources.
 *
 * "Document" here is Spring AI's class. Filenames come from DocumentService as a plain map, so
 * the JPA entity (same name) never appears in this package.
 */
@Service
public class RetrievalService {

    /**
     * How many chunks go into the prompt. Each chunk costs prompt-reading time on a CPU
     * (about 15 to 20 seconds for four chunks, as far as measured with a 4B model), so this is a
     * trade-off between answer quality and latency. Milestone 5 is where you tune it.
     */
    static final int TOP_K = 4;

    private final VectorStore vectorStore;
    private final DocumentService documentService;

    public RetrievalService(VectorStore vectorStore, DocumentService documentService) {
        this.vectorStore = vectorStore;
        this.documentService = documentService;
    }

    /** Best matches first. An empty list means nothing was found. */
    public List<RetrievedChunk> retrieve(String question) {
        return retrieve(question, TOP_K);
    }

    /** Best matches first, with a custom top-K. An empty list means nothing was found. */
    public List<RetrievedChunk> retrieve(String question, int topK) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(topK).build());
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }

        Set<UUID> documentIds = hits.stream()
                .map(RetrievalService::documentIdOf)
                .collect(Collectors.toSet());
        Map<UUID, String> filenames = documentService.findFilenames(documentIds);

        return hits.stream()
                .map(hit -> new RetrievedChunk(
                        documentIdOf(hit),
                        filenames.getOrDefault(documentIdOf(hit), "unknown document"),
                        pageOf(hit),
                        Objects.toString(hit.getText(), "")))
                .toList();
    }

    private static UUID documentIdOf(Document hit) {
        return UUID.fromString(String.valueOf(hit.getMetadata().get(IngestionService.META_DOCUMENT_ID)));
    }

    /** The page may be stored as a number or as a string, depending on how it was ingested. */
    private static int pageOf(Document hit) {
        Object page = hit.getMetadata().get(IngestionService.META_PAGE);
        if (page instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(page));
        } catch (NumberFormatException e) {
            return 0; // unknown page
        }
    }
}
