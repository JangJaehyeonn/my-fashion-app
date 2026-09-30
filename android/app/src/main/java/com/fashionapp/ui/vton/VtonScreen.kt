package com.fashionapp.ui.vton

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fashionapp.ui.common.createCameraImageUri

private enum class CameraTarget { PERSON, GARMENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VtonScreen(
    viewModel: VtonViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val personImageUri by viewModel.personImageUri.collectAsState()
    val garmentImageUri by viewModel.garmentImageUri.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val result by viewModel.result.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraTarget by remember { mutableStateOf<CameraTarget?>(null) }
    var permissionTarget by remember { mutableStateOf<CameraTarget?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingCameraUri?.let { uri ->
                when (pendingCameraTarget) {
                    CameraTarget.PERSON -> viewModel.setPersonImage(uri)
                    CameraTarget.GARMENT -> viewModel.setGarmentImage(uri)
                    null -> {}
                }
            }
        }
        pendingCameraTarget = null
    }

    fun launchCamera(target: CameraTarget) {
        val uri = createCameraImageUri(context)
        pendingCameraUri = uri
        pendingCameraTarget = target
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = permissionTarget
        permissionTarget = null
        if (granted && target != null) {
            launchCamera(target)
        } else if (!granted) {
            viewModel.setError("카메라 권한이 필요합니다.")
        }
    }

    fun requestCamera(target: CameraTarget) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCamera(target)
        } else {
            permissionTarget = target
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val personGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.setPersonImage(it) }
    }

    val garmentGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.setGarmentImage(it) }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("가상 피팅", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (viewModel.garmentDesc.isNotBlank()) {
                Text("선택한 아이템: ${viewModel.garmentDesc}", fontSize = 14.sp, color = Color.Gray)
            }

            ImagePickerSlot(
                title = "전신 사진",
                imageUri = personImageUri,
                onCameraClick = { requestCamera(CameraTarget.PERSON) },
                onGalleryClick = { personGalleryLauncher.launch("image/*") }
            )

            ImagePickerSlot(
                title = "옷 사진",
                imageUri = garmentImageUri,
                onCameraClick = { requestCamera(CameraTarget.GARMENT) },
                onGalleryClick = { garmentGalleryLauncher.launch("image/*") }
            )

            Button(
                onClick = { viewModel.tryOn(context) },
                enabled = !isProcessing && personImageUri != null && garmentImageUri != null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text("✨  가상 피팅 시작")
                }
            }

            if (isProcessing) {
                Text(
                    "가상 피팅 생성 중입니다. 최대 1~2분 정도 걸릴 수 있어요.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            result?.let { vtonResult ->
                Text("결과", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                AsyncImage(
                    model = vtonResult.resultImageUrl,
                    contentDescription = "가상 피팅 결과",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                )
            }
        }
    }
}

// 피팅 탭(ui/fitting)의 전신 사진 칸에서도 재사용
@Composable
fun ImagePickerSlot(
    title: String,
    imageUri: Uri?,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Column {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF0F0F0)),
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("사진 없음", color = Color.Gray)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCameraClick, modifier = Modifier.weight(1f)) {
                Text("📷  촬영")
            }
            OutlinedButton(onClick = onGalleryClick, modifier = Modifier.weight(1f)) {
                Text("🖼️  갤러리")
            }
        }
    }
}
