package com.fashionapp.domain.outfit;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.domain.combo.ComboItem;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * FR-28 weather rules loaded from {@code rules/weather-rules.json} (format: docs/adr/0015-commute-weather-rules.md):
 * thresholds for a big daily temperature range and for rain, what rain excludes, and the comfortable temperature range
 * per category × estimated thickness (estimated from the material).
 */
public final class WeatherRules {

    public enum Thickness { THIN, MEDIUM, THICK }

    /** Inclusive comfortable temperature range in °C. */
    public record Range(double min, double max) {

        public Range {
            if (min > max) {
                throw new IllegalArgumentException("comfort range min > max: " + min + " > " + max);
            }
        }

        public boolean contains(double temperature) {
            return temperature >= min && temperature <= max;
        }
    }

    private final double tempRangeThreshold;
    private final int rainProbabilityThreshold;
    private final Set<String> rainExcludedMaterials;
    private final Set<String> rainExcludedShoeColors;
    private final Thickness defaultThickness;
    private final Map<String, Thickness> thicknessByMaterial;
    private final Map<ClothesCategory, Map<Thickness, Range>> comfortRanges;

    public WeatherRules(double tempRangeThreshold, int rainProbabilityThreshold,
                        Collection<String> rainExcludedMaterials, Collection<String> rainExcludedShoeColors,
                        Thickness defaultThickness, Map<String, Thickness> thicknessByMaterial,
                        Map<ClothesCategory, Map<Thickness, Range>> comfortRanges) {
        this.tempRangeThreshold = tempRangeThreshold;
        this.rainProbabilityThreshold = rainProbabilityThreshold;
        this.rainExcludedMaterials = normalizedSet(rainExcludedMaterials);
        this.rainExcludedShoeColors = normalizedSet(rainExcludedShoeColors);
        this.defaultThickness = defaultThickness;
        this.thicknessByMaterial = new HashMap<>();
        thicknessByMaterial.forEach((material, t) -> this.thicknessByMaterial.put(normalize(material), t));
        this.comfortRanges = new EnumMap<>(ClothesCategory.class);
        comfortRanges.forEach((category, ranges) -> this.comfortRanges.put(category, Map.copyOf(ranges)));
    }

    /** The day's temperature range is big (rule TEMP_RANGE) — the threshold itself counts as big. */
    public boolean bigTemperatureRange(double tempRange) {
        return tempRange >= tempRangeThreshold;
    }

    /** Rain is likely (rule RAIN) — the threshold itself counts. */
    public boolean rainLikely(int precipitationProbability) {
        return precipitationProbability >= rainProbabilityThreshold;
    }

    /** Pieces left out when rain is likely: the excluded materials (any category) and shoes in the excluded colors. */
    public boolean excludedInRain(ComboItem item) {
        if (item.material() != null && rainExcludedMaterials.contains(normalize(item.material()))) {
            return true;
        }
        return item.category() == ClothesCategory.SHOES
                && item.color() != null && rainExcludedShoeColors.contains(normalize(item.color()));
    }

    public Thickness thicknessOf(ComboItem item) {
        if (item.material() == null) {
            return defaultThickness;
        }
        return thicknessByMaterial.getOrDefault(normalize(item.material()), defaultThickness);
    }

    /** Whether the piece is comfortable at the temperature. Categories without ranges are always comfortable. */
    public boolean comfortableAt(ComboItem item, double temperature) {
        Map<Thickness, Range> ranges = comfortRanges.get(item.category());
        if (ranges == null) {
            return true;
        }
        Range range = ranges.get(thicknessOf(item));
        return range == null || range.contains(temperature);
    }

    private static Set<String> normalizedSet(Collection<String> values) {
        Set<String> set = new HashSet<>();
        values.forEach(v -> set.add(normalize(v)));
        return set;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
