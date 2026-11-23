package com.fashionapp.domain.weather;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.jwt.CurrentUser;
import com.fashionapp.infra.AiWeatherResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final DayWeatherService dayWeatherService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AiWeatherResponse>> getWeather(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon) {
        return ResponseEntity.ok(ApiResponse.success(weatherService.getWeather(lat, lon)));
    }

    /** FR-28: temperature, precipitation probability and wind at going out, midday (13:00) and coming back. */
    @GetMapping("/day")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DayWeather>> getDayWeather(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon) {
        return ResponseEntity.ok(ApiResponse.success(dayWeatherService.getDay(userPrincipal.getId(), lat, lon)));
    }
}
