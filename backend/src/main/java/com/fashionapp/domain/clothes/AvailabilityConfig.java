package com.fashionapp.domain.clothes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;

/**
 * Builds the FR-27 availability calculator from the rules file. The location can point at another file
 * (e.g. {@code file:/etc/wearon/availability-rules.json}) to tune days without a code change.
 */
@Configuration
public class AvailabilityConfig {

    @Bean
    public ClothesAvailability clothesAvailability(
            ResourceLoader resourceLoader, Clock clock,
            @Value("${wearon.availability.rules-location:classpath:rules/availability-rules.json}") String location)
            throws IOException {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream in = resource.getInputStream()) {
            return new ClothesAvailability(AvailabilityRulesLoader.load(in), clock);
        }
    }
}
