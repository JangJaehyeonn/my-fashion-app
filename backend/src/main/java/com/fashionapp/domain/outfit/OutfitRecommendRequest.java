package com.fashionapp.domain.outfit;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OutfitRecommendRequest {
    private double temperature;
    private String condition;
}
