package com.fashionapp.ui.closet

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.ui.common.createCameraImageUri
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

// 갤러리에서 한 번에 고를 수 있는 최대 장수
private const val MAX_PICK = 10

// 탭 노출 순서 (null = 전체)
private val CATEGORY_TABS: List<ClothesCategory?> = listOf(
    null, ClothesCategory.TOP, ClothesCategory.BOTTOM, ClothesCategory.OUTER, ClothesCategory.SHOES, ClothesCategory.ETC
)

@Composable
fun ClosetScreen(viewModel: ClosetViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val clothes by viewModel.filteredClothes.collectAsState()
    val counts by viewModel.countByCategory.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val registerProgress by viewModel.registerProgress.collectAsState()
    val isRegistering = registerProgress != null
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddMenu by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var deleteTarget by remember { mutableStateOf<Clothes?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) viewModel.register(context, uri)
    }

    fun launchCamera() {
        val uri = createCameraImageUri(context)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else viewModel.setMessage("카메라 권한이 필요합니다.")
    }

    // 포토 피커 다중 선택 — 한 번에 너무 많이 올리면 AI 분류 대기가 길어지므로 최대 장수 제한
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PICK)
    ) { uris: List<Uri> ->
        viewModel.registerAll(context, uris)
    }

    fun requestCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = WearonColors.Ivory,
        // 바깥 AppNavigation Scaffold가 이미 시스템 바 인셋을 적용하므로 중복 적용 방지
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("옷장", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
                Spacer(Modifier.width(6.dp))
                Text("${counts[null] ?: 0}", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = WearonColors.SubText)
                Spacer(Modifier.weight(1f))
                Box {
                    Row(
                        modifier = Modifier
                            .clip(WearonShapes.Image)
                            .background(if (isRegistering) WearonColors.Line else WearonColors.Ink)
                            .clickable(enabled = !isRegistering) { showAddMenu = true }
                            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("옷 등록", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("📷  사진 촬영") },
                            onClick = { showAddMenu = false; requestCamera() }
                        )
                        DropdownMenuItem(
                            text = { Text("🖼️  갤러리에서 선택 (최대 ${MAX_PICK}장)") },
                            onClick = {
                                showAddMenu = false
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            CategoryTabs(
                selected = selectedCategory,
                counts = counts,
                onSelect = viewModel::selectCategory
            )

            registerProgress?.let { progress ->
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .clip(WearonShapes.Card)
                        .background(WearonColors.Beige)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WearonColors.Ink)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (progress.total > 1) "${progress.current}/${progress.total} 등록 중… AI가 옷을 분류하고 있어요"
                            else "AI가 옷을 분류하고 있어요…",
                            fontSize = 13.sp,
                            color = WearonColors.Ink
                        )
                    }
                    if (progress.total > 1) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            // 완료된 장수 기준 진행률 (처리 중인 사진은 아직 미포함)
                            progress = { (progress.current - 1).toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = WearonColors.Ink,
                            trackColor = WearonColors.White
                        )
                    }
                }
            }

            when {
                isLoading && clothes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WearonColors.BeigeDeep)
                }
                clothes.isEmpty() -> EmptyCloset(isFiltered = selectedCategory != null)
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    // 좌우 여백과 간격을 줄여 사진을 최대한 크게 (쇼핑몰 상품 그리드 느낌)
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(clothes, key = { it.id }) { item ->
                        ClothesCard(item, onDelete = { deleteTarget = item })
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("옷을 삭제할까요?") },
            text = { Text(target.name ?: target.category.label) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(target); deleteTarget = null }) {
                    Text("삭제", color = Color(0xFFD64545))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소", color = WearonColors.Ink) }
            },
            containerColor = WearonColors.White
        )
    }
}

// 쇼핑몰 카테고리 탭처럼 텍스트 + 하단 밑줄 인디케이터
@Composable
private fun CategoryTabs(
    selected: ClothesCategory?,
    counts: Map<ClothesCategory?, Int>,
    onSelect: (ClothesCategory?) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(WearonColors.Line)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            CATEGORY_TABS.forEach { category ->
                val isSelected = category == selected
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onSelect(category) }
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            category?.label ?: "전체",
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) WearonColors.Ink else WearonColors.SubText
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "${counts[category] ?: 0}",
                            fontSize = 12.sp,
                            color = if (isSelected) WearonColors.Ink else WearonColors.SubText.copy(alpha = 0.7f)
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (isSelected) WearonColors.Ink else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClothesCard(clothes: Clothes, onDelete: () -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .clip(WearonShapes.Image)
                .background(WearonColors.Beige)
        ) {
            AsyncImage(
                model = clothes.imageUrl,
                contentDescription = clothes.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.8f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "삭제", tint = WearonColors.Ink, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(clothes.category.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WearonColors.Ink)
            clothes.color?.let { color ->
                Spacer(Modifier.width(8.dp))
                colorSwatch(color)?.let { swatch ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(1.dp, WearonColors.Line, CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(color, fontSize = 12.sp, color = WearonColors.SubText)
            }
        }
        clothes.name?.let {
            Text(
                it,
                fontSize = 13.sp,
                color = WearonColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun EmptyCloset(isFiltered: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("👕", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (isFiltered) "이 카테고리에 등록된 옷이 없어요" else "아직 등록된 옷이 없어요",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = WearonColors.Ink
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "사진을 찍거나 갤러리에서 고르면\nAI가 자동으로 분류해드려요",
            fontSize = 13.sp,
            color = WearonColors.SubText,
            lineHeight = 19.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// AI가 주는 한국어 대표 색상명 → 스와치 색 (목록에 없으면 스와치 없이 텍스트만 표시)
private fun colorSwatch(name: String): Color? = when (name) {
    "블랙" -> Color(0xFF1A1A1A)
    "화이트" -> Color(0xFFFFFFFF)
    "아이보리", "크림" -> Color(0xFFF6F0E3)
    "그레이" -> Color(0xFF9E9E9E)
    "차콜" -> Color(0xFF4A4A4A)
    "네이비" -> Color(0xFF1F2A44)
    "블루" -> Color(0xFF3B6FD8)
    "스카이블루", "하늘색" -> Color(0xFF8EC5EC)
    "베이지" -> Color(0xFFD9C7A7)
    "브라운" -> Color(0xFF7A5230)
    "카키" -> Color(0xFF7A7A4A)
    "그린" -> Color(0xFF3F8F5A)
    "레드" -> Color(0xFFD64545)
    "핑크" -> Color(0xFFF2A7B8)
    "옐로우" -> Color(0xFFF2D14B)
    "오렌지" -> Color(0xFFF08A3C)
    "퍼플" -> Color(0xFF8A5CC2)
    else -> null
}
