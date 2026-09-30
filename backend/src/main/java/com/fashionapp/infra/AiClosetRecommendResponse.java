package com.fashionapp.infra;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AiClosetRecommendResponse {
    private List<ClosetOutfitSuggestion> outfits;

    @Getter
    @NoArgsConstructor
    public static class ClosetOutfitSuggestion {
        private List<String> clothesIds;
        private String reason;
        private String styleTag;
    }
}
