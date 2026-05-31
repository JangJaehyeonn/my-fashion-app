package com.fashionapp.domain.clothes;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ClothesResponse {

    private UUID id;
    private String imageUrl;
    private String category;
    private String color;
    private String pattern;
    private String season;
    private String styleTag;
    private LocalDateTime createdAt;

    public static ClothesResponse from(Clothes clothes) {
        return ClothesResponse.builder()
                .id(clothes.getId())
                .imageUrl(clothes.getImageUrl())
                .category(clothes.getCategory())
                .color(clothes.getColor())
                .pattern(clothes.getPattern())
                .season(clothes.getSeason())
                .styleTag(clothes.getStyleTag())
                .createdAt(clothes.getCreatedAt())
                .build();
    }
}
