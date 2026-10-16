package dev.bosse.clubdocs.document;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure unit test: no Spring, no database. Mockito replaces the repository.
 * Run with: ./mvnw test
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    DocumentRepository documentRepository;

    @InjectMocks
    DocumentService documentService;

    @Test
    void delete_throwsWhenDocumentDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsById(id)).thenReturn(false);

        assertThrows(DocumentNotFoundException.class, () -> documentService.delete(id));

        verify(documentRepository, never()).deleteById(any());
    }

    // TODO (you), same pattern (arrange, act, assert):
    //  - delete_removesDocumentWhenItExists
    //  - create_rejectsEmptyFile        (use MockMultipartFile from org.springframework.mock.web)
    //  - create_savesDocumentWithFileMetadata   (capture the saved Document with ArgumentCaptor)
    @Test 
    void delete_removesDocumentWhenItExists() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsById(id)).thenReturn(true);
        documentService.delete(id);
        verify(documentRepository).deleteById(id);
    }

    @Test 
    void create_rejectsEmptyFile() {
        var file = new org.springframework.mock.web.MockMultipartFile("file", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> documentService.create(file));
    }

    @Test 
    void create_savesDocumentWithFileMetadata() {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "test.txt", "text/plain", "Hello, World!".getBytes());
        documentService.create(file);
        verify(documentRepository).save(any(Document.class));
    }
}