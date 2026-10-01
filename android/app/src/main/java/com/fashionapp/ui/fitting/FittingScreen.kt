package com.fashionapp.ui.fitting

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fashionapp.ui.common.createCameraImageUri
import com.fashionapp.ui.theme.WearonColors
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.fashionapp.ui.theme.WearonShapes

@Composable
fun FittingScreen(viewModel: FittingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val productUrl by viewModel.productUrl.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val product by viewModel.product.collectAsState()
    val garment by viewModel.garment.collectAsState()
    val personImageUri by viewModel.personImageUri.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val result by viewModel.result.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) viewModel.setPersonImage(uri)
    }

    fun launchCamera() {
        val uri = createCameraImageUri(context)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else viewModel.setError("카메라 권한이 필요합니다.")
    }

    fun requestCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val personGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.setPersonImage(it) }
    }
    val garmentGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.setLocalGarment(it) }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = WearonColors.Ivory,
        // 바깥 AppNavigation Scaffold가 이미 시스템 바 인셋을 적용하므로 중복 적용 방지
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp)
        ) {
            Text("피팅", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
            Spacer(Modifier.height(4.dp))
            Text("쇼핑몰 옷을 내 사진에 입혀보세요", fontSize = 13.sp, color = WearonColors.SubText)

            // 1. 쇼핑몰 URL → 옷 이미지 추출 — 입력창 안에 불러오기 버튼을 넣어 한 줄로
            Spacer(Modifier.height(24.dp))
            SectionLabel("상품 주소")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(WearonShapes.Card)
                    .background(WearonColors.White)
                    .border(1.dp, WearonColors.Line, WearonShapes.Card)
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Link, contentDescription = null, tint = WearonColors.SubText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (productUrl.isEmpty()) {
                        Text("무신사·29CM 등 상품 링크 붙여넣기", fontSize = 14.sp, color = WearonColors.SubText)
                    }
                    BasicTextField(
                        value = productUrl,
                        onValueChange = viewModel::setProductUrl,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = WearonColors.Ink),
                        cursorBrush = SolidColor(WearonColors.Ink),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus(); viewModel.extract() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                val canExtract = !isExtracting && productUrl.isNotBlank()
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(WearonShapes.Image)
                        .background(if (canExtract) WearonColors.Ink else WearonColors.Line)
                        .clickable(enabled = canExtract) { focusManager.clearFocus(); viewModel.extract() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WearonColors.Ink)
                    } else {
                        Text("불러오기", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }

            product?.let { page ->
                Spacer(Modifier.height(14.dp))
                page.title?.let {
                    Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WearonColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                }
                if (page.imageUrls.size > 1) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        page.imageUrls.forEach { url ->
                            val selected = (garment as? GarmentSource.Remote)?.imageUrl == url
                            AsyncImage(
                                model = url,
                                contentDescription = "옷 이미지 후보",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 64.dp, height = 80.dp)
                                    .clip(WearonShapes.Image)
                                    .background(WearonColors.Beige)
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) WearonColors.Ink else WearonColors.Line,
                                        WearonShapes.Image
                                    )
                                    .clickable { viewModel.selectRemoteGarment(url) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("모델 없이 옷만 나온 사진을 고르면 결과가 좋아요", fontSize = 12.sp, color = WearonColors.SubText)
                }
            }

            // 2. 옷 사진 + 전신 사진을 나란히 — 무엇이 채워졌고 무엇이 비었는지 한눈에
            Spacer(Modifier.height(28.dp))
            SectionLabel("피팅 사진")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val garmentModel: Any? = when (val g = garment) {
                    is GarmentSource.Remote -> g.imageUrl
                    is GarmentSource.Local -> g.uri
                    null -> null
                }
                PhotoSlot(
                    label = "옷",
                    emptyHint = "상품 주소를 불러오거나\n갤러리에서 골라주세요",
                    model = garmentModel,
                    actions = listOf(
                        "갤러리" to {
                            garmentGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )
                PhotoSlot(
                    label = "전신",
                    emptyHint = "정면 전신 사진이\n가장 잘 나와요",
                    model = personImageUri,
                    actions = listOf(
                        "촬영" to ::requestCamera,
                        "갤러리" to {
                            personGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. 피팅
            Spacer(Modifier.height(24.dp))
            val canTryOn = !isProcessing && garment != null && personImageUri != null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(WearonShapes.Card)
                    .background(if (canTryOn) WearonColors.Ink else WearonColors.Line)
                    .clickable(enabled = canTryOn) { viewModel.tryOn(context) },
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = WearonColors.Ink)
                } else {
                    Text("가상 피팅 시작", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            if (isProcessing) {
                Spacer(Modifier.height(10.dp))
                Text("가상 피팅 생성 중입니다. 최대 1~2분 정도 걸릴 수 있어요.", fontSize = 12.sp, color = WearonColors.SubText)
            }

            result?.let { vtonResult ->
                Spacer(Modifier.height(28.dp))
                SectionLabel("결과")
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = vtonResult.resultImageUrl,
                    contentDescription = "가상 피팅 결과",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WearonShapes.Card)
                        .background(WearonColors.Beige)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
}

// 3:4 사진 칸 + 하단 텍스트 액션 — 비어 있으면 베이지 바탕에 안내 문구, 칸을 누르면 마지막 액션(갤러리) 실행
@Composable
private fun PhotoSlot(
    label: String,
    emptyHint: String,
    model: Any?,
    actions: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(WearonShapes.Card)
                .background(WearonColors.Beige)
                .clickable(onClick = actions.last().second),
            contentAlignment = Alignment.Center
        ) {
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = WearonColors.SubText, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        emptyHint,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = WearonColors.SubText,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(WearonShapes.Image)
                    .background(WearonColors.Ink)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            actions.forEach { (text, onClick) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(WearonShapes.Image)
                        .border(1.dp, WearonColors.Line, WearonShapes.Image)
                        .background(WearonColors.White)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = WearonColors.Ink)
                }
            }
        }
    }
}
