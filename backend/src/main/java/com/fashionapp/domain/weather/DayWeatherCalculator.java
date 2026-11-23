package com.fashionapp.domain.weather;

import com.fashionapp.infra.ForecastSlot;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * FR-28: reads today's out / midday / back values from 3-hour forecast slots. Pure: the date comes from the caller.
 * A moment between two slots is linearly interpolated; before the first or after the last slot it takes that slot.
 */
public final class DayWeatherCalculator {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final LocalTime DEFAULT_OUT = LocalTime.of(8, 0);
    public static final LocalTime MIDDAY = LocalTime.of(13, 0);
    public static final LocalTime DEFAULT_BACK = LocalTime.of(19, 0);

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private DayWeatherCalculator() {
    }

    /** Missing commute times fall back to 08:00 and 19:00. {@code slots} must be sorted by time and non-empty. */
    public static DayWeather calculate(List<ForecastSlot> slots, LocalDate today, LocalTime out, LocalTime back) {
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("forecast has no slots");
        }
        DayWeather.Point outPoint = pointAt(slots, today, out == null ? DEFAULT_OUT : out);
        DayWeather.Point dayPoint = pointAt(slots, today, MIDDAY);
        DayWeather.Point backPoint = pointAt(slots, today, back == null ? DEFAULT_BACK : back);
        double max = Math.max(outPoint.temperature(), Math.max(dayPoint.temperature(), backPoint.temperature()));
        double min = Math.min(outPoint.temperature(), Math.min(dayPoint.temperature(), backPoint.temperature()));
        return new DayWeather(outPoint, dayPoint, backPoint, round1(max - min));
    }

    static DayWeather.Point pointAt(List<ForecastSlot> slots, LocalDate today, LocalTime time) {
        long at = today.atTime(time).atZone(ZONE).toEpochSecond();
        ForecastSlot before = slots.get(0);
        ForecastSlot after = slots.get(slots.size() - 1);
        for (ForecastSlot slot : slots) {
            if (slot.epochSecond() <= at) {
                before = slot;
            }
            if (slot.epochSecond() >= at) {
                after = slot;
                break;
            }
        }
        double ratio = ratio(before, after, at);
        return new DayWeather.Point(time.format(HH_MM),
                round1(lerp(before.temperature(), after.temperature(), ratio)),
                (int) Math.round(lerp(before.precipitationChance(), after.precipitationChance(), ratio) * 100),
                round1(lerp(before.windSpeed(), after.windSpeed(), ratio)));
    }

    private static double ratio(ForecastSlot before, ForecastSlot after, long at) {
        long span = after.epochSecond() - before.epochSecond();
        if (span <= 0) {
            return 0;
        }
        return Math.min(1, Math.max(0, (double) (at - before.epochSecond()) / span));
    }

    private static double lerp(double a, double b, double ratio) {
        return a + (b - a) * ratio;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
