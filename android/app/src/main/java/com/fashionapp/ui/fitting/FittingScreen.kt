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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.fashionapp.ui.vton.ImagePickerSlot

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
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column {
                Text("피팅", fontSize = 26.sp, fontWeight = FontWeight.Black, color = WearonColors.Ink)
                Spacer(Modifier.height(4.dp))
                Text("쇼핑몰 옷을 내 사진에 입혀보세요", fontSize = 14.sp, color = WearonColors.SubText)
            }

            // 1. 쇼핑몰 URL → 옷 이미지 추출
            StepTitle("1", "입어볼 옷")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = productUrl,
                    onValueChange = viewModel::setProductUrl,
                    placeholder = { Text("상품 페이지 주소 붙여넣기", fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus(); viewModel.extract() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WearonColors.Ink,
                        unfocusedBorderColor = WearonColors.Line,
                        focusedContainerColor = WearonColors.White,
                        unfocusedContainerColor = WearonColors.White
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                PillButton(
                    text = "불러오기",
                    enabled = !isExtracting && productUrl.isNotBlank(),
                    loading = isExtracting,
                    onClick = { focusManager.clearFocus(); viewModel.extract() }
                )
            }

            product?.let { page ->
                page.title?.let {
                    Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (page.imageUrls.size > 1) {
                    Text("피팅할 사진을 골라주세요 — 모델 없이 옷만 나온 사진이 결과가 좋아요", fontSize = 12.sp, color = WearonColors.SubText)
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    page.imageUrls.forEach { url ->
                        val selected = (garment as? GarmentSource.Remote)?.imageUrl == url
                        AsyncImage(
                            model = url,
                            contentDescription = "옷 이미지 후보",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 110.dp, height = 146.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(WearonColors.Beige)
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) WearonColors.Ink else WearonColors.Line,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.selectRemoteGarment(url) }
                        )
                    }
                }
            }

            (garment as? GarmentSource.Local)?.let { local ->
                AsyncImage(
                    model = local.uri,
                    contentDescription = "내 옷 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 110.dp, height = 146.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, WearonColors.Ink, RoundedCornerShape(12.dp))
                )
            }

            Text(
                "주소가 안 되면 갤러리에서 옷 사진 고르기",
                fontSize = 13.sp,
                color = WearonColors.Ink,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    garmentGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )

            // 2. 전신 사진
            StepTitle("2", "내 전신 사진")
            ImagePickerSlot(
                title = "정면 전신 사진이 가장 잘 나와요",
                imageUri = personImageUri,
                onCameraClick = ::requestCamera,
                onGalleryClick = {
                    personGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )

            // 3. 피팅
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (!isProcessing && garment != null && personImageUri != null) WearonColors.Ink else WearonColors.Line)
                    .clickable(enabled = !isProcessing && garment != null && personImageUri != null) { viewModel.tryOn(context) },
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = WearonColors.Ink)
                } else {
                    Text("가상 피팅 시작", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            if (isProcessing) {
                Text("가상 피팅 생성 중입니다. 최대 1~2분 정도 걸릴 수 있어요.", fontSize = 13.sp, color = WearonColors.SubText)
            }

            result?.let { vtonResult ->
                Text("결과", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
                AsyncImage(
                    model = vtonResult.resultImageUrl,
                    contentDescription = "가상 피팅 결과",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(WearonColors.Beige)
                )
            }
        }
    }
}

@Composable
private fun StepTitle(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape).background(WearonColors.Ink),
            contentAlignment = Alignment.Center
        ) {
            Text(number, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
    }
}

@Composable
private fun PillButton(text: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) WearonColors.Ink else WearonColors.Line)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WearonColors.Ink)
        } else {
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
