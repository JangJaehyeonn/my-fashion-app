package com.fashionapp.domain.purchase;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * FR-11 expected wears per year and cost per wear. Pure: "today" is passed in.
 *
 * <p>expectedWears = round(wears of the same category in the last 90 days × (plus ÷ valid combos containing that
 * category before the purchase) × 365 ÷ 90). Null when the wear history is shorter than 30 days (first record to
 * today) or the category is in no combo yet. costPerWear = round(price ÷ expectedWears) when there is a price and
 * expectedWears ≥ 1, otherwise null.
 */
public final class ExpectedWears {

    public static final int WINDOW_DAYS = 90;
    public static final int MIN_HISTORY_DAYS = 30;
    private static final double DAYS_PER_YEAR = 365.0;

    private ExpectedWears() {
    }

    /** First day of the 90-day window ending today (inclusive). */
    public static LocalDate windowStart(LocalDate today) {
        return today.minusDays(WINDOW_DAYS - 1L);
    }

    public static Integer expectedWears(long recentCategoryWears, int plus, int categoryCombos,
                                        LocalDate firstWornOn, LocalDate today) {
        if (firstWornOn == null || ChronoUnit.DAYS.between(firstWornOn, today) < MIN_HISTORY_DAYS) {
            return null;
        }
        if (categoryCombos <= 0) {
            return null;
        }
        double perWindow = recentCategoryWears * ((double) plus / categoryCombos);
        return (int) Math.round(perWindow * DAYS_PER_YEAR / WINDOW_DAYS);
    }

    public static Long costPerWear(Long price, Integer expectedWears) {
        if (price == null || expectedWears == null || expectedWears < 1) {
            return null;
        }
        return Math.round((double) price / expectedWears);
    }

    /** FR-20: price ÷ wears so far in won (rounded); null without a price or before the first wear. */
    public static Long actualCostPerWear(Long price, Long wears) {
        if (price == null || wears == null || wears < 1) {
            return null;
        }
        return Math.round((double) price / wears);
    }
}
