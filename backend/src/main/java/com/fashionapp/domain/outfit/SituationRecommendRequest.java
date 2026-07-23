package com.fashionapp.domain.outfit;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SituationRecommendRequest {
    private double temperature;
    private String condition;
    private String situation;
}
