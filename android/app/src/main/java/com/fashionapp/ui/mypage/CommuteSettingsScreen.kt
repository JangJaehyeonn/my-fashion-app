package com.fashionapp.ui.mypage

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fashionapp.data.model.TravelMode
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes
import java.time.LocalTime

/** FR-30 마이 > 출퇴근 시각·이동 방식. Saving changes the moments of the home weather timeline (FR-28). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommuteSettingsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CommuteSettingsViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(context, { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) }, initial.hour, initial.minute, true)
            .show()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = WearonColors.Ivory,
        topBar = {
            TopAppBar(
                title = { Text(CommuteSettingsViewModel.MENU_LABEL, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                // 바깥 AppNavigation Scaffold가 이미 상태바 인셋을 적용하므로 중복 적용 방지
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WearonColors.Ivory)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WearonColors.Ink, strokeWidth = 2.dp)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "나갈 때와 돌아올 때 기온으로 오늘 코디를 골라요. 정하지 않으면 8시·13시·19시 기온을 봐요.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = WearonColors.SubText
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(WearonShapes.Card)
                    .background(WearonColors.White)
                    .border(1.dp, WearonColors.Line, WearonShapes.Card)
            ) {
                TimeRow("나가는 시각", CommuteSettingsViewModel.outLabel(form)) {
                    pickTime(form.outAt ?: LocalTime.of(8, 0), viewModel::setOutAt)
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(WearonColors.Line))
                TimeRow("돌아오는 시각", CommuteSettingsViewModel.backLabel(form)) {
                    pickTime(form.backAt ?: LocalTime.of(19, 0), viewModel::setBackAt)
                }
            }
            if (form.outAt != null || form.backAt != null) {
                Text(
                    "기본 시각으로 되돌리기",
                    fontSize = 13.sp,
                    color = WearonColors.SubText,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = viewModel::clearTimes).padding(vertical = 12.dp)
                )
            }

            Column {
                Text("이동 방식", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TravelMode.entries.forEach { mode ->
                        ModeChip(mode.label, selected = form.travelMode == mode, modifier = Modifier.weight(1f)) {
                            viewModel.selectTravelMode(mode)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(WearonShapes.Card)
                    .background(if (isSaving) WearonColors.Line else WearonColors.Ink)
                    .clickable(enabled = !isSaving) { viewModel.save(onSaved) },
                contentAlignment = Alignment.Center
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("저장", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClickLabel = "$label 바꾸기", onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = WearonColors.Ink, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WearonColors.Ink)
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(WearonShapes.Image)
            .background(if (selected) WearonColors.Ink else WearonColors.White)
            .border(1.dp, if (selected) WearonColors.Ink else WearonColors.Line, WearonShapes.Image)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else WearonColors.Ink
        )
    }
}
