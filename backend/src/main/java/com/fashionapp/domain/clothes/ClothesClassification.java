package com.fashionapp.domain.clothes;

import com.fashionapp.infra.AiClothesClassifyResponse;

import java.util.Optional;

/**
 * A validated AI classification result. fit, material, pattern and season (FR-07) are optional: null means unknown.
 */
public record ClothesClassification(ClothesCategory category, String color, String name,
                                    String fit, String material, String pattern, ClothesSeason season) {

    static final int MAX_ATTRIBUTE_LENGTH = 50;

    public ClothesClassification(ClothesCategory category, String color, String name) {
        this(category, color, name, null, null, null, null);
    }

    /**
     * Validates the AI response. Missing category or color means a malformed response (empty Optional).
     * A category outside the known list falls back to ETC (existing behavior); UNCLASSIFIED from the AI is treated as malformed.
     * Blank, too long or unknown optional attributes become null instead of failing the whole classification.
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
        return Optional.of(new ClothesClassification(category, response.getColor().trim(), name,
                attribute(response.getFit()), attribute(response.getMaterial()), attribute(response.getPattern()),
                ClothesSeason.parse(response.getSeason()).orElse(null)));
    }

    private static String attribute(String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_ATTRIBUTE_LENGTH ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
