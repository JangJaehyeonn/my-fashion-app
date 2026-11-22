package com.fashionapp.domain.outfit;

import com.fashionapp.domain.combo.ComboTestSupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Builds the weather rules / planner from the real default rules file (src/main/resources/rules/weather-rules.json). */
final class WeatherRulesTestSupport {

    private WeatherRulesTestSupport() {
    }

    static WeatherRules defaultRules() {
        try (InputStream in = WeatherRulesTestSupport.class.getResourceAsStream("/rules/weather-rules.json")) {
            return WeatherRulesLoader.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static WeatherOutfitPlanner defaultPlanner() {
        return new WeatherOutfitPlanner(defaultRules(), ComboTestSupport.defaultEngine());
    }
}
