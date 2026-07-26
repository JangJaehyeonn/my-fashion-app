package com.fashionapp.infra;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AiShoppingRecommendRequest {
    private int budget;
    private String situation;
    private BodyProfile bodyProfile;

    @Getter
    @AllArgsConstructor
    public static class BodyProfile {
        private Integer height;
        private Integer weight;
        private String bodyType;
        private String preferredStyle;
    }
}
