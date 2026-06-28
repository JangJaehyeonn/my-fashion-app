package com.fashionapp.infra;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AiRecommendRequest {
    private WeatherInfo weather;
    private List<ClothesItem> clothes;

    @Getter
    @AllArgsConstructor
    public static class WeatherInfo {
        private double temperature;
        private String condition;
    }

    @Getter
    @AllArgsConstructor
    public static class ClothesItem {
        private String id;
        private String category;
        private String color;
        private String pattern;
        private String season;
        private String styleTag;
    }
}
