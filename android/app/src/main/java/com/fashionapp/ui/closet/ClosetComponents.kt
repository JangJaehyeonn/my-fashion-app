package com.fashionapp.ui.closet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

// Chip order (null = 전체)
private val CATEGORY_CHIPS: List<ClothesCategory?> = listOf(
    null, ClothesCategory.TOP, ClothesCategory.BOTTOM, ClothesCategory.OUTER, ClothesCategory.SHOES, ClothesCategory.ETC
)

// ui-spec 옷장 탭 2: beige row with three cells; the last one sorts least worn first
@Composable
internal fun SummaryRow(summary: ClosetSummaryRow, onUnwornClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(WearonShapes.Card)
            .background(WearonColors.Beige),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryCell("만들 수 있는 코디", summary.comboCount?.toString() ?: "-", Modifier.weight(1f))
        SummaryDivider()
        SummaryCell("이번 달 착용", summary.monthWearCount?.let { "${it}회" } ?: "-", Modifier.weight(1f))
        SummaryDivider()
        SummaryCell(
            "잘 안 입는 옷",
            "${summary.unwornCount}벌 ›",
            Modifier.weight(1f).clickable(onClickLabel = "적게 입은 순으로 보기", onClick = onUnwornClick),
            valueColor = WearonColors.Accent
        )
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier, valueColor: Color = WearonColors.Ink) {
    Column(
        modifier = modifier.heightIn(min = 44.dp).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 11.sp, color = WearonColors.SubText, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
    }
}

@Composable
private fun SummaryDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 12.dp).background(WearonColors.Line))
}

// ui-spec 옷장 탭 3: fully rounded 38dp chips with counts; the selected one is black
@Composable
internal fun CategoryChips(
    selected: ClothesCategory?,
    counts: Map<ClothesCategory?, Int>,
    onSelect: (ClothesCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CATEGORY_CHIPS.forEach { category ->
            val isSelected = category == selected
            // 44dp touch target around the 38dp chip
            Box(
                modifier = Modifier.heightIn(min = 44.dp).clickable { onSelect(category) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${category?.label ?: "전체"} ${counts[category] ?: 0}",
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else WearonColors.Ink,
                    modifier = Modifier
                        .height(38.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) WearonColors.Ink else WearonColors.White)
                        .border(1.dp, if (isSelected) WearonColors.Ink else WearonColors.Line, CircleShape)
                        .padding(horizontal = 14.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }
        }
    }
}

// ui-spec 옷장 탭 4: "N벌" on the left, three sort text buttons on the right (selected: bold + underline)
@Composable
internal fun SortRow(count: Int, selected: ClosetSort, onSelect: (ClosetSort) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${count}벌", fontSize = 13.sp, color = WearonColors.SubText)
        Spacer(Modifier.weight(1f))
        ClosetSort.entries.forEach { sort ->
            val isSelected = sort == selected
            Box(
                modifier = Modifier.heightIn(min = 44.dp).clickable { onSelect(sort) }.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    sort.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) WearonColors.Ink else WearonColors.SubText,
                    textDecoration = if (isSelected) TextDecoration.Underline else TextDecoration.None
                )
            }
        }
    }
}

@Composable
internal fun ClothesCard(clothes: Clothes, badge: ClothesBadge?, onClick: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(WearonShapes.Card)
                .background(WearonColors.Beige)
                .then(photoBorder(badge))
        ) {
            AsyncImage(
                model = clothes.imageUrl,
                contentDescription = clothes.name,
                contentScale = ContentScale.Crop,
                // ui-spec: pieces in the laundry are dimmed to 50%
                modifier = Modifier.fillMaxSize().alpha(if (badge == ClothesBadge.LAUNDRY) 0.5f else 1f)
            )
            DeleteButton(onDelete, Modifier.align(Alignment.TopEnd))
            badge?.let { CardBadge(it, Modifier.align(Alignment.TopStart).padding(8.dp)) }
            SmallBadge(
                ClosetList.comboLabel(clothes),
                background = WearonColors.Ink,
                textColor = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            clothes.name ?: clothes.category.label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = WearonColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Spacer(Modifier.height(3.dp))
        CategoryColorLine(clothes)
        ClosetList.wearLabel(clothes)?.let { label ->
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (clothes.wearCount == 0) WearonColors.Accent else WearonColors.Ink,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

private fun photoBorder(badge: ClothesBadge?): Modifier = when (badge) {
    ClothesBadge.UNCLASSIFIED -> Modifier.dashedBorder(WearonColors.Accent)
    ClothesBadge.NEW -> Modifier.border(1.5.dp, WearonColors.Ink, WearonShapes.Card)
    else -> Modifier
}

// 44dp touch target; the visible circle stays small
@Composable
private fun DeleteButton(onDelete: () -> Unit, modifier: Modifier) {
    Box(modifier = modifier.size(44.dp).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "삭제", tint = WearonColors.Ink, modifier = Modifier.size(14.dp))
        }
    }
}

// Color dot + "카테고리 · 색상"
@Composable
private fun CategoryColorLine(clothes: Clothes) {
    Row(modifier = Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        clothes.color?.let(::colorSwatch)?.let { swatch ->
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(1.dp, WearonColors.Line, CircleShape)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            listOfNotNull(clothes.category.label, clothes.color).joinToString(" · "),
            fontSize = 12.sp,
            color = WearonColors.SubText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ui-spec: one top-left badge, styled per kind
@Composable
private fun CardBadge(badge: ClothesBadge, modifier: Modifier) {
    when (badge) {
        ClothesBadge.UNCLASSIFIED -> UnclassifiedBadge(modifier)
        ClothesBadge.LAUNDRY -> SmallBadge(badge.label, WearonColors.White, WearonColors.Ink, modifier)
        ClothesBadge.UNWORN -> SmallBadge(badge.label, WearonColors.White, WearonColors.Accent, modifier)
        ClothesBadge.NEW -> SmallBadge(badge.label, WearonColors.Ink, Color.White, modifier)
    }
}

@Composable
private fun SmallBadge(text: String, background: Color, textColor: Color, modifier: Modifier) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = modifier
            .clip(WearonShapes.Image)
            .background(background)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}
