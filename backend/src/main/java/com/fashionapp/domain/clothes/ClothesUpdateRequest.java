package com.fashionapp.domain.clothes;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PATCH /api/clothes/{id}. Omitted (null) fields keep their current value. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ClothesUpdateRequest {
    private String category;
    private String color;
    private String name;
    // FR-07 attributes
    private String fit;
    private String material;
    private String pattern;
    private String season;

    public ClothesUpdateRequest(String category, String color, String name) {
        this(category, color, name, null, null, null, null);
    }
}
