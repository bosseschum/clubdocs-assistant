package dev.bosse.clubdocs.document;

import java.util.UUID;

/**
 * Published by DocumentService after a document was deleted, so its chunks can
 * be removed.
 */
public record DocumentDeletedEvent(UUID documentId) {
}