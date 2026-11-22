package com.fashionapp.infra;

import java.util.List;
import java.util.Map;

/**
 * FR-11: POST /ai/purchase-check/explain request. The verdict, +N and candidates are already decided by the backend
 * rules; the AI only picks up to 3 of the candidates and writes the sentences.
 */
public record AiPurchaseExplainRequest(Item item, String verdict, int plus, int comboCountBefore,
                                       int comboCountAfter, List<Similar> similar, Map<String, Integer> distribution,
                                       List<Candidate> candidates) {

    /** The piece being considered (attributes from classification; null = unknown). */
    public record Item(String category, String color, String name, String fit, String material, String pattern,
                       String season) {
    }

    /** A similar closet piece. lastWornAt is "YYYY-MM-DD" or null when never worn. */
    public record Similar(String id, String name, double similarity, int wearCount, String lastWornAt) {
    }

    /**
     * A valid combo with the new piece; only closet clothes ids (the new piece is implied). labels are short
     * "카테고리 색 이름" texts of the same pieces, in the same order, for the prompt.
     */
    public record Candidate(List<String> clothesIds, List<String> labels) {
    }
}
