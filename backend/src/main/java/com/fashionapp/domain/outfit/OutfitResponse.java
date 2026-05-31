package com.fashionapp.domain.outfit;

import com.fashionapp.domain.clothes.ClothesResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class OutfitResponse {

    private UUID id;
    private String name;
    private String styleTag;
    private String weatherCondition;
    private List<ClothesResponse> clothes;
    private LocalDateTime createdAt;

    public static OutfitResponse from(Outfit outfit) {
        return OutfitResponse.builder()
                .id(outfit.getId())
                .name(outfit.getName())
                .styleTag(outfit.getStyleTag())
                .weatherCondition(outfit.getWeatherCondition())
                .clothes(outfit.getItems().stream()
                        .map(item -> ClothesResponse.from(item.getClothes()))
                        .toList())
                .createdAt(outfit.getCreatedAt())
                .build();
    }
}
