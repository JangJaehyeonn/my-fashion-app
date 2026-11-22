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
 * Pure and deterministic: same closet and weather give the same plan.
 */
public class WeatherOutfitPlanner {

    public static final String TEMP_RANGE = "TEMP_RANGE";
    public static final String RAIN = "RAIN";

    /** Upper bound on candidates sent to the AI, so a big closet does not blow up the prompt. */
    static final int MAX_CANDIDATES = 30;

    /** @param excludedIds pieces left out by the rules; {@code candidates} are the combos the AI may pick from */
    public record Plan(List<String> appliedRules, Set<UUID> excludedIds, List<Combo> candidates) {

        public Plan {
            appliedRules = List.copyOf(appliedRules);
            excludedIds = Set.copyOf(excludedIds);
            candidates = List.copyOf(candidates);
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
        return new Plan(applied, excluded, combos.stream().limit(MAX_CANDIDATES).toList());
    }
}
