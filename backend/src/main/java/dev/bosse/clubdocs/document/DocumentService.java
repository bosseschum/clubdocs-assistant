package dev.bosse.clubdocs.document;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Business logic. Knows nothing about HTTP status codes or JSON.
 * Failures are reported by throwing exceptions (see ApiExceptionHandler).
 */
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;

    // Constructor injection: Spring provides the repository and event publisher.
    public DocumentService(DocumentRepository documentRepository, ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Register an uploaded file. For now only the metadata is stored.
     */
    @Transactional
    public Document create(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must exist and not be empty");
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file" + System.currentTimeMillis();
        String contentType = file.getContentType();
        long size = file.getSize();

        Document document = new Document(filename, contentType, size);
        Document savedDocument = documentRepository.save(document);
        eventPublisher.publishEvent(new DocumentUploadedEvent(savedDocument.getId()));
        return savedDocument;
    }

    /**
     * List all documents, newest first.
     */
    @Transactional(readOnly = true)
    public List<Document> findAll() {
        return documentRepository.findAll(Sort.by(Sort.Direction.DESC, "uploadedAt"));
    }

    /**
     * Delete a document by id.
     */
    @Transactional
    public void delete(UUID id) {
        if (!documentRepository.existsById(id)) {
            throw new DocumentNotFoundException(id);
        }

        documentRepository.deleteById(id);
        eventPublisher.publishEvent(new DocumentDeletedEvent(id));
    }
}