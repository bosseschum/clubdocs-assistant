package dev.bosse.clubdocs.chat;

import java.util.UUID;

/**
 * One piece of a document that the vector search found for a question, with enough
 * information to show it as a source ("bylaws.pdf, page 2").
 */
public record RetrievedChunk(UUID documentId, String filename, int page, String text) {}