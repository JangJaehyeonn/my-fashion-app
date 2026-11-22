package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.domain.clothes.ClothesClassification;
import com.fashionapp.domain.clothes.ClothesSeason;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;

/**
 * FR-13 PATCH /api/purchase-checks/{id}/attributes body. Omitted (null) fields keep the analysed value; the rules are
 * the same as PATCH /api/clothes/{id} (category assignable, season known, texts 1..50 chars after trimming).
 */
public record PurchaseAttributesRequest(String category, String color, String name, String fit, String material,
                                        String pattern, String season) {

    static final int MAX_TEXT_LENGTH = 50;

    /** The analysed attributes with these corrections applied; 400 INVALID_PURCHASE_ATTRIBUTES when invalid. */
    public ClothesClassification applyTo(ClothesClassification current) {
        ClothesCategory newCategory = category == null ? current.category()
                : ClothesCategory.parseAssignable(category).orElseThrow(PurchaseAttributesRequest::invalid);
        ClothesSeason newSeason = season == null ? current.season()
                : ClothesSeason.parse(season).orElseThrow(PurchaseAttributesRequest::invalid);
        return new ClothesClassification(newCategory, orElse(color, current.color()), orElse(name, current.name()),
                orElse(fit, current.fit()), orElse(material, current.material()),
                orElse(pattern, current.pattern()), newSeason);
    }

    private static String orElse(String value, String current) {
        if (value == null) {
            return current;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TEXT_LENGTH) {
            throw invalid();
        }
        return trimmed;
    }

    private static CustomException invalid() {
        return new CustomException(ErrorCode.INVALID_PURCHASE_ATTRIBUTES);
    }
}
