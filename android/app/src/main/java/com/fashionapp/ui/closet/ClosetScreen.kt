package com.fashionapp.ui.closet

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.fashionapp.data.model.Clothes
import com.fashionapp.ui.common.createCameraImageUri
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

// 갤러리에서 한 번에 고를 수 있는 최대 장수
private const val MAX_PICK = 10

@Composable
fun ClosetScreen(
    onOpenClothes: (Clothes) -> Unit = {},
    viewModel: ClosetViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clothes by viewModel.filteredClothes.collectAsState()
    val counts by viewModel.countByCategory.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val boughtIds by viewModel.boughtIds.collectAsState()
    val laundryCount by viewModel.laundryCount.collectAsState()
    val laundryOnly by viewModel.laundryOnly.collectAsState()
    val isFinishingLaundry by viewModel.isFinishingLaundry.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val registerProgress by viewModel.registerProgress.collectAsState()
    val isRegistering = registerProgress != null
    val message by viewModel.message.collectAsState()
    val urlState by viewModel.urlState.collectAsState()
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (laundryOnly && laundryCount > 0) {
                FinishLaundryBar(count = laundryCount, isSaving = isFinishingLaundry, onFinish = viewModel::finishLaundry)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("옷장 ${counts[null] ?: 0}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = WearonColors.Ink)
                Spacer(Modifier.weight(1f))
                Box {
                    Row(
                        modifier = Modifier
                            .clip(WearonShapes.Image)
                            .background(if (isRegistering) WearonColors.Line else WearonColors.Ink)
                            .clickable(enabled = !isRegistering) { showAddMenu = true }
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("＋ 옷 등록", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("사진 촬영") },
                            onClick = { showAddMenu = false; requestCamera() }
                        )
                        DropdownMenuItem(
                            text = { Text("갤러리에서 선택 (최대 ${MAX_PICK}장)") },
                            onClick = {
                                showAddMenu = false
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("상품 주소로 등록") },
                            onClick = {
                                showAddMenu = false
                                viewModel.openUrlDialog()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SummaryRow(summary = summary, onUnwornClick = viewModel::showUnworn)
            Spacer(Modifier.height(12.dp))
            CategoryChips(
                selected = selectedCategory,
                counts = counts,
                onSelect = viewModel::selectCategory,
                laundryCount = laundryCount,
                laundryOnly = laundryOnly,
                onLaundry = viewModel::showLaundry
            )
            SortRow(count = clothes.size, selected = sort, onSelect = viewModel::selectSort)

            registerProgress?.let { progress ->
                val stageLabel = when (progress.stage) {
                    RegisterStage.DOWNLOADING -> "상품 이미지를 가져오는 중…"
                    RegisterStage.COMPRESSING -> "사진을 최적화하는 중…"
                    RegisterStage.UPLOADING -> "업로드 중 ${progress.uploadPercent}%"
                    RegisterStage.ANALYZING -> "AI가 옷을 분류하는 중…"
                }
                // 여러 장일 때만 "3/5 ·" 앞붙임
                val label = if (progress.total > 1) "${progress.current}/${progress.total} · $stageLabel" else stageLabel
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
                        Text(label, fontSize = 13.sp, color = WearonColors.Ink)
                    }
                    Spacer(Modifier.height(10.dp))
                    val barModifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    if (progress.stage == RegisterStage.ANALYZING) {
                        // 서버가 AI 분류하는 동안은 끝을 알 수 없으므로 불확정 바
                        LinearProgressIndicator(modifier = barModifier, color = WearonColors.Ink, trackColor = WearonColors.White)
                    } else {
                        // 앞서 끝낸 장수 + 지금 장의 압축/업로드 진행분(전체의 절반까지, 나머지 절반은 AI 분류)
                        val within = if (progress.stage == RegisterStage.UPLOADING) 0.05f + 0.45f * progress.uploadPercent / 100f else 0.02f
                        LinearProgressIndicator(
                            progress = { ((progress.current - 1) + within) / progress.total },
                            modifier = barModifier,
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(clothes, key = { it.id }) { item ->
                        ClothesCard(
                            item,
                            badge = ClosetList.badge(item, boughtIds),
                            onClick = { onOpenClothes(item) },
                            onDelete = { deleteTarget = item }
                        )
                    }
                }
            }
        }
    }

    urlState?.let { state ->
        UrlRegisterDialog(
            state = state,
            onUrlChange = viewModel::setUrl,
            onFetch = viewModel::fetchProduct,
            onSelectImage = viewModel::selectUrlImage,
            onRegister = viewModel::registerFromUrl,
            onDismiss = viewModel::closeUrlDialog
        )
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

internal fun Modifier.dashedBorder(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = 1.5.dp.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(4.dp.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
    )
}

@Composable
private fun EmptyCloset(isFiltered: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
internal fun colorSwatch(name: String): Color? = when (name) {
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
