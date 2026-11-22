package com.fashionapp.domain.purchase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-11 expectedWears = round(recent 90-day category wears × plus ÷ category combos × 365 ÷ 90), costPerWear. */
class ExpectedWearsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    @Test
    @DisplayName("상의 최근 90일 4회 × (2 ÷ 4) × 365 ÷ 90 = 8.11 → 8, 39000원 ÷ 8 = 4875원")
    void formula() {
        Integer wears = ExpectedWears.expectedWears(4, 2, 4, TODAY.minusDays(40), TODAY);
        assertThat(wears).isEqualTo(8);
        assertThat(ExpectedWears.costPerWear(39000L, wears)).isEqualTo(4875L);
    }

    @Test
    @DisplayName("반올림: 1 × (1 ÷ 2) × 365 ÷ 90 = 2.03 → 2, 10000 ÷ 3 = 3333.3 → 3333")
    void rounding() {
        assertThat(ExpectedWears.expectedWears(1, 1, 2, TODAY.minusDays(30), TODAY)).isEqualTo(2);
        assertThat(ExpectedWears.costPerWear(10000L, 3)).isEqualTo(3333L);
        assertThat(ExpectedWears.costPerWear(5000L, 3)).isEqualTo(1667L);
    }

    @Test
    @DisplayName("착용 기록이 30일 미만이거나 없으면 null, 그러면 1회당 가격도 null")
    void shortHistory() {
        assertThat(ExpectedWears.expectedWears(4, 2, 4, TODAY.minusDays(29), TODAY)).isNull();
        assertThat(ExpectedWears.expectedWears(4, 2, 4, null, TODAY)).isNull();
        assertThat(ExpectedWears.costPerWear(39000L, null)).isNull();
    }

    @Test
    @DisplayName("그 카테고리가 든 코디가 없으면 null")
    void noCategoryCombos() {
        assertThat(ExpectedWears.expectedWears(4, 2, 0, TODAY.minusDays(40), TODAY)).isNull();
    }

    @Test
    @DisplayName("가격이 없거나 예상 착용이 1 미만이면 1회당 가격은 null")
    void costPerWearNeedsPriceAndWears() {
        assertThat(ExpectedWears.costPerWear(null, 8)).isNull();
        assertThat(ExpectedWears.costPerWear(39000L, 0)).isNull();
        assertThat(ExpectedWears.expectedWears(0, 2, 4, TODAY.minusDays(40), TODAY)).isZero();
    }

    @Test
    @DisplayName("90일 창은 오늘을 포함해 90일")
    void window() {
        assertThat(ExpectedWears.windowStart(TODAY)).isEqualTo(TODAY.minusDays(89));
    }
}
