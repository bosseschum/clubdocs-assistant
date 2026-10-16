package dev.bosse.clubdocs.document;

import java.util.UUID;

/** Thrown when a document id does not exist. Mapped to HTTP 404 in ApiExceptionHandler. */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID id) {
        super("Document not found: " + id);
    }
}