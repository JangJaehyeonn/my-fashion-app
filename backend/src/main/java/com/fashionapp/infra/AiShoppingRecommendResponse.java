package com.fashionapp.infra;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AiShoppingRecommendResponse {
    private List<ShoppingItemSuggestion> items;
    private int totalEstimatedPrice;
    private String usageTip;

    @Getter
    @NoArgsConstructor
    public static class ShoppingItemSuggestion {
        private String item;
        private String reason;
        private int estimatedPrice;
        private String site;
        private String searchKeyword;
    }
}
