package com.fashionapp.domain.outfit;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.domain.combo.ComboItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-28 weather rules file: defaults and fail-fast on a malformed file */
class WeatherRulesLoaderTest {

    private static ComboItem top(String material) {
        return new ComboItem(null, ClothesCategory.TOP, "네이비", null, null, null, material);
    }

    private static WeatherRules load(String json) throws Exception {
        return WeatherRulesLoader.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("기본 규칙: 일교차 8도·강수 60% 기준, 린넨 상의는 12도에 안 맞고 니트·면·소재 모름 상의는 맞는다")
    void defaults() {
        WeatherRules rules = WeatherRulesTestSupport.defaultRules();

        assertThat(rules.bigTemperatureRange(8)).isTrue();
        assertThat(rules.bigTemperatureRange(7.9)).isFalse();
        assertThat(rules.rainLikely(60)).isTrue();
        assertThat(rules.rainLikely(59)).isFalse();
        assertThat(rules.comfortableAt(top("린넨"), 12)).isFalse();
        assertThat(rules.comfortableAt(top("니트"), 12)).isTrue();
        assertThat(rules.comfortableAt(top("면"), 12)).isTrue();
        assertThat(rules.comfortableAt(top(null), 12)).isTrue();
        assertThat(rules.thicknessOf(top("린넨"))).isEqualTo(WeatherRules.Thickness.THIN);
        assertThat(rules.thicknessOf(top("모르는 소재"))).isEqualTo(WeatherRules.Thickness.MEDIUM);
    }

    @Test
    @DisplayName("비 제외: 스웨이드는 카테고리와 무관, 흰색은 신발만")
    void rainExclusions() {
        WeatherRules rules = WeatherRulesTestSupport.defaultRules();

        assertThat(rules.excludedInRain(new ComboItem(null, ClothesCategory.OUTER, "베이지", null, null, null, "스웨이드"))).isTrue();
        assertThat(rules.excludedInRain(new ComboItem(null, ClothesCategory.SHOES, "화이트", null, null, null, null))).isTrue();
        assertThat(rules.excludedInRain(new ComboItem(null, ClothesCategory.TOP, "화이트", null, null, null, "면"))).isFalse();
        assertThat(rules.excludedInRain(new ComboItem(null, ClothesCategory.SHOES, "블랙", null, null, null, "가죽"))).isFalse();
    }

    @Test
    @DisplayName("버전·필수 값 누락, 모르는 키·카테고리·두께, 잘못된 범위는 기동 실패")
    void rejectsMalformedFiles() {
        String ok = "\"version\": 1, \"tempRangeThreshold\": 8, \"rainProbabilityThreshold\": 60";
        assertThatThrownBy(() -> load("{\"version\": 2, \"tempRangeThreshold\": 8, \"rainProbabilityThreshold\": 60}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> load("{\"version\": 1}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> load("{" + ok + ", \"unknown\": 1}")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> load("{" + ok + ", \"comfortRanges\": {\"HAT\": {\"THIN\": [1, 2]}}}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> load("{" + ok + ", \"comfortRanges\": {\"TOP\": {\"HEAVY\": [1, 2]}}}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> load("{" + ok + ", \"comfortRanges\": {\"TOP\": {\"THIN\": [30, 2]}}}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> load("{" + ok + ", \"comfortRanges\": {\"TOP\": {\"THIN\": [1]}}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
