package com.fashionapp.domain.clothes;

import com.fashionapp.infra.AiClothesClassifyResponse;

import java.util.Optional;

/** A validated AI classification result. */
public record ClothesClassification(ClothesCategory category, String color, String name) {

    /**
     * Validates the AI response. Missing category or color means a malformed response (empty Optional).
     * A category outside the known list falls back to ETC (existing behavior); UNCLASSIFIED from the AI is treated as malformed.
     */
    public static Optional<ClothesClassification> from(AiClothesClassifyResponse response) {
        if (response == null || isBlank(response.getCategory()) || isBlank(response.getColor())) {
            return Optional.empty();
        }
        ClothesCategory category = ClothesCategory.from(response.getCategory());
        if (category == ClothesCategory.UNCLASSIFIED) {
            return Optional.empty();
        }
        String name = isBlank(response.getName()) ? null : response.getName().trim();
        return Optional.of(new ClothesClassification(category, response.getColor().trim(), name));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
