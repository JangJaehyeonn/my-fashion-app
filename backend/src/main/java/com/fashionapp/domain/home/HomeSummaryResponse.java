package com.fashionapp.domain.home;

import com.fashionapp.infra.AiShoppingRecommendResponse;
import com.fashionapp.infra.AiSituationRecommendResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HomeSummaryResponse {
    private AiSituationRecommendResponse outfitRecommendation;
    private AiShoppingRecommendResponse shoppingRecommendation;
}
