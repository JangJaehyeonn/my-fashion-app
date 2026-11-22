package com.fashionapp.domain.outfit;

import com.fashionapp.domain.combo.ComboEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Builds the FR-28 weather outfit planner from the rules file. The location can point at another file
 * (e.g. {@code file:/etc/wearon/weather-rules.json}) to tune thresholds and ranges without a code change.
 */
@Configuration
public class WeatherRulesConfig {

    @Bean
    public WeatherOutfitPlanner weatherOutfitPlanner(
            ResourceLoader resourceLoader, ComboEngine comboEngine,
            @Value("${wearon.weather.rules-location:classpath:rules/weather-rules.json}") String location)
            throws IOException {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream in = resource.getInputStream()) {
            return new WeatherOutfitPlanner(WeatherRulesLoader.load(in), comboEngine);
        }
    }
}
