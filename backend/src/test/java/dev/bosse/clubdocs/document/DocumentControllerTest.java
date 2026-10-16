package dev.bosse.clubdocs.document;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

/**
 * Tests the HTTP layer only: routing, status codes, JSON, exception mapping.
 *
 * @WebMvcTest starts just the web parts of Spring (controllers, ApiExceptionHandler,
 * JSON conversion), no database. The service is replaced by a Mockito mock
 * (@MockitoBean), so these tests never need Docker.
 */
@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DocumentService documentService;

    @Test
    void delete_returns404WhenDocumentDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new DocumentNotFoundException(id)).when(documentService).delete(id);

        mockMvc.perform(delete("/api/documents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Document not found: " + id));
    }

    @Test 
    void delete_returns204WhenDocumentExists() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/documents/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test 
    void list_returnsDocumentsAsJsonArray() throws Exception {
        when(documentService.findAll()).thenReturn(List.of(
                new Document("doc1.pdf", "application/pdf", 1000),
                new Document("doc2.pdf", "application/pdf", 2000)
        ));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].filename").value("doc1.pdf"))
                .andExpect(jsonPath("$[1].filename").value("doc2.pdf"));
    }
}