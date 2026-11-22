package com.fashionapp.domain.clothes;

import java.util.Map;

/**
 * FR-27 rules for "clothes I can wear now" (docs/adr/0014-clothes-status-cooldown.md).
 * {@code laundryDays}: a LAUNDRY piece counts as AVAILABLE again after this many days.
 * {@code cooldownDays}: a piece worn on day D is on cooldown while (today - D) is less than its category's days.
 */
public record AvailabilityRules(int laundryDays, Map<ClothesCategory, Integer> cooldownDays) {

    public AvailabilityRules {
        if (laundryDays < 0) {
            throw new IllegalArgumentException("laundryDays must be >= 0: " + laundryDays);
        }
        cooldownDays.forEach((category, days) -> {
            if (days == null || days < 0) {
                throw new IllegalArgumentException("cooldown days must be >= 0 for " + category);
            }
        });
        cooldownDays = Map.copyOf(cooldownDays);
    }

    /** Requirement defaults: laundry 3 days, cooldown top 3, bottom 2, outer 0, shoes 0. */
    public static AvailabilityRules defaults() {
        return new AvailabilityRules(3, Map.of(
                ClothesCategory.TOP, 3, ClothesCategory.BOTTOM, 2,
                ClothesCategory.OUTER, 0, ClothesCategory.SHOES, 0));
    }

    /** Categories without an entry have no cooldown. */
    public int cooldownOf(ClothesCategory category) {
        return cooldownDays.getOrDefault(category, 0);
    }
}
