package com.fashionapp.domain.outfit;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.domain.combo.Combo;
import com.fashionapp.domain.combo.ComboItem;
import com.fashionapp.domain.weather.DayWeather;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-28: weather rules choose the candidates by rules alone (big temperature range, rain) */
class WeatherOutfitPlannerTest {

    private final WeatherOutfitPlanner planner = WeatherRulesTestSupport.defaultPlanner();

    private static int seq = 0;

    private static ComboItem item(ClothesCategory category, String color, String material) {
        return new ComboItem(new UUID(0, ++seq), category, color, null, null, null, material);
    }

    private static DayWeather weather(double out, double day, double back, int pop) {
        return new DayWeather(new DayWeather.Point("08:00", out, pop, 2), new DayWeather.Point("13:00", day, pop, 2),
                new DayWeather.Point("19:00", back, pop, 2),
                Math.max(out, Math.max(day, back)) - Math.min(out, Math.min(day, back)));
    }

    private final ComboItem knit = item(ClothesCategory.TOP, "네이비", "니트");
    private final ComboItem cotton = item(ClothesCategory.TOP, "화이트", "면");
    private final ComboItem linen = item(ClothesCategory.TOP, "베이지", "린넨");
    private final ComboItem slacks = item(ClothesCategory.BOTTOM, "블랙", null);
    private final ComboItem woolPants = item(ClothesCategory.BOTTOM, "그레이", "울");
    private final ComboItem loafers = item(ClothesCategory.SHOES, "블랙", "가죽");
    private final ComboItem coat = item(ClothesCategory.OUTER, "네이비", "울");
    private final List<ComboItem> closet = List.of(knit, cotton, linen, slacks, woolPants, loafers, coat);

    @Test
    @DisplayName("아침 3도·낮 12도: TEMP_RANGE — 모든 후보가 아우터 없이도 유효하고 낮 기온에 안 맞는 린넨 상의가 없다")
    void bigRangeKeepsCombosValidWithoutOuter() {
        WeatherOutfitPlanner.Plan plan = planner.plan(closet, weather(3, 12, 7, 0));

        assertThat(plan.appliedRules()).containsExactly(WeatherOutfitPlanner.TEMP_RANGE);
        assertThat(plan.candidates()).isNotEmpty().allSatisfy(combo -> {
            assertThat(combo.top()).isNotNull();
            assertThat(combo.bottom()).isNotNull();
            assertThat(combo.shoes()).isNotNull();
            assertThat(combo.top()).isNotEqualTo(linen);
        });
        assertThat(plan.candidates()).extracting(Combo::top).contains(knit, cotton);
        assertThat(plan.excludedIds()).isEmpty();
    }

    @ParameterizedTest(name = "아침 {0}도·낮 12도·저녁 {1}도 → 적용 {2}")
    @CsvSource({"4, 9, true", "5, 9, false", "10, 11, false"})
    @DisplayName("일교차 기준은 8도 (경계 포함), 미만이면 제한 없음")
    void thresholdIsEightDegrees(double morning, double evening, boolean applied) {
        WeatherOutfitPlanner.Plan plan = planner.plan(closet, weather(morning, 12, evening, 0));

        assertThat(plan.appliedRules().contains(WeatherOutfitPlanner.TEMP_RANGE)).isEqualTo(applied);
        assertThat(plan.candidates().stream().anyMatch(c -> c.top() == linen)).isEqualTo(!applied);
    }

    @Test
    @DisplayName("낮 기온에 맞는 상의가 하나도 없으면 TEMP_RANGE를 적용하지 않고 유효 조합을 그대로 준다")
    void notAppliedWhenNothingSurvives() {
        List<ComboItem> linenOnly = List.of(linen, slacks, loafers);
        WeatherOutfitPlanner.Plan plan = planner.plan(linenOnly, weather(3, 12, 7, 0));

        assertThat(plan.appliedRules()).isEmpty();
        assertThat(plan.candidates()).hasSize(1);
    }

    @Test
    @DisplayName("강수 확률 70%: RAIN — 스웨이드(신발·아우터)와 흰색 신발을 빼고, 흰 상의·가죽 부츠는 남긴다")
    void rainRemovesSuedeAndWhiteShoes() {
        ComboItem whiteTop = item(ClothesCategory.TOP, "화이트", "면");
        ComboItem chino = item(ClothesCategory.BOTTOM, "베이지", null);
        ComboItem whiteSneakers = item(ClothesCategory.SHOES, "화이트", null);
        ComboItem suedeLoafers = item(ClothesCategory.SHOES, "브라운", "스웨이드");
        ComboItem boots = item(ClothesCategory.SHOES, "블랙", "가죽");
        ComboItem suedeJacket = item(ClothesCategory.OUTER, "베이지", "스웨이드");
        List<ComboItem> rainCloset = List.of(whiteTop, chino, whiteSneakers, suedeLoafers, boots, suedeJacket);

        WeatherOutfitPlanner.Plan plan = planner.plan(rainCloset, weather(12, 13, 12, 70));

        assertThat(plan.appliedRules()).containsExactly(WeatherOutfitPlanner.RAIN);
        assertThat(plan.excludedIds()).containsExactlyInAnyOrder(
                whiteSneakers.id(), suedeLoafers.id(), suedeJacket.id());
        assertThat(plan.candidates()).isNotEmpty()
                .allSatisfy(combo -> assertThat(combo.items()).doesNotContain(whiteSneakers, suedeLoafers, suedeJacket));
        assertThat(plan.candidates()).extracting(Combo::shoes).containsOnly(boots);
    }

    @Test
    @DisplayName("강수 확률 50%면 비 규칙을 적용하지 않는다 (기준 60%)")
    void lowRainKeepsWhiteShoes() {
        ComboItem whiteSneakers = item(ClothesCategory.SHOES, "화이트", null);
        WeatherOutfitPlanner.Plan plan = planner.plan(List.of(knit, slacks, whiteSneakers), weather(12, 13, 12, 50));

        assertThat(plan.appliedRules()).isEmpty();
        assertThat(plan.excludedIds()).isEmpty();
        assertThat(plan.candidates()).extracting(Combo::shoes).containsOnly(whiteSneakers);
    }

    @Test
    @DisplayName("비와 일교차가 함께면 두 규칙을 모두 적용한다")
    void bothRules() {
        WeatherOutfitPlanner.Plan plan = planner.plan(closet, weather(3, 12, 7, 60));
        assertThat(plan.appliedRules()).containsExactly(WeatherOutfitPlanner.RAIN, WeatherOutfitPlanner.TEMP_RANGE);
    }
}
