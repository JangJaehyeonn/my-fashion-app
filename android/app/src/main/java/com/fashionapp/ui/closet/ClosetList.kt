package com.fashionapp.ui.closet

import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory

/** FR-19 sort orders of the closet grid. */
enum class ClosetSort(val label: String) {
    MOST_COMBOS("코디 많은 순"),
    LEAST_WORN("적게 입은 순"),
    RECENT("최근 등록")
}

/** Top-left card badge. Only one is shown, in this priority order (ui-spec). */
enum class ClothesBadge(val label: String) {
    LAUNDRY("세탁 중"),
    UNCLASSIFIED("미분류 · 확인 필요"),
    UNWORN("잘 안 입는 옷"),
    NEW("NEW")
}

/** Summary row: combos the closet can make, wears this month, classified pieces never worn. */
data class ClosetSummaryRow(
    val comboCount: Int? = null,
    val monthWearCount: Int? = null,
    val unwornCount: Int = 0
)

object ClosetList {

    // Newest first; createdAt is ISO-8601 so string order is time order. Missing dates go last.
    private val RECENT_FIRST: Comparator<Clothes> =
        compareByDescending<Clothes, String?>(nullsFirst()) { it.createdAt }

    private val TIE_BREAK: Comparator<Clothes> = RECENT_FIRST.thenBy { it.id }

    /** Classified, the server says it was never worn. Unknown (null) counts are not treated as zero. */
    fun isUnworn(clothes: Clothes): Boolean = !clothes.isUnclassified && clothes.wearCount == 0

    fun filter(clothes: List<Clothes>, category: ClothesCategory?): List<Clothes> =
        if (category == null) clothes else clothes.filter { it.category == category }

    fun sort(clothes: List<Clothes>, sort: ClosetSort): List<Clothes> = clothes.sortedWith(comparator(sort))

    private fun comparator(sort: ClosetSort): Comparator<Clothes> = when (sort) {
        ClosetSort.MOST_COMBOS -> compareByDescending<Clothes> { it.comboCount ?: 0 }.then(TIE_BREAK)
        // Fewest wears first; among equals the one worn longest ago (never worn = no date) comes first
        ClosetSort.LEAST_WORN -> compareBy<Clothes> { it.wearCount ?: 0 }
            .thenBy(nullsFirst()) { it.lastWornAt }
            .then(TIE_BREAK)
        ClosetSort.RECENT -> TIE_BREAK
    }

    fun badge(clothes: Clothes, boughtIds: Set<String>): ClothesBadge? = when {
        clothes.isInLaundry -> ClothesBadge.LAUNDRY
        clothes.isUnclassified -> ClothesBadge.UNCLASSIFIED
        isUnworn(clothes) -> ClothesBadge.UNWORN
        clothes.id in boughtIds -> ClothesBadge.NEW
        else -> null
    }

    /** Bottom-left photo badge: the server's combo count, or "코디 계산 전" for unclassified pieces. */
    fun comboLabel(clothes: Clothes): String =
        if (clothes.isUnclassified || clothes.comboCount == null) "코디 계산 전" else "코디 ${clothes.comboCount}개"

    /** Wear line under the card: "착용 N회", or "아직 안 입었어요" for zero. Null when the server sent no count. */
    fun wearLabel(clothes: Clothes): String? = when (val count = clothes.wearCount) {
        null -> null
        0 -> "아직 안 입었어요"
        else -> "착용 ${count}회"
    }

    fun countByCategory(clothes: List<Clothes>): Map<ClothesCategory?, Int> = buildMap {
        put(null, clothes.size)
        ClothesCategory.entries.forEach { c -> put(c, clothes.count { it.category == c }) }
    }
}
