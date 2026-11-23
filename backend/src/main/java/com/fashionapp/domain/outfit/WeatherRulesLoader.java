package com.fashionapp.domain.outfit;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses the weather rules JSON file (format: docs/adr/0016-commute-weather-rules.md). Fails fast on a bad file. */
public final class WeatherRulesLoader {

    static final int SUPPORTED_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private WeatherRulesLoader() {
    }

    record ThicknessFile(@JsonProperty("default") String defaultValue, Map<String, String> materials) {
    }

    record RulesFile(Integer version, Double tempRangeThreshold, Integer rainProbabilityThreshold,
                     List<String> rainExcludedMaterials, List<String> rainExcludedShoeColors,
                     ThicknessFile thickness, Map<String, Map<String, List<Double>>> comfortRanges) {
    }

    public static WeatherRules load(InputStream json) throws IOException {
        RulesFile file = MAPPER.readValue(json, RulesFile.class);
        if (file.version() == null || file.version() != SUPPORTED_VERSION) {
            throw new IllegalArgumentException("unsupported weather rules version: " + file.version());
        }
        if (file.tempRangeThreshold() == null || file.rainProbabilityThreshold() == null) {
            throw new IllegalArgumentException("tempRangeThreshold and rainProbabilityThreshold are required");
        }
        ThicknessFile thickness = file.thickness() == null ? new ThicknessFile("MEDIUM", Map.of()) : file.thickness();
        Map<String, WeatherRules.Thickness> byMaterial = new HashMap<>();
        orEmpty(thickness.materials()).forEach((material, value) -> byMaterial.put(material, parseThickness(value)));
        return new WeatherRules(file.tempRangeThreshold(), file.rainProbabilityThreshold(),
                orEmpty(file.rainExcludedMaterials()), orEmpty(file.rainExcludedShoeColors()),
                parseThickness(thickness.defaultValue() == null ? "MEDIUM" : thickness.defaultValue()),
                byMaterial, parseRanges(orEmpty(file.comfortRanges())));
    }

    private static Map<ClothesCategory, Map<WeatherRules.Thickness, WeatherRules.Range>> parseRanges(
            Map<String, Map<String, List<Double>>> raw) {
        Map<ClothesCategory, Map<WeatherRules.Thickness, WeatherRules.Range>> ranges = new EnumMap<>(ClothesCategory.class);
        raw.forEach((categoryName, byThickness) -> {
            ClothesCategory category = ClothesCategory.parseAssignable(categoryName)
                    .orElseThrow(() -> new IllegalArgumentException("unknown category in comfortRanges: " + categoryName));
            Map<WeatherRules.Thickness, WeatherRules.Range> perThickness = new EnumMap<>(WeatherRules.Thickness.class);
            orEmpty(byThickness).forEach((t, pair) -> perThickness.put(parseThickness(t), parseRange(pair)));
            ranges.put(category, perThickness);
        });
        return ranges;
    }

    private static WeatherRules.Range parseRange(List<Double> pair) {
        if (pair == null || pair.size() != 2 || pair.get(0) == null || pair.get(1) == null) {
            throw new IllegalArgumentException("comfort range must be [min, max]: " + pair);
        }
        return new WeatherRules.Range(pair.get(0), pair.get(1));
    }

    private static WeatherRules.Thickness parseThickness(String value) {
        try {
            return WeatherRules.Thickness.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("unknown thickness: " + value);
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <K, V> Map<K, V> orEmpty(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }
}
