package com.fashionapp.infra;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AiClosetRecommendRequest {
    private AiSituationRecommendRequest.WeatherInfo weather;
    private String situation;
    private AiSituationRecommendRequest.BodyProfile bodyProfile;
    private List<ClosetItem> clothes;

    @Getter
    @AllArgsConstructor
    public static class ClosetItem {
        private String id;
        private String category;
        private String color;
        private String name;
    }
}
