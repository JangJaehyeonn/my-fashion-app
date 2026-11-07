package com.fashionapp.infra;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AiWeatherResponse {
    private double temperature;
    private String condition;
    private int humidity;
    private double windSpeed;
}
