package com.fashionapp.domain.combo;

import com.fashionapp.domain.clothes.Clothes;
import com.fashionapp.domain.clothes.ClothesCategory;

import java.util.UUID;

/**
 * The attributes of one piece that the combo engine looks at. {@code id} is null for a hypothetical new piece
 * (FR-09 (3) / FR-11), which only has attributes.
 */
public record ComboItem(UUID id, ClothesCategory category, String color, String season,
                        String pattern, String fit, String material) {

    public static ComboItem of(Clothes clothes) {
        return new ComboItem(clothes.getId(), ClothesCategory.from(clothes.getCategory()), clothes.getColor(),
                clothes.getSeason(), clothes.getPattern(), clothes.getFit(), clothes.getMaterial());
    }
}
