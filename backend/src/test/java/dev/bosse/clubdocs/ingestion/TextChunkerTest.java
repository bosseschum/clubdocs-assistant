package dev.bosse.clubdocs.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * These tests define what TextChunker must do. Pure unit tests: no Spring, no
 * Docker.
 * Run with: ./mvnw test -Dtest=TextChunkerTest
 *
 * Test texts are made of UNIQUE words of VARYING length ("word0", "word1x",
 * "word2xx", ...).
 * Varying lengths matter: with equal-length words a chunk can start on a word
 * boundary by
 * coincidence, which hides bugs where a chunk starts in the middle of a word.
 */
@Timeout(5)
class TextChunkerTest {

    private static final int MAX = 100;
    private static final int OVERLAP = 20;

    private final TextChunker chunker = new TextChunker(MAX, OVERLAP);

    /** Unique words of varying length: word0, word1x, word2xx, ... */
    private static List<String> words(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "word" + i + "x".repeat(i % 7))
                .toList();
    }

    private static Set<String> tokensOf(String chunk) {
        return new HashSet<>(Arrays.asList(chunk.split("\\s+")));
    }

    @Test
    void nullEmptyOrBlankTextGivesNoChunks() {
        assertTrue(chunker.chunk(null).isEmpty());
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk("   \n  ").isEmpty());
    }

    @Test
    void shortTextGivesOneTrimmedChunk() {
        List<String> chunks = chunker.chunk("  Hello club members.  ");

        assertEquals(List.of("Hello club members."), chunks);
    }

    @Test
    void longTextIsSplitAndNoChunkExceedsMaxLength() {
        List<String> chunks = chunker.chunk(String.join(" ", words(100)));

        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= MAX, "chunk too long: " + chunk.length());
        }
    }

    @Test
    void consecutiveChunksOverlap() {
        List<String> chunks = chunker.chunk(String.join(" ", words(100)));

        for (int i = 0; i < chunks.size() - 1; i++) {
            String firstWordOfNext = chunks.get(i + 1).split("\\s+")[0];
            assertTrue(tokensOf(chunks.get(i)).contains(firstWordOfNext),
                    "chunk " + (i + 1) + " should start with a word that also ends chunk " + i);
        }
    }

    @Test
    void neverCutsInsideAWord() {
        List<String> original = words(100);
        List<String> chunks = chunker.chunk(String.join(" ", original));

        for (String chunk : chunks) {
            for (String token : chunk.split("\\s+")) {
                assertTrue(original.contains(token), "cut word: '" + token + "'");
            }
        }
    }

    @Test
    void noTextIsLost() {
        List<String> original = words(100);
        List<String> chunks = chunker.chunk(String.join(" ", original));

        Set<String> found = new HashSet<>();
        chunks.forEach(chunk -> found.addAll(tokensOf(chunk)));

        for (String word : original) {
            assertTrue(found.contains(word), "missing word " + word);
        }
    }

    @Test
    void newlinesAndTabsAreWordBoundariesToo() {
        // Text extracted from PDFs is full of line breaks, not only spaces.
        List<String> original = words(100);

        for (String separator : List.of("\n", "\t", " \n ")) {
            List<String> chunks = chunker.chunk(String.join(separator, original));

            for (String chunk : chunks) {
                assertTrue(chunk.length() <= MAX, "chunk too long for separator '" + separator + "'");
                for (String token : chunk.split("\\s+")) {
                    assertTrue(original.contains(token),
                            "cut word '" + token + "' for separator '" + separator + "'");
                }
            }
        }
    }

    @Test
    void aSingleWordLongerThanMaxIsCutHardAndStillTerminates() {
        String giant = "x".repeat(250);

        List<String> chunks = chunker.chunk(giant);

        assertEquals(List.of("x".repeat(100), "x".repeat(100), "x".repeat(50)), chunks);
    }

    @Test
    void rejectsInvalidSettings() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> new TextChunker(0, 0));
        assertEquals("Maximum chunk length must be positive", exception.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(100, -1));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(100, 100));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(100, 150));
    }

    @Test
    void skipsChunksThatContainOnlyWhitespace() {
        List<String> chunks = new TextChunker(4, 2).chunk("a        b");

        assertEquals(List.of("a", "b"), chunks);
        assertFalse(chunks.contains(""));
    }

    @Test
    void umlautsAndEszettAreCountedAsOneChar() {
        String text = "Straße Übergröße";

        List<String> chunks = chunker.chunk(text);

        assertEquals(List.of(text), chunks);
    }

    @Test
    void textWithParagraphsIsChunked() {
        String text = "This is the first paragraph.\n\nThis is the second paragraph, which is a bit longer than the first one.\n\nAnd this is the third paragraph.";

        List<String> chunks = chunker.chunk(text);

        assertFalse(chunks.isEmpty());
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= MAX);
        }
    }

    @Test
    void wordsLongerThanTheOverlapStillMakeProgress() {
        String word = "y".repeat(60);
        String text = (word + " ").repeat(6);

        List<String> chunks = chunker.chunk(text);

        assertFalse(chunks.isEmpty());
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= MAX);
            for (String token : chunk.split("\\s+")) {
                assertEquals(word, token);
            }
        }
    }

    @Test
    void aShortWordFollowedByAHugeWordDoesNotCrash() {
        List<String> chunks = chunker.chunk("a " + "b".repeat(150));

        assertEquals(List.of("a", "b".repeat(100), "b".repeat(50)), chunks);
    }

    @Test
    void aVeryLongTokenInsideNormalTextDoesNotBreakTheChunker() {
        List<String> normal = words(8);
        String text = String.join(" ", normal) + " https://" + "x".repeat(120)
                + " " + String.join(" ", normal);

        List<String> chunks = chunker.chunk(text);

        Set<String> found = new HashSet<>();
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= MAX);
            found.addAll(tokensOf(chunk));
        }
        assertTrue(found.containsAll(normal));

        for (int i = 1; i < chunks.size(); i++) {
            assertFalse(chunks.get(i - 1).contains(chunks.get(i)),
                    "chunk " + i + " is entirely contained in the previous chunk");
        }
    }
}