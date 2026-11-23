package com.fashionapp.ui.shopping

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

private val SubGray = Color(0xFF6B6458)
private val FaintGray = Color(0xFF8A8378)
private val Divider = Color(0xFFECE7DE)
private val SmallShape = RoundedCornerShape(4.dp)

/**
 * FR-16 shopping home. Capture, gallery and product URL lead to the purchase check flow (FR-17);
 * the virtual fitting card opens the existing FittingScreen.
 */
@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel = hiltViewModel(),
    onCapture: () -> Unit = {},
    onGallery: () -> Unit = {},
    onProductUrl: () -> Unit = {},
    onGoToCloset: () -> Unit = {},
    onOpenFitting: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Reload whenever the tab is shown again, so closet changes are reflected
    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = WearonColors.Ivory,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("쇼핑", fontSize = 24.sp, fontWeight = FontWeight.Black, color = WearonColors.Ink)
            CompareCard(
                enabled = state.canCompare,
                onCapture = onCapture,
                onGallery = onGallery,
                onProductUrl = onProductUrl
            )
            if (state.showClosetGuide) ClosetGuide(onGoToCloset)
            SummaryRow(state.closetCount, state.comboCount)
            RecentChecks(state.recentChecks)
            FittingCard(onOpenFitting)
        }
    }
}

@Composable
private fun CompareCard(
    enabled: Boolean,
    onCapture: () -> Unit,
    onGallery: () -> Unit,
    onProductUrl: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WearonShapes.Card)
            .background(WearonColors.Ink)
            .padding(20.dp)
    ) {
        Text("매장에서 고민될 때", fontSize = 12.sp, color = WearonColors.BeigeDeep)
        Spacer(Modifier.height(6.dp))
        Text(
            "살까 말까? 내 옷장이랑 비교해 드려요",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = WearonColors.White,
            lineHeight = 26.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCapture,
                enabled = enabled,
                shape = SmallShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WearonColors.White,
                    contentColor = WearonColors.Ink,
                    disabledContainerColor = WearonColors.White.copy(alpha = 0.3f),
                    disabledContentColor = WearonColors.Ink.copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { ButtonContent(Icons.Default.CameraAlt, "촬영") }
            OutlineDarkButton("갤러리", Icons.Default.PhotoLibrary, enabled, onGallery)
            OutlineDarkButton("상품 주소", Icons.Default.Link, enabled, onProductUrl)
        }
        Spacer(Modifier.height(12.dp))
        Text("옷만 나오게 찍으면 더 정확해요", fontSize = 12.sp, color = WearonColors.BeigeDeep)
    }
}

@Composable
private fun RowScope.OutlineDarkButton(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = SmallShape,
        border = BorderStroke(1.dp, WearonColors.White.copy(alpha = if (enabled) 0.8f else 0.3f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = WearonColors.White,
            disabledContentColor = WearonColors.White.copy(alpha = 0.4f)
        ),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
    ) { ButtonContent(icon, label) }
}

@Composable
private fun ButtonContent(icon: ImageVector, label: String) {
    // Text label sits next to the icon, so the icon itself is decorative
    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

@Composable
private fun ClosetGuide(onGoToCloset: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WearonColors.Line, WearonShapes.Card)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "옷장에 5벌 이상 있어야 비교할 수 있어요",
            fontSize = 13.sp,
            color = SubGray,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onGoToCloset,
            shape = SmallShape,
            colors = ButtonDefaults.buttonColors(containerColor = WearonColors.Ink, contentColor = WearonColors.White),
            modifier = Modifier.heightIn(min = 44.dp)
        ) { Text("옷장으로 가기", fontSize = 13.sp) }
    }
}

@Composable
private fun SummaryRow(closetCount: Int?, comboCount: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(WearonShapes.Card)
            .background(WearonColors.Beige)
            .padding(vertical = 14.dp)
    ) {
        SummaryCell("내 옷장", closetCount?.let { "${it}벌" } ?: "-")
        Box(Modifier.width(1.dp).fillMaxHeight().background(WearonColors.BeigeDeep))
        SummaryCell("만들 수 있는 코디", comboCount?.let { "${it}개" } ?: "-")
    }
}

@Composable
private fun RowScope.SummaryCell(label: String, value: String) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = SubGray)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
    }
}

@Composable
private fun RecentChecks(rows: List<RecentCheckRow>) {
    Column {
        Text("최근 판정", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text("아직 살까 말까 기록이 없어요", fontSize = 13.sp, color = FaintGray)
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
            RecentCheckItem(row)
        }
    }
}

@Composable
private fun RecentCheckItem(row: RecentCheckRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumbnail(row)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = WearonColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(row.subtitle, fontSize = 12.sp, color = SubGray)
        }
        row.plusText?.let {
            Text(it, fontSize = 15.sp, fontWeight = FontWeight.Black, color = WearonColors.Ink)
            Spacer(Modifier.width(8.dp))
        }
        row.badge?.let { ResultBadgeChip(it) }
    }
}

@Composable
private fun Thumbnail(row: RecentCheckRow) {
    val modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(WearonColors.Beige)
    if (row.photoUrl != null) {
        AsyncImage(
            model = row.photoUrl,
            contentDescription = row.title,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                categoryIcon(row.category),
                contentDescription = row.category?.label ?: "옷",
                tint = FaintGray,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

private fun categoryIcon(category: ClothesCategory?): ImageVector =
    if (category == ClothesCategory.OUTER) Icons.Default.DryCleaning else Icons.Default.Checkroom

@Composable
private fun ResultBadgeChip(badge: ResultBadge) {
    val bought = badge == ResultBadge.BOUGHT
    Text(
        badge.label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (bought) WearonColors.White else WearonColors.Ink,
        modifier = Modifier
            .clip(SmallShape)
            .background(if (bought) WearonColors.Ink else WearonColors.Beige)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun FittingCard(onOpenFitting: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WearonShapes.Card)
            .border(1.dp, WearonColors.Line, WearonShapes.Card)
            .background(WearonColors.White)
            .clickable(onClick = onOpenFitting)
            .heightIn(min = 44.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("가상 피팅", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
            Spacer(Modifier.height(2.dp))
            Text("판정 결과에서 바로 입어볼 수 있어요", fontSize = 13.sp, color = SubGray)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "가상 피팅 열기", tint = WearonColors.Ink)
    }
}
