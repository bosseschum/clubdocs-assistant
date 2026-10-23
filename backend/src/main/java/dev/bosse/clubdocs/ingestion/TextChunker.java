package dev.bosse.clubdocs.ingestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits long text into overlapping chunks, so each piece is small enough to
 * embed and
 * consecutive chunks share some context (a sentence cut at a boundary is still
 * found).
 *
 * Pure logic: no Spring, no AI calls. The contract is defined by
 * TextChunkerTest.
 */
public class TextChunker {

    private final int maxChars;
    private final int overlapChars;

    public TextChunker(int maxChars, int overlapChars) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("Maximum chunk length must be positive");
        }
        if (overlapChars < 0) {
            throw new IllegalArgumentException("Overlap cannot be negative");
        }
        if (overlapChars >= maxChars) {
            throw new IllegalArgumentException("Overlap must be smaller than max length");
        }

        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }
        if (text.length() <= maxChars) {
            String chunk = text.trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            return chunks;
        }

        int start = 0;
        int end = Math.min(start + maxChars, text.length());
        int previousChunkStart = -1;
        int previousChunkEnd = -1;

        while (start < text.length()) {
            boolean foundWhitespace = false;
            if (end < text.length()) {
                for (int i = end - 1; i > start; i--) {
                    if (Character.isWhitespace(text.charAt(i))) {
                        end = i;
                        foundWhitespace = true;
                        break;
                    }
                }
            }

            String chunk = text.substring(start, end).trim();
            boolean containedInPrevious = previousChunkStart <= start && previousChunkEnd >= end;
            if (!chunk.isEmpty() && !containedInPrevious) {
                chunks.add(chunk);
                previousChunkStart = start;
                previousChunkEnd = end;
            }
            if (end == text.length()) {
                break;
            }

            if (!foundWhitespace) {
                start = end;
                while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                    start++;
                }
                end = Math.min(start + maxChars, text.length());
                continue;
            }

            int previousStart = start;
            if (end - overlapChars > start) {
                start = end - overlapChars;
            } else {
                start += 1;
            }

            while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
                start--;
            }
            if (start <= previousStart) {
                start = end;
            }
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            end = Math.min(start + maxChars, text.length());
        }

        return chunks;
    }
}