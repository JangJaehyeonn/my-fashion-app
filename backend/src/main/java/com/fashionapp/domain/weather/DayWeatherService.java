package com.fashionapp.domain.weather;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** FR-28: today's out / midday / back weather for the user's commute times (08:00·13:00·19:00 when not set). */
@Service
@RequiredArgsConstructor
public class DayWeatherService {

    private final WeatherService weatherService;
    private final UserRepository userRepository;
    private final Clock clock;

    public DayWeather getDay(UUID userId, Double lat, Double lon) {
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return dayFor(user, lat, lon);
    }

    /** Throws WEATHER_UNAVAILABLE when the forecast cannot be loaded, INVALID_COORDINATES for bad coordinates. */
    public DayWeather dayFor(User user, Double lat, Double lon) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), DayWeatherCalculator.ZONE);
        return DayWeatherCalculator.calculate(weatherService.getForecast(lat, lon).slots(), today,
                user.getCommuteOutAt(), user.getCommuteBackAt());
    }
}
