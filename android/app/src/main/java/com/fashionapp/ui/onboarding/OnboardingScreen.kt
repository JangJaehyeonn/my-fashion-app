package com.fashionapp.ui.onboarding

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

// Same limit as the closet tab gallery picker
private const val MAX_PICK = 10

/** FR-22: first-run onboarding shown while the closet has fewer than 5 pieces. */
@Composable
fun OnboardingScreen(
    onLater: () -> Unit,
    onGoShopping: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PICK)
    ) { uris: List<Uri> ->
        viewModel.registerAll(uris.map { uri ->
            PickedPhoto(open = { context.contentResolver.openInputStream(uri) }, mimeType = context.contentResolver.getType(uri))
        })
    }

    fun later() {
        viewModel.later()
        onLater()
    }

    BackHandler { later() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
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
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Text("WEARON", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = WearonColors.Ink)
            Spacer(Modifier.height(40.dp))
            Text(state.title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = WearonColors.Ink, lineHeight = 30.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.isComplete) "옷장과 비교해서 +N 코디를 알려드려요" else "갤러리에서 한 번에 여러 벌을 고를 수 있어요",
                fontSize = 14.sp,
                color = WearonColors.SubText
            )
            Spacer(Modifier.height(28.dp))
            ProgressCard(state)
            Spacer(Modifier.weight(1f))
            if (state.isComplete) {
                PrimaryButton("쇼핑 탭으로 가기", enabled = true, onClick = onGoShopping)
            } else {
                PrimaryButton(
                    "갤러리에서 여러 벌 등록",
                    enabled = state.canPick,
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { later() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("나중에 하기", fontSize = 14.sp, color = WearonColors.SubText)
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(state: OnboardingState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WearonShapes.Card)
            .background(WearonColors.Beige)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                state.progressText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = WearonColors.Ink,
                modifier = Modifier.semantics { contentDescription = "${OnboardingState.GOAL}벌 중 ${state.progress}벌 등록" }
            )
            Spacer(Modifier.width(8.dp))
            Text("벌 등록", fontSize = 14.sp, color = WearonColors.SubText, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(OnboardingState.GOAL) { index ->
                val filled = index < state.progress
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (filled) WearonColors.Ink else WearonColors.White)
                        .border(1.dp, if (filled) WearonColors.Ink else WearonColors.Line, CircleShape)
                )
            }
        }
        state.uploadText?.let { text ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WearonColors.Ink)
                Spacer(Modifier.width(10.dp))
                Text(text, fontSize = 13.sp, color = WearonColors.Ink)
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = WearonShapes.Card,
        colors = ButtonDefaults.buttonColors(
            containerColor = WearonColors.Ink,
            contentColor = WearonColors.White,
            disabledContainerColor = WearonColors.Line,
            disabledContentColor = WearonColors.White
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
