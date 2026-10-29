package dev.bosse.clubdocs.eval;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One evaluation question loaded from {@code eval/questions.json}.
 */
public record EvalQuestion(
        String id,
        String category,
        String question,
        List<String> mustContain,
        List<String> niceToHave,
        List<String> evidence,
        @JsonProperty("expectFallback") boolean expectFallback,
        String notes) {

    /** Returns true if this question has evidence text to search for (non-absent questions). */
    public boolean hasEvidence() {
        return !evidence.isEmpty();
    }
}
