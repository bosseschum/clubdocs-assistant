package dev.bosse.clubdocs.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Defines what TextNormalizer must do. Pure unit tests. */
class TextNormalizerTest {

    @Test
    void nullGivesEmptyString() {
        assertEquals("", TextNormalizer.normalize(null));
    }

    @Test
    void collapsesRunsOfSpacesAndTabsToOneSpace() {
        assertEquals("Rechnung: Nr. 123", TextNormalizer.normalize("Rechnung:        Nr.\t\t123"));
    }

    @Test
    void treatsNonBreakingSpacesLikeSpaces() {
        assertEquals("a b", TextNormalizer.normalize("a\u00A0\u00A0b"));
    }

    @Test
    void removesSpacesAtTheStartAndEndOfLines() {
        assertEquals("a\nb", TextNormalizer.normalize("a   \n   b"));
    }

    @Test
    void convertsWindowsAndOldMacLineEndings() {
        assertEquals("a\nb\nc", TextNormalizer.normalize("a\r\nb\rc"));
    }

    @Test
    void collapsesThreeOrMoreLineBreaksIntoOneBlankLine() {
        assertEquals("a\n\nb", TextNormalizer.normalize("a\n\n\n\n\nb"));
        assertEquals("a\n\nb", TextNormalizer.normalize("a\n\nb")); // exactly two stay two
        assertEquals("a\nb", TextNormalizer.normalize("a\nb")); // a single break stays
    }

    @Test
    void trimsTheWholeResult() {
        assertEquals("a b", TextNormalizer.normalize("  \n a   b \n\n  "));
    }

    @Test
    void leavesCleanGermanTextUntouched() {
        String clean = "Gießener Wingolf e.V.\n\nStraße 5, 35390 Gießen";

        assertEquals(clean, TextNormalizer.normalize(clean));
    }
}
