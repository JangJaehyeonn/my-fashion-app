package com.fashionapp.domain.clothes;

import com.fashionapp.infra.AiClothesClassifyResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ClothesClassificationTest {

    private static AiClothesClassifyResponse ai(String category, String color, String name) {
        AiClothesClassifyResponse response = new AiClothesClassifyResponse();
        ReflectionTestUtils.setField(response, "category", category);
        ReflectionTestUtils.setField(response, "color", color);
        ReflectionTestUtils.setField(response, "name", name);
        return response;
    }

    @Test
    @DisplayName("정상 응답은 카테고리·색상·이름으로 바뀐다 (목록 밖 카테고리는 ETC)")
    void valid() {
        assertThat(ClothesClassification.from(ai("top", " 네이비 ", "네이비 니트")))
                .contains(new ClothesClassification(ClothesCategory.TOP, "네이비", "네이비 니트"));
        assertThat(ClothesClassification.from(ai("hat", "블랙", null)))
                .contains(new ClothesClassification(ClothesCategory.ETC, "블랙", null));
    }

    @Test
    @DisplayName("null 응답, 카테고리·색상 누락, UNCLASSIFIED는 잘못된 형식")
    void malformed() {
        assertThat(ClothesClassification.from(null)).isEmpty();
        assertThat(ClothesClassification.from(ai(null, "블랙", "x"))).isEmpty();
        assertThat(ClothesClassification.from(ai("TOP", " ", "x"))).isEmpty();
        assertThat(ClothesClassification.from(ai("unclassified", "블랙", "x"))).isEmpty();
    }

    @Test
    @DisplayName("사용자가 지정할 수 있는 카테고리에 UNCLASSIFIED는 없다")
    void parseAssignable() {
        assertThat(ClothesCategory.parseAssignable("outer")).contains(ClothesCategory.OUTER);
        assertThat(ClothesCategory.parseAssignable("UNCLASSIFIED")).isEmpty();
        assertThat(ClothesCategory.parseAssignable("hat")).isEmpty();
        assertThat(ClothesCategory.from("UNCLASSIFIED")).isEqualTo(ClothesCategory.UNCLASSIFIED);
    }
}
