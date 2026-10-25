package dev.bosse.clubdocs.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import dev.bosse.clubdocs.document.DocumentService;
import dev.bosse.clubdocs.document.DocumentStatus;
import dev.bosse.clubdocs.storage.FileStorage;

/**
 * Unit test for the ingestion orchestration. No Spring, no database, no embedding model:
 * the collaborators are Mocks, and readPages (which would open a real PDF) is replaced
 * on a Mockito spy, so we only test OUR logic: statuses, order of steps, error handling.
 *
 * Note: this class imports Spring AI's Document, not the JPA entity (same name, another package).
 */
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    private static final String META_DOCUMENT_ID = UUID.randomUUID().toString();
    private static final String META_PAGE = "page";
    private static final String META_CHUNK_INDEX = "chunkIndex";
    @Mock
    DocumentService documentService;

    @Mock
    FileStorage fileStorage;

    @Mock
    VectorStore vectorStore;

    IngestionService service;

    @BeforeEach
    void setUp() {
        // A spy is a real object whose methods can be selectively replaced.
        service = spy(new IngestionService(documentService, fileStorage, vectorStore));
    }

    @Test
    void ingest_storesChunksAndMarksDocumentReady() {
        UUID id = UUID.randomUUID();
        List<Document> pages = List.of(new Document("Hello club members.", Map.of("page_number", 1)));
        doReturn(pages).when(service).readPages(id);

        service.ingest(id);

        InOrder order = inOrder(documentService, vectorStore);
        order.verify(documentService).updateStatus(id, DocumentStatus.PROCESSING);
        order.verify(vectorStore).add(anyList());
        order.verify(documentService).updateStatus(id, DocumentStatus.READY);
    }

    @Test
    void ingest_marksDocumentFailedWhenReadingThrows_andDoesNotPropagate() {
        UUID id = UUID.randomUUID();
        doThrow(new RuntimeException("corrupt pdf")).when(service).readPages(id);

        service.ingest(id); // must not throw: nobody is waiting on the background thread

        verify(documentService).updateStatus(id, DocumentStatus.FAILED);
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void ingest_marksDocumentFailedWhenThePdfHasNoText() {
        // A scanned PDF gives no extractable text. READY with zero chunks would look healthy,
        // but the document could never be found by a search. This test fails until you fix that.
        UUID id = UUID.randomUUID();
        doReturn(List.of()).when(service).readPages(id);

        service.ingest(id);

        verify(documentService).updateStatus(id, DocumentStatus.FAILED);
        verify(documentService, never()).updateStatus(id, DocumentStatus.READY);
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void toChunkDocuments_putsDocumentIdPageAndChunkIndexIntoMetadata() {
        UUID id = UUID.randomUUID();
        List<Document> pages = List.of(
                new Document("Some text.", Map.of("page_number", 42, "filename", "report.pdf"))
        );

        List<Document> chunks = service.toChunkDocuments(id, pages);

        assertEquals(1, chunks.size());
        Document chunk = chunks.get(0);
        assertEquals(id, chunk.getMetadata().get(IngestionService.META_DOCUMENT_ID));
        assertEquals(42, chunk.getMetadata().get(IngestionService.META_PAGE));
        assertEquals(0, chunk.getMetadata().get(IngestionService.META_CHUNK_INDEX));
    }

    @Test
    void toChunkDocuments_splitsLongPageIntoSeveralChunks() {
        UUID id = UUID.randomUUID();
        // Build a string longer than CHUNK_SIZE_CHARS (800) so it splits into multiple chunks
        String longText = "x".repeat(2000);
        List<Document> pages = List.of(new Document(longText, Map.of("page_number", 1)));

        List<Document> chunks = service.toChunkDocuments(id, pages);

        assertEquals(3, chunks.size());
        assertEquals(0, chunks.get(0).getMetadata().get(IngestionService.META_CHUNK_INDEX));
        assertEquals(1, chunks.get(1).getMetadata().get(IngestionService.META_CHUNK_INDEX));
        assertEquals(2, chunks.get(2).getMetadata().get(IngestionService.META_CHUNK_INDEX));
    }

    @Test
    void toChunkDocuments_keepsTextOfAPageWithoutPageNumber() {
        UUID id = UUID.randomUUID();
        // Page metadata has no "page_number" key
        List<Document> pages = List.of(new Document("Orphan page text.", Map.of("filename", "scan.pdf")));

        List<Document> chunks = service.toChunkDocuments(id, pages);

        assertEquals(1, chunks.size());
        assertEquals("Orphan page text.", chunks.get(0).getText());
        assertEquals(id, chunks.get(0).getMetadata().get(IngestionService.META_DOCUMENT_ID));
        // Page metadata should be absent when page_number is missing
        assertEquals(null, chunks.get(0).getMetadata().get(IngestionService.META_PAGE));
    }

    @Test
    void deleteChunks_deletesByDocumentIdFilter() {
        UUID id = UUID.randomUUID();

        service.deleteChunks(id);

        verify(vectorStore).delete("documentId == '" + id + "'");
    }
}