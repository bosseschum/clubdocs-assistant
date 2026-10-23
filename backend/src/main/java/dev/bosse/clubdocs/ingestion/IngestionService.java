package dev.bosse.clubdocs.ingestion;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import dev.bosse.clubdocs.document.DocumentService;
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
    public static final String META_FILENAME = "filename";
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
     *
     * TODO (you):
     * 1. Set the status to PROCESSING: documentService.updateStatus(documentId,
     * DocumentStatus.PROCESSING)
     * 2. pages = readPages(documentId)
     * 3. chunks = toChunkDocuments(documentId, filename?, pages) (see below; decide
     * where filename comes from)
     * 4. vectorStore.add(chunks) <- this is where the embedding model runs
     * 5. Set the status to READY.
     * 6. If ANYTHING throws: log it (log.error(message, exception)) and set the
     * status to FAILED.
     * An exception must never escape: nobody is waiting for this thread, so it
     * would just vanish.
     * Think about: what if the PDF has no extractable text (a scanned image)? Is
     * that READY or FAILED?
     */
    public void ingest(UUID documentId) {
        throw new UnsupportedOperationException("TODO: implement ingest");
    }

    /**
     * Converts page documents into chunk documents ready for the vector store.
     *
     * TODO (you): for every page
     * - read its text: page.getText() (if that method is missing in your Spring AI
     * version, try getContent())
     * - read its page number: page.getMetadata().get("page_number") (set by the PDF
     * reader)
     * - split the text with chunker.chunk(...)
     * - create one Spring AI Document per chunk:
     * new Document(chunkText, Map.of(META_DOCUMENT_ID, documentId.toString(),
     * META_PAGE, pageNumber, META_CHUNK_INDEX, index, ...))
     * Metadata values must not be null. Keep the documentId as a STRING (the delete
     * filter compares strings).
     * - chunkIndex: counted across the whole document, or per page? Decide, and
     * note it in a comment.
     */
    List<Document> toChunkDocuments(UUID documentId, List<Document> pages) {
        throw new UnsupportedOperationException("TODO: implement toChunkDocuments");
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