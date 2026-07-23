package com.fashionapp.infra;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AiSituationRecommendResponse {
    private List<SituationOutfitSuggestion> outfits;

    @Getter
    @NoArgsConstructor
    public static class SituationOutfitSuggestion {
        private String description;
        private String reason;
        private String styleTag;
    }
}
