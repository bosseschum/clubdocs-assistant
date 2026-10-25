package dev.bosse.clubdocs.ingestion;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import dev.bosse.clubdocs.document.DocumentService;
import dev.bosse.clubdocs.document.DocumentStatus;
import dev.bosse.clubdocs.storage.FileStorage;

/**
 * Turns an uploaded PDF into searchable chunks: read pages, split into chunks,
 * embed, store.
 *
 * NAME CLASH: "Document" here is Spring AI's class (a piece of text plus
 * metadata).
 * Your JPA entity is also called Document but lives in the document package.
 * This class never
 * touches the entity: status changes go through DocumentService, which avoids
 * the clash.
 *
 * No @Transactional on ingest(): embedding can take a while, and a database
 * transaction
 * should not stay open that long. Each status update is its own short
 * transaction.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    // Metadata key holding the id of the uploaded document. deleteChunks() filters
    // on it.
    public static final String META_DOCUMENT_ID = "documentId";
    public static final String META_PAGE = "page";
    public static final String META_CHUNK_INDEX = "chunkIndex";

    // The default embedding model only looks at roughly the first 256 tokens of its
    // input
    // (as far as I know) and silently ignores the rest, so chunks must not be much
    // larger.
    // These values are a starting point. Milestone 5 (evaluation) is where you tune
    // them.
    private static final int CHUNK_SIZE_CHARS = 800;
    private static final int CHUNK_OVERLAP_CHARS = 150;

    private final TextChunker chunker = new TextChunker(CHUNK_SIZE_CHARS, CHUNK_OVERLAP_CHARS);

    private final DocumentService documentService;
    private final FileStorage fileStorage;
    private final VectorStore vectorStore;

    public IngestionService(DocumentService documentService, FileStorage fileStorage, VectorStore vectorStore) {
        this.documentService = documentService;
        this.fileStorage = fileStorage;
        this.vectorStore = vectorStore;
    }

    /**
     * Runs in the background after an upload (see IngestionListener).
     */
    public void ingest(UUID documentId) {
        try {
            documentService.updateStatus(documentId, DocumentStatus.PROCESSING);

            List<Document> pages = readPages(documentId);
            List<Document> chunks = toChunkDocuments(documentId, pages);

            if (chunks.isEmpty()) {
                throw new IllegalStateException("No extractable text found in document " + documentId);
            }

            vectorStore.add(chunks);
            documentService.updateStatus(documentId, DocumentStatus.READY);
        } catch (Exception e) {
            log.error("Ingestion failed for document {}", documentId, e);
            try {
                documentService.updateStatus(documentId, DocumentStatus.FAILED);
            } catch (Exception statusException) {
                log.error("Failed to update status to FAILED for document {}", documentId, statusException);
            }
        }
    }

    /**
     * Converts page documents into chunk documents ready for the vector store.
     */
    List<Document> toChunkDocuments(UUID documentId, List<Document> pages) {
        List<Document> chunks = new ArrayList<>();

        if (pages.isEmpty()) {
            log.warn("Document {} has no extractable text", documentId);
        }

        int chunkIndex = 0;

        for (int i = 0; i < pages.size(); i++) {
            Document page = pages.get(i);
            Object pageNumberValue = page.getMetadata().get("page_number");
            Integer pageNumber = pageNumberValue == null ? null : Integer.parseInt(String.valueOf(pageNumberValue));

            if (pageNumber == null) {
                log.warn("Page {} of document {} has no page_number metadata, chunks will be created without page info", i, documentId);
            }

            String text = page.getText();
            List<String> pageChunks = chunker.chunk(TextNormalizer.normalize(text));

            for (String chunkText : pageChunks) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put(META_DOCUMENT_ID, documentId);
                if (pageNumber != null) {
                    metadata.put(META_PAGE, pageNumber);
                }
                metadata.put(META_CHUNK_INDEX, chunkIndex);
                Document chunkDoc = new Document(chunkText, metadata);
                chunks.add(chunkDoc);
                chunkIndex++;
            }
        }
        return chunks;
    }

    /**
     * Reads the stored PDF, one Spring AI Document per page (metadata includes
     * "page_number").
     */
    List<Document> readPages(UUID documentId) {
        return new PagePdfDocumentReader(fileStorage.load(documentId)).get();
    }

    /** Removes all chunks of a document from the vector store. */
    public void deleteChunks(UUID documentId) {
        // Filter expression over the metadata column. documentId is a UUID, so it
        // cannot contain quotes.
        vectorStore.delete(META_DOCUMENT_ID + " == '" + documentId + "'");
        log.info("Deleted chunks of document {}", documentId);
    }
}