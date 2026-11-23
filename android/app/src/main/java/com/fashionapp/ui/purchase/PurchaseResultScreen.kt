package com.fashionapp.ui.purchase

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.fashionapp.data.model.ClothesSeason
import com.fashionapp.data.repository.CompressedPhoto
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

private val SubGray = Color(0xFF6B6458)
private val FaintGray = Color(0xFF8A8378)
private val Divider = Color(0xFFECE7DE)
private val BottomBarHeight = 76.dp

/** FR-18 result: summary, +N combos, verdict, similar clothes, combos, gap and the fixed decision buttons. */
@Composable
fun ResultStep(state: PurchaseCheckState, viewModel: PurchaseCheckViewModel) {
    val ui = state.resultUi ?: return
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = BottomBarHeight + 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ItemSummary(state.photo, ui, canEdit = state.canDecide, onEdit = viewModel::openAttributeSheet)
            if (state.expired) ExpiredNotice()
            PlusComboBlock(ui)
            ui.verdict?.let { VerdictCard(it, ui.headline, ui.reasons) }
            if (ui.similar.isNotEmpty()) SimilarSection(ui.similar)
            if (ui.outfits.isNotEmpty()) OutfitSection(state.photo, ui)
            ui.gap?.let { GapBox(it) }
        }
        DecisionBar(
            state = state,
            onSkip = viewModel::skip,
            onTryOn = viewModel::tryOn,
            onBuy = viewModel::buy,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        if (state.isRechecking) {
            Box(
                Modifier.fillMaxSize().background(WearonColors.Ivory.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = WearonColors.Ink) }
        }
    }
    if (state.attributeSheetOpen) {
        AttributeSheet(
            initial = AttributeEdit.of(state.result?.item),
            isSaving = state.isRechecking,
            onDismiss = viewModel::closeAttributeSheet,
            onSave = viewModel::recheck
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemSummary(photo: CompressedPhoto?, ui: PurchaseResultUi, canEdit: Boolean, onEdit: () -> Unit) {
    Row {
        PhotoBox(photo, Modifier.size(width = 96.dp, height = 120.dp))
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ui.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            ui.priceText?.let { Text(it, fontSize = 13.sp, color = SubGray) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ui.attributeChips.forEach { chip ->
                    Text(
                        chip,
                        fontSize = 12.sp,
                        color = WearonColors.Ink,
                        modifier = Modifier.clip(WearonShapes.Image).background(WearonColors.Beige)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                "AI가 본 정보가 틀렸나요? 고치기",
                fontSize = 12.sp,
                color = if (canEdit) SubGray else FaintGray,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.heightIn(min = 44.dp).clickable(enabled = canEdit, onClick = onEdit)
                    .padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ExpiredNotice() {
    Text(
        PurchaseErrors.MSG_EXPIRED,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = WearonColors.Accent,
        modifier = Modifier.fillMaxWidth().clip(WearonShapes.Card).background(WearonColors.Beige).padding(12.dp)
    )
}

@Composable
private fun PlusComboBlock(ui: PurchaseResultUi) {
    Column(Modifier.fillMaxWidth().border(1.dp, WearonColors.Ink, WearonShapes.Card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ui.plusText, fontSize = 48.sp, fontWeight = FontWeight.Black, color = WearonColors.Ink)
                Text("코디", fontSize = 13.sp, color = SubGray)
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("이 옷을 사면 코디가 늘어나요", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
                Text(ui.comboChangeText, fontSize = 14.sp, color = WearonColors.Ink)
                Text(ui.comboLine, fontSize = 12.sp, color = SubGray)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Divider)
        Text(
            ui.costPerWearText,
            fontSize = 13.sp,
            color = if (ui.hasCostPerWear) WearonColors.Ink else FaintGray
        )
    }
}

@Composable
private fun VerdictCard(style: VerdictStyle, headline: String?, reasons: List<String>) {
    val (background, textColor) = when (style.card) {
        VerdictCardTone.BEIGE -> WearonColors.Beige to WearonColors.Ink
        VerdictCardTone.INK -> WearonColors.Ink to WearonColors.White
        VerdictCardTone.WHITE -> WearonColors.White to WearonColors.Ink
    }
    val (badgeBackground, badgeText) = when (style.badge) {
        VerdictBadgeTone.BROWN -> WearonColors.Accent to WearonColors.White
        VerdictBadgeTone.WHITE -> WearonColors.White to WearonColors.Ink
        VerdictBadgeTone.LIGHT_BEIGE -> WearonColors.Beige to WearonColors.Ink
    }
    val outline = if (style.card == VerdictCardTone.WHITE) Modifier.border(1.dp, WearonColors.Line, WearonShapes.Card) else Modifier
    Column(
        Modifier.fillMaxWidth().clip(WearonShapes.Card).background(background).then(outline).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            style.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = badgeText,
            modifier = Modifier.clip(WearonShapes.Image).background(badgeBackground).padding(horizontal = 8.dp, vertical = 4.dp)
        )
        headline?.let { Text(it, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor) }
        reasons.forEach { Text("• $it", fontSize = 14.sp, color = textColor) }
    }
}

@Composable
private fun SectionTitle(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, fontSize = 12.sp, color = FaintGray) }
    }
}

@Composable
private fun SimilarSection(rows: List<SimilarRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("비슷한 내 옷")
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().clip(WearonShapes.Card).background(WearonColors.White)
                    .border(1.dp, WearonColors.Line, WearonShapes.Card).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UrlPhoto(row.photoUrl, row.name, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    row.note?.let { Text(it, fontSize = 12.sp, color = SubGray, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(row.wearText, fontSize = 12.sp, color = FaintGray)
                }
                Text(row.similarityText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WearonColors.Accent)
            }
        }
    }
}

@Composable
private fun OutfitSection(photo: CompressedPhoto?, ui: PurchaseResultUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("내 옷이랑 만드는 코디", ui.outfitHeaderText)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ui.outfits.forEach { outfit ->
                Column(
                    Modifier.clip(WearonShapes.Card).background(WearonColors.White)
                        .border(1.dp, WearonColors.Line, WearonShapes.Card).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(outfit.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NewPiece(photo)
                        outfit.photoUrls.forEach { url -> UrlPhoto(url, "내 옷", Modifier.size(width = 64.dp, height = 80.dp)) }
                    }
                }
            }
        }
    }
}

/** The new piece inside a combo: black border and a NEW mark at the top left. */
@Composable
private fun NewPiece(photo: CompressedPhoto?) {
    Box(Modifier.size(width = 64.dp, height = 80.dp).border(2.dp, WearonColors.Ink, WearonShapes.Image)) {
        PhotoBox(photo, Modifier.fillMaxSize().padding(2.dp))
        Text(
            "NEW",
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = WearonColors.White,
            modifier = Modifier.align(Alignment.TopStart).background(WearonColors.Ink).padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun GapBox(text: String) {
    Column(
        Modifier.fillMaxWidth().clip(WearonShapes.Card).background(WearonColors.Beige).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = WearonColors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("옷장 빈틈", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WearonColors.Accent)
        }
        Text(text, fontSize = 13.sp, color = WearonColors.Ink)
    }
}

@Composable
private fun DecisionBar(
    state: PurchaseCheckState,
    onSkip: () -> Unit,
    onTryOn: () -> Unit,
    onBuy: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier.fillMaxWidth().background(WearonColors.Ivory).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val outlined = ButtonDefaults.outlinedButtonColors(contentColor = WearonColors.Ink)
        OutlinedButton(
            onClick = onSkip,
            enabled = !state.isBusy,
            shape = WearonShapes.Card,
            colors = outlined,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp)
        ) { Text("안 살래요", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        OutlinedButton(
            onClick = onTryOn,
            enabled = !state.isBusy && state.photo != null,
            shape = WearonShapes.Card,
            colors = outlined,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp)
        ) { Text("입어보기", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        Button(
            onClick = onBuy,
            enabled = state.canDecide,
            shape = WearonShapes.Card,
            colors = ButtonDefaults.buttonColors(containerColor = WearonColors.Ink, contentColor = WearonColors.White),
            modifier = Modifier.weight(1.3f).heightIn(min = 52.dp)
        ) {
            if (state.isDeciding) {
                CircularProgressIndicator(Modifier.size(18.dp), color = WearonColors.White, strokeWidth = 2.dp)
            } else {
                Text("살래요", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** FR-13 attribute sheet: category, season and text attributes. Saving re-judges the check. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AttributeSheet(
    initial: AttributeEdit,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (AttributeEdit) -> Unit
) {
    var edit by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = WearonColors.White) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("정보 고치기", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
            Text("고친 정보로 다시 비교해 드려요", fontSize = 13.sp, color = SubGray)
            Text("종류", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ClothesCategory.entries.filter { it != ClothesCategory.UNCLASSIFIED }.forEach { category ->
                    ChoiceChip(category.label, edit.category == category) { edit = edit.copy(category = category) }
                }
            }
            Text("계절", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ClothesSeason.entries.forEach { season ->
                    ChoiceChip(season.label, edit.season == season) { edit = edit.copy(season = season) }
                }
            }
            SheetField("이름", edit.name) { edit = edit.copy(name = it) }
            SheetField("색상", edit.color) { edit = edit.copy(color = it) }
            SheetField("핏", edit.fit) { edit = edit.copy(fit = it) }
            SheetField("소재", edit.material) { edit = edit.copy(material = it) }
            SheetField("패턴", edit.pattern) { edit = edit.copy(pattern = it) }
            Button(
                onClick = { onSave(edit) },
                enabled = !isSaving,
                shape = WearonShapes.Card,
                colors = ButtonDefaults.buttonColors(containerColor = WearonColors.Ink, contentColor = WearonColors.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text(if (isSaving) "다시 비교하는 중" else "다시 비교하기", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(WearonShapes.Image)
            .background(if (selected) WearonColors.Ink else WearonColors.White)
            .border(1.dp, if (selected) WearonColors.Ink else WearonColors.Line, WearonShapes.Image)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) WearonColors.White else WearonColors.Ink)
    }
}

@Composable
private fun SheetField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(MAX_ATTRIBUTE_LENGTH)) },
        singleLine = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

/** Same limit as the server (PurchaseAttributesRequest.MAX_TEXT_LENGTH). */
private const val MAX_ATTRIBUTE_LENGTH = 50

/** FR-18 done step after 살래요. */
@Composable
fun DoneStep(state: PurchaseCheckState, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(56.dp).clip(CircleShape).background(WearonColors.Ink), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, contentDescription = "완료", tint = WearonColors.White, modifier = Modifier.size(30.dp))
        }
        Text("옷장에 등록했어요", fontSize = 20.sp, fontWeight = FontWeight.Black, color = WearonColors.Ink)
        state.doneMessage?.let { Text(it, fontSize = 14.sp, color = SubGray) }
        RegisteredCard(state.registered, state.photo, state.resultUi?.title)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onClose,
            shape = WearonShapes.Card,
            colors = ButtonDefaults.buttonColors(containerColor = WearonColors.Ink, contentColor = WearonColors.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("쇼핑으로 돌아가기", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun RegisteredCard(clothes: Clothes?, photo: CompressedPhoto?, fallbackTitle: String?) {
    Row(
        Modifier.fillMaxWidth().clip(WearonShapes.Card).background(WearonColors.White)
            .border(1.dp, WearonColors.Line, WearonShapes.Card).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val photoModifier = Modifier.size(width = 64.dp, height = 80.dp)
        if (clothes != null) UrlPhoto(clothes.imageUrl, "등록한 옷", photoModifier) else PhotoBox(photo, photoModifier)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                clothes?.name ?: fallbackTitle ?: PurchaseResultFormatter.DEFAULT_TITLE,
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink
            )
            val detail = listOfNotNull(clothes?.category?.label, clothes?.color).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, color = SubGray)
        }
    }
}

@Composable
private fun UrlPhoto(url: String?, description: String, modifier: Modifier) {
    Box(modifier.clip(WearonShapes.Image).background(WearonColors.Beige)) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = description, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PhotoBox(photo: CompressedPhoto?, modifier: Modifier) {
    Box(modifier.clip(WearonShapes.Image).background(WearonColors.Beige)) {
        if (photo != null) {
            AsyncImage(model = photo.jpeg, contentDescription = "고른 옷 사진", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        }
    }
}
