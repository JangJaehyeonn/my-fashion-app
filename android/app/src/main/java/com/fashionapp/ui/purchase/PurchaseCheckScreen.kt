package com.fashionapp.ui.purchase

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fashionapp.data.model.PurchaseCheckResult
import com.fashionapp.data.repository.CompressedPhoto
import com.fashionapp.ui.common.createCameraImageUri
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

private val SubGray = Color(0xFF6B6458)
private val FaintGray = Color(0xFF8A8378)

/**
 * FR-17 purchase check flow: pick (camera / gallery / product URL) → confirm (optional price) → analyzing.
 * The result stage shows the key numbers until the full result screen (FR-18) replaces it.
 */
@Composable
fun PurchaseCheckScreen(
    onClose: () -> Unit,
    viewModel: PurchaseCheckViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val launchPicker = rememberPhotoPicker(state.source, viewModel)

    LaunchedEffect(state.closed) { if (state.closed) onClose() }

    LaunchedEffect(state.stage) {
        if (state.stage == PurchaseStage.PICKING && !viewModel.pickerAutoLaunched) {
            viewModel.markPickerLaunched()
            launchPicker()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    BackHandler(onBack = onClose)

    Scaffold(
        containerColor = WearonColors.Ivory,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TopBar(onClose)
            when (state.stage) {
                PurchaseStage.PICKING -> PickingStep(state, launchPicker)
                PurchaseStage.URL_INPUT -> ProductUrlStep(state, viewModel)
                PurchaseStage.CONFIRM -> ConfirmStep(state, viewModel::setPrice, viewModel::compare)
                PurchaseStage.ANALYZING -> AnalyzingStep(state.photo, state.analysisStep)
                PurchaseStage.RESULT -> state.result?.let { ResultSummary(state.photo, it, onClose) }
            }
        }
    }
}

/** Returns a function that opens the camera (with permission) or the single photo picker. */
@Composable
private fun rememberPhotoPicker(source: PurchaseSource, viewModel: PurchaseCheckViewModel): () -> Unit {
    val context = LocalContext.current
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) viewModel.onPhotoPicked { context.contentResolver.openInputStream(uri) }
        else viewModel.onPickCancelled()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createCameraImageUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            viewModel.onPickCancelled()
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPhotoPicked { context.contentResolver.openInputStream(uri) }
        else viewModel.onPickCancelled()
    }

    return {
        when (source) {
            PurchaseSource.CAMERA -> {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) {
                    val uri = createCameraImageUri(context)
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            PurchaseSource.GALLERY ->
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            PurchaseSource.URL -> Unit
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = WearonColors.Ink)
        }
        Text("살까 말까", fontSize = 18.sp, fontWeight = FontWeight.Black, color = WearonColors.Ink)
    }
}

@Composable
private fun PickingStep(state: PurchaseCheckState, launchPicker: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isPreparing) {
            CircularProgressIndicator(color = WearonColors.Ink)
            Spacer(Modifier.height(12.dp))
            Text("사진을 준비하고 있어요", fontSize = 14.sp, color = SubGray)
        } else {
            Text("옷만 나오게 찍으면 더 정확해요", fontSize = 14.sp, color = SubGray)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                label = if (state.source == PurchaseSource.CAMERA) "촬영하기" else "사진 고르기",
                enabled = true,
                onClick = launchPicker
            )
        }
    }
}

@Composable
private fun ProductUrlStep(state: PurchaseCheckState, viewModel: PurchaseCheckViewModel) {
    val focusManager = LocalFocusManager.current
    val url = state.productUrl
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("상품 주소", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
        OutlinedTextField(
            value = url.url,
            onValueChange = viewModel::setUrl,
            singleLine = true,
            placeholder = { Text("무신사·29CM 등 상품 링크", fontSize = 13.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus(); viewModel.fetchProduct() }),
            modifier = Modifier.fillMaxWidth()
        )
        PrimaryButton(
            label = if (url.isFetching) "불러오는 중" else "불러오기",
            enabled = !url.isFetching && url.url.isNotBlank(),
            onClick = { focusManager.clearFocus(); viewModel.fetchProduct() }
        )
        url.error?.let { Text(it, fontSize = 12.sp, color = Color(0xFFD64545)) }
        url.product?.let { page ->
            page.title?.let {
                Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WearonColors.Ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                page.imageUrls.forEach { candidate ->
                    val selected = candidate == url.selectedImageUrl
                    AsyncImage(
                        model = candidate,
                        contentDescription = "상품 이미지 후보",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 78.dp, height = 98.dp)
                            .clip(WearonShapes.Image)
                            .background(WearonColors.Beige)
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) WearonColors.Ink else WearonColors.Line,
                                WearonShapes.Image
                            )
                            .clickable { viewModel.selectProductImage(candidate) }
                    )
                }
            }
            Text("옷만 나온 사진을 고르면 더 정확해요", fontSize = 11.sp, color = FaintGray)
            PrimaryButton(
                label = if (state.isPreparing) "사진을 준비하고 있어요" else "이 사진으로 비교하기",
                enabled = url.selectedImageUrl != null && !state.isPreparing,
                onClick = viewModel::useSelectedProductImage
            )
        }
    }
}

@Composable
private fun ConfirmStep(state: PurchaseCheckState, onPriceChange: (String) -> Unit, onCompare: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PhotoCard(state.photo, Modifier.fillMaxWidth().height(320.dp))
        OutlinedTextField(
            value = state.priceText,
            onValueChange = onPriceChange,
            singleLine = true,
            label = { Text("가격 (선택)") },
            suffix = { Text("원") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        PrimaryButton(label = "비교하기", enabled = state.canCompare, onClick = onCompare)
    }
}

@Composable
private fun AnalyzingStep(photo: CompressedPhoto?, activeStep: Int) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.clip(WearonShapes.Card).background(WearonColors.Beige).padding(16.dp),
            contentAlignment = Alignment.Center
        ) { PhotoCard(photo, Modifier.size(width = 160.dp, height = 200.dp)) }
        Spacer(Modifier.height(24.dp))
        Text("옷장이랑 비교하는 중", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text("보통 5초 안에 끝나요", fontSize = 13.sp, color = SubGray)
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            PurchaseCheckViewModel.ANALYSIS_STEPS.forEachIndexed { index, label ->
                AnalysisStepRow(label, done = index < activeStep, active = index == activeStep)
            }
        }
    }
}

@Composable
private fun AnalysisStepRow(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val circle = Modifier.size(24.dp).clip(CircleShape)
        when {
            done -> Box(circle.background(WearonColors.Ink), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = "완료", tint = WearonColors.White, modifier = Modifier.size(16.dp))
            }
            active -> Box(circle.border(2.dp, WearonColors.Ink, CircleShape))
            else -> Box(circle.border(2.dp, WearonColors.Line, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (done || active) WearonColors.Ink else FaintGray
        )
    }
}

/** Key numbers of the response. FR-18 replaces this with the full result screen. */
@Composable
private fun ResultSummary(photo: CompressedPhoto?, result: PurchaseCheckResult, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhotoCard(photo, Modifier.size(width = 96.dp, height = 120.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    result.item?.name ?: result.item?.category?.label ?: "옷",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink
                )
                result.verdict?.let { Text(it.label, fontSize = 13.sp, color = SubGray) }
            }
        }
        Column(
            Modifier.fillMaxWidth().border(1.dp, WearonColors.Ink, WearonShapes.Card).padding(16.dp)
        ) {
            Text("+${result.plus} 코디", fontSize = 32.sp, fontWeight = FontWeight.Black, color = WearonColors.Ink)
            Text(
                "지금 ${result.comboCountBefore}개 → ${result.comboCountAfter}개",
                fontSize = 14.sp, color = SubGray
            )
        }
        result.headline?.let { Text(it, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink) }
        result.reasons.orEmpty().forEach { Text("· $it", fontSize = 14.sp, color = WearonColors.Ink) }
        PrimaryButton(label = "쇼핑으로 돌아가기", enabled = true, onClick = onClose)
    }
}

@Composable
private fun PhotoCard(photo: CompressedPhoto?, modifier: Modifier) {
    Box(modifier.clip(WearonShapes.Card).background(WearonColors.Beige), contentAlignment = Alignment.Center) {
        if (photo != null) {
            AsyncImage(
                model = photo.jpeg,
                contentDescription = "고른 옷 사진",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("사진 없음", fontSize = 12.sp, color = FaintGray, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = WearonShapes.Card,
        colors = ButtonDefaults.buttonColors(containerColor = WearonColors.Ink, contentColor = WearonColors.White),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}
