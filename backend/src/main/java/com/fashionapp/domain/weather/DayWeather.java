package com.fashionapp.domain.weather;

import java.util.List;

/**
 * FR-28 weather of today's three moments: going out, midday and coming back.
 * {@code tempRange} is the highest minus the lowest of the three temperatures.
 */
public record DayWeather(Point out, Point day, Point back, double tempRange) {

    /**
     * @param time                     "HH:mm" (Korean time)
     * @param temperature              °C, one decimal
     * @param precipitationProbability 0..100 (%)
     * @param windSpeed                m/s, one decimal
     */
    public record Point(String time, double temperature, int precipitationProbability, double windSpeed) {
    }

    public List<Point> points() {
        return List.of(out, day, back);
    }

    /** Highest precipitation probability of the three moments (%). */
    public int maxPrecipitationProbability() {
        return points().stream().mapToInt(Point::precipitationProbability).max().orElse(0);
    }
}
