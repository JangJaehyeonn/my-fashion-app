package com.fashionapp.domain.combo;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validity rules for outfit combos, loaded from the JSON rules file (docs/adr/0008-combo-count-rules.md).
 * Two pieces conflict when any rule pair matches their values. A null value never conflicts.
 */
public final class ComboRules {

    /** Attributes a style conflict rule may look at. */
    public enum StyleAttribute { PATTERN, FIT, MATERIAL }

    private final Set<String> neutralColors;
    private final Set<String> colorConflicts;
    private final Set<String> seasonConflicts;
    private final StyleAttribute styleAttribute;
    private final Set<String> styleConflicts;

    public ComboRules(Collection<String> neutralColors, List<List<String>> colorConflicts,
                      List<List<String>> seasonConflicts, StyleAttribute styleAttribute,
                      List<List<String>> styleConflicts) {
        this.neutralColors = new HashSet<>();
        neutralColors.forEach(c -> this.neutralColors.add(normalize(c)));
        this.colorConflicts = pairKeys(colorConflicts);
        this.seasonConflicts = pairKeys(seasonConflicts);
        this.styleAttribute = styleAttribute;
        this.styleConflicts = pairKeys(styleConflicts);
    }

    public boolean compatible(ComboItem a, ComboItem b) {
        return !colorConflict(a.color(), b.color())
                && !conflict(seasonConflicts, a.season(), b.season())
                && !conflict(styleConflicts, styleValue(a), styleValue(b));
    }

    private boolean colorConflict(String a, String b) {
        if (a == null || b == null || neutralColors.contains(normalize(a)) || neutralColors.contains(normalize(b))) {
            return false;
        }
        return conflict(colorConflicts, a, b);
    }

    private String styleValue(ComboItem item) {
        return switch (styleAttribute) {
            case PATTERN -> item.pattern();
            case FIT -> item.fit();
            case MATERIAL -> item.material();
        };
    }

    private static boolean conflict(Set<String> pairs, String a, String b) {
        return a != null && b != null && pairs.contains(key(a, b));
    }

    private static Set<String> pairKeys(List<List<String>> pairs) {
        Set<String> keys = new HashSet<>();
        for (List<String> pair : pairs) {
            if (pair == null || pair.size() != 2 || pair.get(0) == null || pair.get(1) == null) {
                throw new IllegalArgumentException("conflict rule must be a pair of two values: " + pair);
            }
            keys.add(key(pair.get(0), pair.get(1)));
        }
        return keys;
    }

    private static String key(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        return x.compareTo(y) <= 0 ? x + "\u0000" + y : y + "\u0000" + x;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
