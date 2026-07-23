package com.fashionapp.infra;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AiSituationRecommendRequest {
    private WeatherInfo weather;
    private String situation;
    private BodyProfile bodyProfile;

    @Getter
    @AllArgsConstructor
    public static class WeatherInfo {
        private double temperature;
        private String condition;
    }

    @Getter
    @AllArgsConstructor
    public static class BodyProfile {
        private Integer height;
        private Integer weight;
        private String bodyType;
        private String preferredStyle;
    }
}
