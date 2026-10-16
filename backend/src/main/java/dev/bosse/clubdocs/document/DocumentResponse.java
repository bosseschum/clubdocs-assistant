package dev.bosse.clubdocs.document;

import java.time.Instant;
import java.util.UUID;

/**
 * What the API returns for a document. A plain record, not a Spring bean.
 * Jackson turns it into JSON. The entity is never exposed directly.
 */
public record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        Instant uploadedAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus(),
                document.getUploadedAt());
    }
}