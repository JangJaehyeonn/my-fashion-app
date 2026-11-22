package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.domain.clothes.ClothesClassification;
import com.fashionapp.domain.clothes.ClothesSeason;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-13 attribute corrections: omitted keeps the analysed value, invalid values are 400. */
class PurchaseAttributesRequestTest {

    private static final ClothesClassification CURRENT = new ClothesClassification(ClothesCategory.TOP, "블랙",
            "블랙 니트", "오버핏", "울", "무지", ClothesSeason.WINTER);

    private static PurchaseAttributesRequest category(String category) {
        return new PurchaseAttributesRequest(category, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("생략한 속성은 분석값 그대로, 고친 속성만 바뀐다 (앞뒤 공백 제거)")
    void appliesOnlyGivenFields() {
        ClothesClassification result = new PurchaseAttributesRequest("bottom", " 네이비 ", null, null, null, null,
                "fall").applyTo(CURRENT);
        assertThat(result).isEqualTo(new ClothesClassification(ClothesCategory.BOTTOM, "네이비", "블랙 니트",
                "오버핏", "울", "무지", ClothesSeason.FALL));
    }

    @Test
    @DisplayName("빈 요청이면 분석값 그대로다")
    void emptyKeepsEverything() {
        assertThat(new PurchaseAttributesRequest(null, null, null, null, null, null, null).applyTo(CURRENT))
                .isEqualTo(CURRENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNCLASSIFIED", "HAT", ""})
    @DisplayName("고를 수 없는 카테고리는 400")
    void invalidCategory(String category) {
        assertInvalid(category(category));
    }

    @Test
    @DisplayName("모르는 계절, 빈 문자열, 50자를 넘는 문자열은 400")
    void invalidTexts() {
        assertInvalid(new PurchaseAttributesRequest(null, null, null, null, null, null, "rainy"));
        assertInvalid(new PurchaseAttributesRequest(null, "  ", null, null, null, null, null));
        assertInvalid(new PurchaseAttributesRequest(null, null, "가".repeat(51), null, null, null, null));
    }

    private static void assertInvalid(PurchaseAttributesRequest request) {
        assertThatThrownBy(() -> request.applyTo(CURRENT)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PURCHASE_ATTRIBUTES));
    }
}
