package com.fashionapp.domain.combo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.fashionapp.domain.clothes.ClothesCategory.BOTTOM;
import static com.fashionapp.domain.clothes.ClothesCategory.TOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-09 rules file format (docs/adr/0008-combo-count-rules.md). */
class ComboRulesLoaderTest {

    private static InputStream json(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static ComboItem piece(com.fashionapp.domain.clothes.ClothesCategory category, String color,
                                   String material) {
        return new ComboItem(UUID.randomUUID(), category, color, null, null, null, material);
    }

    @Test
    @DisplayName("기본 규칙 파일: 레드–그린은 충돌, 무채색은 어떤 색과도 어울린다")
    void defaultRules() {
        ComboRules rules = ComboTestSupport.defaultRules();

        assertThat(rules.compatible(piece(TOP, "레드", null), piece(BOTTOM, "그린", null))).isFalse();
        assertThat(rules.compatible(piece(TOP, "그린", null), piece(BOTTOM, "레드", null))).isFalse();
        assertThat(rules.compatible(piece(TOP, "레드", null), piece(BOTTOM, "블랙", null))).isTrue();
        assertThat(rules.compatible(piece(TOP, null, null), piece(BOTTOM, "그린", null))).isTrue();
    }

    @Test
    @DisplayName("코드 수정 없이 규칙 파일만 바꿔 충돌 조합과 스타일 속성을 조정할 수 있다")
    void customRules() throws Exception {
        ComboRules rules = ComboRulesLoader.load(json("""
                {"version": 1, "neutralColors": [], "colorConflicts": [["블루", "브라운"]],
                 "seasonConflicts": [], "styleConflicts": {"attribute": "material", "pairs": [["린넨", "패딩"]]}}
                """));

        assertThat(rules.compatible(piece(TOP, "블루", null), piece(BOTTOM, "브라운", null))).isFalse();
        assertThat(rules.compatible(piece(TOP, "레드", null), piece(BOTTOM, "그린", null))).isTrue();
        assertThat(rules.compatible(piece(TOP, null, "린넨"), piece(BOTTOM, null, "패딩"))).isFalse();
    }

    @Test
    @DisplayName("지원하지 않는 버전·알 수 없는 키·쌍이 아닌 규칙은 기동 시 실패한다")
    void malformedRulesFail() {
        assertThatThrownBy(() -> ComboRulesLoader.load(json("{\"version\": 2}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComboRulesLoader.load(json("{\"version\": 1, \"colorConflict\": []}")))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> ComboRulesLoader.load(json("{\"version\": 1, \"colorConflicts\": [[\"레드\"]]}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
