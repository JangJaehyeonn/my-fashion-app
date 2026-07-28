package com.fashionapp.domain.weather;

import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.infra.AiWeatherResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AiWeatherResponse>> getWeather() {
        return ResponseEntity.ok(ApiResponse.success(weatherService.getWeather()));
    }
}
