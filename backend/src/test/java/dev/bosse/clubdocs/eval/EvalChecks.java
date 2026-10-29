package dev.bosse.clubdocs.eval;

import java.util.List;

import dev.bosse.clubdocs.chat.PromptBuilder;

/**
 * Pure utility methods for grading retrieval and answer quality.
 */
public final class EvalChecks {

    private EvalChecks() {}

    /**
     * Returns the 1-based rank of the first chunk that contains any evidence string.
     * Returns {@code Integer.MAX_VALUE} if no chunk contains the evidence.
     */
    public static int firstHitRank(List<String> chunkTexts, List<String> evidence) {
        for (int i = 0; i < chunkTexts.size(); i++) {
            String chunk = chunkTexts.get(i).toLowerCase();
            for (String ev : evidence) {
                if (chunk.contains(ev.toLowerCase())) {
                    return i + 1; // 1-based rank
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Returns true if the evidence was found within the first {@code k} chunks.
     */
    public static boolean hitAtK(List<String> chunkTexts, List<String> evidence, int k) {
        return firstHitRank(chunkTexts, evidence) <= k;
    }

    /**
     * Returns true if the answer contains all the required strings (case-insensitive).
     */
    public static boolean containsAll(String answer, List<String> mustContain) {
        String lower = answer.toLowerCase();
        for (String s : mustContain) {
            if (!lower.contains(s.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the answer is the fallback sentence (model couldn't find the answer).
     */
    public static boolean isFallback(String answer) {
        return answer.trim().equals(PromptBuilder.NO_ANSWER_TEXT);
    }

    /**
     * Counts citations like [N] in the answer that exceed the number of available sources.
     */
    public static int invalidCitations(String answer, int sourceCount) {
        int count = 0;
        for (int i = 0; i < answer.length(); i++) {
            if (answer.charAt(i) == '[') {
                int close = answer.indexOf(']', i);
                if (close > i && close < i + 5) {
                    try {
                        int num = Integer.parseInt(answer.substring(i + 1, close));
                        if (num < 1 || num > sourceCount) {
                            count++;
                        }
                    } catch (NumberFormatException ignored) {
                        // not a citation
                    }
                }
            }
        }
        return count;
    }

    /**
     * Counts how many nice-to-have strings the answer also contains.
     */
    public static int niceToHaveCount(String answer, List<String> niceToHave) {
        String lower = answer.toLowerCase();
        int count = 0;
        for (String s : niceToHave) {
            if (lower.contains(s.toLowerCase())) {
                count++;
            }
        }
        return count;
    }
}
