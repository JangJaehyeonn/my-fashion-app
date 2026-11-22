package com.fashionapp.infra;

/**
 * One 3-hour slot of the OpenWeatherMap 5 day / 3 hour forecast (FR-28).
 *
 * @param epochSecond          slot time (UTC epoch seconds, OpenWeatherMap {@code dt})
 * @param temperature          °C
 * @param precipitationChance  probability of precipitation, 0..1 (OpenWeatherMap {@code pop})
 * @param windSpeed            m/s
 */
public record ForecastSlot(long epochSecond, double temperature, double precipitationChance, double windSpeed) {
}
