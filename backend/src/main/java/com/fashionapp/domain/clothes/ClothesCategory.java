package com.fashionapp.domain.clothes;

import java.util.Optional;

public enum ClothesCategory {
    // UNCLASSIFIED: AI classification failed (FR-01). Excluded from outfit recommendation, combo count and similarity search.
    TOP, BOTTOM, SHOES, OUTER, ETC, UNCLASSIFIED;

    // DB에는 문자열로 저장 — 피봇 이전 옛 clothes 행(자유 텍스트 category)이 남아 있어도 조회가 깨지지 않도록 ETC로 흡수
    public static ClothesCategory from(String value) {
        if (value == null) {
            return ETC;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ETC;
        }
    }

    /** Strict parse for values a user or the AI may assign. UNCLASSIFIED and unknown names are rejected. */
    public static Optional<ClothesCategory> parseAssignable(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            ClothesCategory category = valueOf(value.trim().toUpperCase());
            return category == UNCLASSIFIED ? Optional.empty() : Optional.of(category);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
