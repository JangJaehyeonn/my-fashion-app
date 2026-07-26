package com.fashionapp.domain.shopping;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ShoppingRecommendRequest {
    private int budget;
    private String situation;
}
