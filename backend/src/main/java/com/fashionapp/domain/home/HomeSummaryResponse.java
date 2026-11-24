package com.fashionapp.domain.home;

import com.fashionapp.infra.AiSituationRecommendResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HomeSummaryResponse {
    private AiSituationRecommendResponse outfitRecommendation;
}
