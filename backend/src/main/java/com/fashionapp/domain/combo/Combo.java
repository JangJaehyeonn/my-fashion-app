package com.fashionapp.domain.combo;

import java.util.ArrayList;
import java.util.List;

/** One valid outfit: top + bottom + shoes, plus an optional outer (null when absent). */
public record Combo(ComboItem top, ComboItem bottom, ComboItem shoes, ComboItem outer) {

    /** Pieces in a fixed order: top, bottom, shoes, then outer if present. */
    public List<ComboItem> items() {
        List<ComboItem> items = new ArrayList<>(List.of(top, bottom, shoes));
        if (outer != null) {
            items.add(outer);
        }
        return items;
    }
}
