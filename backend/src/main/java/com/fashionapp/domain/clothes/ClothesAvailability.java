package com.fashionapp.domain.clothes;

import com.fashionapp.domain.wear.WearCalendar;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * FR-27 "clothes I can wear now", computed when read (no batch job): effective status is AVAILABLE and not LAUNDRY,
 * the piece is not on its category's wear cooldown, and it is classified. Time comes from the injected clock.
 */
public class ClothesAvailability {

    private final AvailabilityRules rules;
    private final Clock clock;

    public ClothesAvailability(AvailabilityRules rules, Clock clock) {
        this.rules = rules;
        this.clock = clock;
    }

    /** When a piece set to LAUNDRY now counts as AVAILABLE again (stored in clothes.status_until). */
    public LocalDateTime laundryUntil() {
        return LocalDateTime.now(clock).plusDays(rules.laundryDays());
    }

    /** LAUNDRY whose period has passed (or that has no end time) reads as AVAILABLE. */
    public ClothesStatus effectiveStatus(Clothes clothes) {
        if (ClothesStatus.from(clothes.getStatus()) != ClothesStatus.LAUNDRY) {
            return ClothesStatus.AVAILABLE;
        }
        LocalDateTime until = clothes.getStatusUntil();
        return until != null && LocalDateTime.now(clock).isBefore(until) ? ClothesStatus.LAUNDRY : ClothesStatus.AVAILABLE;
    }

    /** True while the piece's last wear day is fewer than its category's cooldown days ago (Korean calendar day). */
    public boolean onCooldown(ClothesCategory category, LocalDate lastWornOn) {
        if (lastWornOn == null) {
            return false;
        }
        long daysSince = ChronoUnit.DAYS.between(lastWornOn, WearCalendar.today(clock));
        return daysSince < rules.cooldownOf(category);
    }

    public boolean isAvailable(Clothes clothes, LocalDate lastWornOn) {
        ClothesCategory category = ClothesCategory.from(clothes.getCategory());
        return category != ClothesCategory.UNCLASSIFIED
                && effectiveStatus(clothes) == ClothesStatus.AVAILABLE
                && !onCooldown(category, lastWornOn);
    }
}
