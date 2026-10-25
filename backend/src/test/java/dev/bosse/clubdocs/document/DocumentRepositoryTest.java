package dev.bosse.clubdocs.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;

import dev.bosse.clubdocs.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;

/**
 * Integration test for the persistence layer, against a real PostgreSQL
 * (Testcontainers).
 *
 * What happens when it runs:
 * - @DataJpaTest starts only the JPA parts of Spring (no web layer, no
 * controllers).
 * - Flyway runs your real migrations on the fresh container database.
 * - Hibernate validates every entity against the migrated schema (ddl-auto:
 * validate).
 * So even an empty test here would have caught the uuid/bigint mismatch.
 * - Each test runs in a transaction that is rolled back afterwards, so tests
 * don't
 * leak data into each other.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class DocumentRepositoryTest {

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void save_persistsDocumentAndGeneratesId() {
        Document saved = documentRepository.save(new Document("sample/sample-bylaws.pdf", "application/pdf", 1234));

        // Without flush + clear, findById would return the SAME object from the
        // persistence context (first-level cache) and never touch the database.
        // flush() sends the INSERT, clear() forgets the cached entity.
        documentRepository.flush();
        entityManager.clear();

        assertNotNull(saved.getId());
        Document reloaded = documentRepository.findById(saved.getId()).orElseThrow();
        assertEquals("sample/sample-bylaws.pdf", reloaded.getFilename());
        assertEquals(DocumentStatus.UPLOADED, reloaded.getStatus());
    }

    @Test
    void findAll_sortedByUploadedAtDescending_returnsNewestFirst() {
        Document doc1 = new Document("doc1.pdf", "application/pdf", 1000);
        Document doc2 = new Document("doc2.pdf", "application/pdf", 1000);
        Document doc3 = new Document("doc3.pdf", "application/pdf", 1000);

        documentRepository.save(doc1);
        documentRepository.save(doc2);
        documentRepository.save(doc3);

        documentRepository.flush();
        entityManager.clear();

        List<Document> documents = documentRepository.findAll(Sort.by(Sort.Direction.DESC, "uploadedAt"));
        assertEquals(3, documents.size());
        assertEquals("doc3.pdf", documents.get(0).getFilename());
        assertEquals("doc2.pdf", documents.get(1).getFilename());
        assertEquals("doc1.pdf", documents.get(2).getFilename());
    }

    @Test
    void save_withNullFilename_isRejectedByDatabase() {
        Document doc = new Document(null, "application/pdf", 1000);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            documentRepository.saveAndFlush(doc);
        });
    }

    @Test
    void existsById_returnsFalseForUnknownId() {

        boolean exists = documentRepository.existsById(UUID.randomUUID());
        assertEquals(false, exists);
    }
}