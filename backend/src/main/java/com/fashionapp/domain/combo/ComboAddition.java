package com.fashionapp.domain.combo;

import java.util.List;

/**
 * Result of adding a hypothetical new piece to a closet: {@code plus} new valid combos (+N), the totals before and
 * after, and the valid combos that contain the new piece ({@code combos.size() == plus}).
 */
public record ComboAddition(int plus, int before, int after, List<Combo> combos) {

    public ComboAddition {
        combos = List.copyOf(combos);
    }
}
