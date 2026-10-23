package dev.bosse.clubdocs.document;

import java.util.UUID;

/**
 * Published by DocumentService after a document was saved. The ingestion
 * package reacts to it,
 * so the document package does not need to know that ingestion exists.
 */
public record DocumentUploadedEvent(UUID documentId) {
}