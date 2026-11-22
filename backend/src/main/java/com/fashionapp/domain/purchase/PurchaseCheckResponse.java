package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.ClothesCategory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * FR-11 POST /api/purchase-checks response. verdict, plus, the combo counts, similar (ids, similarity, wears) and
 * expectedWears/costPerWear come from rules; headline, reasons, gap, notes and outfit labels from the AI (or the
 * rule-based fallback sentences).
 */
public record PurchaseCheckResponse(UUID id, Item item, PurchaseVerdict verdict, int plus, int comboCountBefore,
                                    int comboCountAfter, List<Similar> similar, List<Outfit> outfits,
                                    String headline, List<String> reasons, String gap, Integer expectedWears,
                                    Long price, Long costPerWear) {

    /** The analysed piece (null attribute = unknown). */
    public record Item(ClothesCategory category, String color, String name, String fit, String material,
                       String pattern, String season) {
    }

    /** A closet piece of the same category, most similar first. note is the AI's one-line difference (nullable). */
    public record Similar(UUID id, String name, double similarity, int wearCount, LocalDate lastWornAt,
                          String note) {
    }

    /** A representative combo with the new piece: closet clothes ids only (the new piece is implied). */
    public record Outfit(List<UUID> clothesIds, String label) {
    }
}
