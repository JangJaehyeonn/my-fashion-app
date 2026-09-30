package com.fashionapp.ui.home

import com.fashionapp.data.model.ClosetOutfit
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory

data class LookCard(
    val items: List<Clothes>,
    val reason: String,
    val styleTag: String
)

// 카드에 표시되는 순서 (아우터 → 상의 → 하의 → 신발 → 기타)
private val DISPLAY_ORDER = listOf(
    ClothesCategory.OUTER, ClothesCategory.TOP, ClothesCategory.BOTTOM, ClothesCategory.SHOES, ClothesCategory.ETC
)

fun ClosetOutfit.toLookCard() = LookCard(
    items = items.sortedBy { DISPLAY_ORDER.indexOf(it.category) },
    reason = reason,
    styleTag = styleTag
)
