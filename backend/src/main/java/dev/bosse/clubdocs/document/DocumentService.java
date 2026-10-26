package dev.bosse.clubdocs.document;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import dev.bosse.clubdocs.storage.FileStorage;

/**
 * Business logic. Knows nothing about HTTP status codes or JSON.
 * Failures are reported by throwing exceptions (see ApiExceptionHandler).
 */
@Service
public class DocumentService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final DocumentRepository documentRepository;
    private final FileStorage fileStorage;
    private final ApplicationEventPublisher eventPublisher;

    // Constructor injection: Spring provides the repository, the file storage and the event publisher.
    public DocumentService(DocumentRepository documentRepository, FileStorage fileStorage,
                           ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.fileStorage = fileStorage;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Register an uploaded PDF: save its metadata, store the file, and announce the upload so
     * ingestion can start (after this transaction has committed, see IngestionListener).
     */
    @Transactional
    public Document create(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must exist and not be empty");
        }
        // Only PDFs can be ingested. A client can lie about the content type, but then the PDF
        // reader fails later and the document ends up FAILED, which is acceptable.
        if (!PDF_CONTENT_TYPE.equals(file.getContentType())) {
            throw new IllegalArgumentException("Only PDF files are supported");
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename()
                : "file" + System.currentTimeMillis();
        String contentType = file.getContentType();
        long size = file.getSize();

        Document document = new Document(filename, contentType, size);
        Document savedDocument = documentRepository.save(document);

        // If storing fails, the exception rolls the transaction back: no row without a file.
        fileStorage.store(savedDocument.getId(), file);
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
     * Delete a document by id, together with its stored file and (via an event) its chunks.
     */
    @Transactional
    public void delete(UUID id) {
        if (!documentRepository.existsById(id)) {
            throw new DocumentNotFoundException(id);
        }

        documentRepository.deleteById(id);
        fileStorage.delete(id);
        eventPublisher.publishEvent(new DocumentDeletedEvent(id));
    }

    @Transactional
    public void updateStatus(UUID id, DocumentStatus status) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        document.setStatus(status);
    }

    /**
     * Look up filenames for a set of document IDs. Used by the chat retrieval service to
     * enrich vector-search results with source filenames.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> findFilenames(Set<UUID> ids) {
        return documentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Document::getId, Document::getFilename));
    }
}