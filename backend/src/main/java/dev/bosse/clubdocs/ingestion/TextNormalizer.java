package dev.bosse.clubdocs.ingestion;

/**
 * Cleans text extracted from PDFs before it is chunked.
 *
 * PDF text extraction keeps the page LAYOUT: columns and tables come out as long runs of spaces,
 * and lines are padded. Those spaces use up the chunk size (800 characters) without carrying any
 * meaning, and they dilute what the embedding model sees. The contract is defined by
 * TextNormalizerTest.
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("\r\n", "\n")
                .replaceAll("\r", "\n")
                .replaceAll("[ \t\u00A0]+", " ")
                .replaceAll("(?m)^ +| +$", "")
                .replaceAll("(?m)\n{3,}", "\n\n")
                .trim();
    }
}
