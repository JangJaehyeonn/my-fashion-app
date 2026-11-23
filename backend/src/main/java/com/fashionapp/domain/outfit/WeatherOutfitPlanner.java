package com.fashionapp.domain.outfit;

import com.fashionapp.domain.combo.Combo;
import com.fashionapp.domain.combo.ComboEngine;
import com.fashionapp.domain.combo.ComboItem;
import com.fashionapp.domain.weather.DayWeather;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FR-28: decides by rules alone which pieces and combos the AI may pick from for today's weather.
 * <ul>
 *   <li>RAIN — the highest precipitation probability of the three moments is at or above the threshold:
 *       the rain-excluded pieces (suede, white shoes) are dropped before combos are built.</li>
 *   <li>TEMP_RANGE — the day's temperature range is at or above the threshold: only combos that still work with the
 *       outer taken off are kept, i.e. the top/bottom/shoes base is valid (always true for engine combos) and the top
 *       is comfortable at the midday temperature. If no combo survives, the rule is not applied.</li>
 * </ul>
 * Pure and deterministic: same closet and weather give the same plan. The candidate cap and the FR-29 revival order
 * are applied afterwards by {@link ClosetPlanner}.
 */
public class WeatherOutfitPlanner {

    public static final String TEMP_RANGE = "TEMP_RANGE";
    public static final String RAIN = "RAIN";

    /** @param excludedIds pieces left out by the rules; {@code allowed} every combo the rules accept, in engine order */
    public record Plan(List<String> appliedRules, Set<UUID> excludedIds, List<Combo> allowed) {

        public Plan {
            appliedRules = List.copyOf(appliedRules);
            excludedIds = Set.copyOf(excludedIds);
            allowed = List.copyOf(allowed);
        }
    }

    private final WeatherRules rules;
    private final ComboEngine engine;

    public WeatherOutfitPlanner(WeatherRules rules, ComboEngine engine) {
        this.rules = rules;
        this.engine = engine;
    }

    public Plan plan(List<ComboItem> closet, DayWeather weather) {
        List<String> applied = new ArrayList<>();
        Set<UUID> excluded = Set.of();
        List<ComboItem> usable = closet;
        if (rules.rainLikely(weather.maxPrecipitationProbability())) {
            applied.add(RAIN);
            excluded = closet.stream().filter(rules::excludedInRain).map(ComboItem::id).collect(Collectors.toSet());
            usable = closet.stream().filter(item -> !rules.excludedInRain(item)).toList();
        }

        List<Combo> combos = engine.combos(usable);
        if (rules.bigTemperatureRange(weather.tempRange())) {
            double midday = weather.day().temperature();
            List<Combo> withoutOuter = combos.stream()
                    .filter(combo -> rules.comfortableAt(combo.top(), midday))
                    .toList();
            if (!withoutOuter.isEmpty()) {
                applied.add(TEMP_RANGE);
                combos = withoutOuter;
            }
        }
        return new Plan(applied, excluded, combos);
    }
}
