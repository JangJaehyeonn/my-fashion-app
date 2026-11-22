package com.fashionapp.domain.combo;

import java.util.Map;
import java.util.UUID;

/** Combo counts of a closet: the total and, per clothes id, the number of valid combos that piece is in. */
public record ComboStats(int total, Map<UUID, Integer> perClothes) {

    public ComboStats {
        perClothes = Map.copyOf(perClothes);
    }

    /** Combo count of one piece; 0 for pieces that are not in any combo (e.g. unclassified). */
    public int countOf(UUID clothesId) {
        return perClothes.getOrDefault(clothesId, 0);
    }
}
