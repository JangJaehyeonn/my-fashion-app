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
    private ClothesCategory category;
    private String color;
    private String name;
    // true when the category is UNCLASSIFIED (AI classification failed and the user has not fixed it yet)
    private boolean classificationFailed;
    private LocalDateTime createdAt;
}
