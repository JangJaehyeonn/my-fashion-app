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
        private List<ShoppingSuggestion> shoppingSuggestions;
    }

    @Getter
    @NoArgsConstructor
    public static class ShoppingSuggestion {
        private String item;
        private String site;
        private String searchKeyword;
    }
}
