package com.fashionapp.domain.outfit;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SituationRecommendRequest {
    private double temperature;
    private String condition;
    private String situation;
    // FR-28 (closet recommendation only): where the user is, for the day forecast. null = old behaviour (no forecast)
    private Double lat;
    private Double lon;
}
