package com.fashionapp.infra;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AiRecommendResponse {
    private List<RecommendedOutfit> outfits;

    @Getter
    @NoArgsConstructor
    public static class RecommendedOutfit {
        private List<String> clothesIds;
        private String reason;
        private String styleTag;
    }
}
