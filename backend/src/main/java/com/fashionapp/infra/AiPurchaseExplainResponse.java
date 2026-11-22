package com.fashionapp.infra;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * FR-11: POST /ai/purchase-check/explain response. Every field may be missing or malformed; the backend validates
 * it (picks must be candidates, sentences must be present) and never takes a verdict from it.
 */
@Getter
@NoArgsConstructor
public class AiPurchaseExplainResponse {
    private List<Pick> picks;
    private String headline;
    private List<String> reasons;
    private String gap;
    private List<SimilarNote> similarNotes;
    // Only read to detect an AI that disagrees with the rule verdict (its sentences are then discarded)
    private String verdict;

    @Getter
    @NoArgsConstructor
    public static class Pick {
        private List<String> clothesIds;
        private String label;
    }

    @Getter
    @NoArgsConstructor
    public static class SimilarNote {
        private String id;
        private String note;
    }
}
