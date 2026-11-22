package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.ClothesCategory;

import java.util.List;
import java.util.Map;

/**
 * FR-11 rule-based sentences used when the AI explanation fails. They state the numbers only: they neither change the
 * verdict nor push towards or away from buying.
 */
final class PurchaseFallbackText {

    static final String DEFAULT_LABEL = "데일리";

    private static final Map<ClothesCategory, String> CATEGORY_LABELS = Map.of(
            ClothesCategory.TOP, "상의",
            ClothesCategory.BOTTOM, "하의",
            ClothesCategory.OUTER, "아우터",
            ClothesCategory.SHOES, "신발",
            ClothesCategory.ETC, "기타");

    private PurchaseFallbackText() {
    }

    static String headline(PurchaseContext context) {
        return switch (context.verdict()) {
            case SIMILAR -> "옷장에 비슷한 옷이 있어요";
            case BUY -> "코디가 " + context.plus() + "개 늘어나는 옷이에요";
            case THINK -> context.plus() > 0
                    ? "늘어나는 코디는 " + context.plus() + "개예요"
                    : "지금 옷장으로는 새 코디가 생기지 않아요";
        };
    }

    static List<String> reasons(PurchaseContext context) {
        String counts = "이 옷을 더하면 코디가 " + context.comboCountBefore() + "개에서 "
                + context.comboCountAfter() + "개가 돼요";
        String compared = "옷장의 " + categoryLabel(context.item().category()) + " "
                + context.sameCategoryCount() + "벌과 비교했어요";
        if (context.verdict() != PurchaseVerdict.SIMILAR || context.similar().isEmpty()) {
            return List.of(counts, "옷장에 많이 비슷한 옷은 없어요", compared);
        }
        PurchaseCheckResponse.Similar top = context.similar().get(0);
        String name = top.name() == null || top.name().isBlank() ? "이름 없는 옷" : top.name();
        String closest = "가장 비슷한 옷은 " + name + "이에요 (비슷한 정도 " + Math.round(top.similarity() * 100) + "%)";
        String worn = top.wearCount() > 0
                ? "가장 비슷한 옷은 지금까지 " + top.wearCount() + "번 입었어요"
                : "가장 비슷한 옷은 아직 입은 기록이 없어요";
        return List.of(closest, worn, counts);
    }

    static String categoryLabel(ClothesCategory category) {
        return CATEGORY_LABELS.getOrDefault(category, "옷");
    }
}
