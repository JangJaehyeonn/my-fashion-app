package com.fashionapp.domain.weather;

import com.fashionapp.infra.ForecastSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-28: today's out / midday / back values from 3-hour forecast slots */
class DayWeatherCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    /** Slots every 3 hours (KST 0·3·…·21) for yesterday, today and tomorrow; temperature by KST hour. */
    private static List<ForecastSlot> slots(IntToDoubleFunction tempByHour, double pop, double wind) {
        List<ForecastSlot> slots = new ArrayList<>();
        for (int day = -1; day <= 1; day++) {
            for (int hour = 0; hour < 24; hour += 3) {
                long at = TODAY.plusDays(day).atTime(hour, 0).atZone(DayWeatherCalculator.ZONE).toEpochSecond();
                slots.add(new ForecastSlot(at, tempByHour.applyAsDouble(hour), pop, wind));
            }
        }
        return slots;
    }

    @Test
    @DisplayName("출퇴근 시각이 없으면 8시·13시·19시 값을 주고, 일교차는 세 시점의 최고 − 최저")
    void defaultsTo8_13_19() {
        List<ForecastSlot> slots = slots(h -> h <= 9 ? 3 : h <= 15 ? 12 : 7, 0.3, 2.0);

        DayWeather day = DayWeatherCalculator.calculate(slots, TODAY, null, null);

        assertThat(day.out().time()).isEqualTo("08:00");
        assertThat(day.day().time()).isEqualTo("13:00");
        assertThat(day.back().time()).isEqualTo("19:00");
        assertThat(day.out().temperature()).isEqualTo(3.0);
        assertThat(day.day().temperature()).isEqualTo(12.0);
        assertThat(day.back().temperature()).isEqualTo(7.0);
        assertThat(day.tempRange()).isEqualTo(9.0);
        assertThat(day.points()).allSatisfy(p -> {
            assertThat(p.precipitationProbability()).isEqualTo(30);
            assertThat(p.windSpeed()).isEqualTo(2.0);
        });
    }

    @Test
    @DisplayName("출퇴근 시각을 쓰고, 칸 사이 시각은 선형 보간한다 (낮은 늘 13시)")
    void usesCommuteTimesAndInterpolates() {
        List<ForecastSlot> slots = slots(h -> h, 0, 1);

        DayWeather day = DayWeatherCalculator.calculate(slots, TODAY, LocalTime.of(6, 0), LocalTime.of(21, 0));

        assertThat(day.out().time()).isEqualTo("06:00");
        assertThat(day.out().temperature()).isEqualTo(6.0);
        assertThat(day.back().time()).isEqualTo("21:00");
        assertThat(day.back().temperature()).isEqualTo(21.0);
        assertThat(day.day().temperature()).isEqualTo(13.0);   // between the 12h (12°) and 15h (15°) slots
        assertThat(day.tempRange()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("예보 범위 밖 시각은 가장 가까운 끝 칸 값을 쓴다 (실제 예보는 지금부터 시작)")
    void clampsOutsideTheForecast() {
        long noon = TODAY.atTime(12, 0).atZone(DayWeatherCalculator.ZONE).toEpochSecond();
        List<ForecastSlot> slots = List.of(new ForecastSlot(noon, 15, 0.7, 3), new ForecastSlot(noon + 10800, 18, 0.1, 1));

        DayWeather day = DayWeatherCalculator.calculate(slots, TODAY, LocalTime.of(7, 30), null);

        assertThat(day.out().temperature()).isEqualTo(15.0);
        assertThat(day.out().precipitationProbability()).isEqualTo(70);
        assertThat(day.back().temperature()).isEqualTo(18.0);
        assertThat(day.maxPrecipitationProbability()).isEqualTo(70);
    }

    @Test
    @DisplayName("같은 입력이면 같은 결과, 빈 예보는 거부")
    void deterministicAndRejectsEmpty() {
        List<ForecastSlot> slots = slots(h -> h * 0.7, 0.45, 2.25);
        assertThat(DayWeatherCalculator.calculate(slots, TODAY, null, null))
                .isEqualTo(DayWeatherCalculator.calculate(slots, TODAY, null, null));
        assertThatThrownBy(() -> DayWeatherCalculator.calculate(List.of(), TODAY, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
