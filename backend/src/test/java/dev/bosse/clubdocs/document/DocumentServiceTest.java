package dev.bosse.clubdocs.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import dev.bosse.clubdocs.storage.FileStorage;

/**
 * Pure unit test: no Spring, no database. Mockito replaces the repository.
 * Run with: ./mvnw test
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    DocumentRepository documentRepository;

    @Mock
    FileStorage fileStorage;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    DocumentService documentService;

    @Test
    void delete_throwsWhenDocumentDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsById(id)).thenReturn(false);

        assertThrows(DocumentNotFoundException.class, () -> documentService.delete(id));

        verify(documentRepository, never()).deleteById(any());
    }

    @Test
    void delete_removesDocumentWhenItExists() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsById(id)).thenReturn(true);
        documentService.delete(id);
        verify(documentRepository).deleteById(id);
    }

    @Test
    void delete_publishesDocumentDeletedEvent() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsById(id)).thenReturn(true);

        documentService.delete(id);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(new DocumentDeletedEvent(id), eventCaptor.getValue());
    }

    @Test
    void create_rejectsEmptyFile() {
        var file = new org.springframework.mock.web.MockMultipartFile("file", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> documentService.create(file));
    }

    @Test
    void create_savesDocumentWithFileMetadata() {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "test.pdf", "application/pdf",
                "Hello, World!".getBytes());
        Document savedDocument = mock(Document.class);
        when(savedDocument.getId()).thenReturn(UUID.randomUUID());
        when(documentRepository.save(any(Document.class))).thenReturn(savedDocument);

        documentService.create(file);

        verify(documentRepository).save(any(Document.class));
    }

    @Test
    void create_publishesDocumentUploadedEvent() {
        UUID id = UUID.randomUUID();
        Document savedDocument = mock(Document.class);
        when(savedDocument.getId()).thenReturn(id);
        when(documentRepository.save(any(Document.class))).thenReturn(savedDocument);
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf",
                "Hello, World!".getBytes());

        documentService.create(file);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(new DocumentUploadedEvent(id), eventCaptor.getValue());
    }

    @Test
    void create_rejectsNonPdfFile() {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "test.txt", "text/plain",
                "Hello, World!".getBytes());
        assertThrows(IllegalArgumentException.class, () -> documentService.create(file));
    }

    @Test
    void create_storesPdfFile() {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "test.pdf", "application/pdf",
                "Hello, World!".getBytes());
        Document savedDocument = mock(Document.class);
        when(savedDocument.getId()).thenReturn(UUID.randomUUID());
        when(documentRepository.save(any(Document.class))).thenReturn(savedDocument);
        documentService.create(file);
        verify(documentRepository).save(any(Document.class));
    }
}