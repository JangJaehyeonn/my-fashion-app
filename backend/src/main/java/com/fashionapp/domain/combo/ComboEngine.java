package com.fashionapp.domain.combo;

import com.fashionapp.domain.clothes.ClothesCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * FR-09 combo count engine. A combo is top 1 + bottom 1 + shoes 1 (+ outer 0..1) where every pair of pieces is
 * compatible under {@link ComboRules}. Pure and deterministic: no clock, no randomness, input order does not matter.
 */
public class ComboEngine {

    private static final Comparator<ComboItem> ORDER =
            Comparator.comparing(ComboItem::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ComboRules rules;

    public ComboEngine(ComboRules rules) {
        this.rules = rules;
    }

    /** (1) total valid combos of the closet and (2) per piece, the valid combos it is in. */
    public ComboStats count(List<ComboItem> closet) {
        Map<ComboItem, Integer> perItem = new IdentityHashMap<>();
        int[] total = {0};
        forEachCombo(closet, combo -> {
            total[0]++;
            combo.items().forEach(item -> perItem.merge(item, 1, Integer::sum));
        });
        Map<UUID, Integer> perClothes = new HashMap<>();
        perItem.forEach((item, n) -> {
            if (item.id() != null) {
                perClothes.merge(item.id(), n, Integer::sum);
            }
        });
        return new ComboStats(total[0], perClothes);
    }

    /** (3) combos gained (+N) by adding a hypothetical piece, and the valid combos that contain it. */
    public ComboAddition addition(List<ComboItem> closet, ComboItem candidate) {
        int before = count(closet).total();
        List<ComboItem> withCandidate = new ArrayList<>(closet);
        withCandidate.add(candidate);
        List<Combo> gained = new ArrayList<>();
        forEachCombo(withCandidate, combo -> {
            if (combo.items().stream().anyMatch(item -> item == candidate)) {
                gained.add(combo);
            }
        });
        return new ComboAddition(gained.size(), before, before + gained.size(), gained);
    }

    private void forEachCombo(List<ComboItem> closet, Consumer<Combo> visitor) {
        List<ComboItem> tops = ofCategory(closet, ClothesCategory.TOP);
        List<ComboItem> bottoms = ofCategory(closet, ClothesCategory.BOTTOM);
        List<ComboItem> shoes = ofCategory(closet, ClothesCategory.SHOES);
        List<ComboItem> outers = ofCategory(closet, ClothesCategory.OUTER);
        for (ComboItem top : tops) {
            for (ComboItem bottom : bottoms) {
                if (!rules.compatible(top, bottom)) {
                    continue;
                }
                for (ComboItem shoe : shoes) {
                    if (fits(shoe, top, bottom)) {
                        visitWithOuters(new Combo(top, bottom, shoe, null), outers, visitor);
                    }
                }
            }
        }
    }

    private void visitWithOuters(Combo base, List<ComboItem> outers, Consumer<Combo> visitor) {
        visitor.accept(base);
        for (ComboItem outer : outers) {
            if (fits(outer, base.top(), base.bottom(), base.shoes())) {
                visitor.accept(new Combo(base.top(), base.bottom(), base.shoes(), outer));
            }
        }
    }

    private boolean fits(ComboItem piece, ComboItem... others) {
        for (ComboItem other : others) {
            if (!rules.compatible(piece, other)) {
                return false;
            }
        }
        return true;
    }

    private static List<ComboItem> ofCategory(List<ComboItem> closet, ClothesCategory category) {
        return closet.stream()
                .filter(item -> item.category() == category)
                .sorted(ORDER)
                .toList();
    }
}
