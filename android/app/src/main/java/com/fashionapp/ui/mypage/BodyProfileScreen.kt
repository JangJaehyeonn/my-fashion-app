package com.fashionapp.ui.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fashionapp.data.model.BodyType
import com.fashionapp.data.model.PreferredStyle
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BodyProfileViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var height by remember(profile) { mutableStateOf(profile?.height?.toString() ?: "") }
    var weight by remember(profile) { mutableStateOf(profile?.weight?.toString() ?: "") }
    var bodyType by remember(profile) {
        mutableStateOf(profile?.bodyType?.let { name -> BodyType.entries.find { it.name == name } })
    }
    var preferredStyle by remember(profile) {
        mutableStateOf(profile?.preferredStyle?.let { name -> PreferredStyle.entries.find { it.name == name } })
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = WearonColors.Ivory,
        topBar = {
            TopAppBar(
                title = { Text("체형·취향 설정", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
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
            SettingCard(title = "신체 정보") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("키", "cm", height, { height = it }, Modifier.weight(1f))
                    NumberField("몸무게", "kg", weight, { weight = it }, Modifier.weight(1f))
                }
            }

            SettingCard(title = "체형") {
                ChoiceChips(BodyType.entries, bodyType, { it.label }) { bodyType = it }
            }

            SettingCard(title = "선호 스타일") {
                ChoiceChips(PreferredStyle.entries, preferredStyle, { it.label }) { preferredStyle = it }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(WearonShapes.Card)
                    .background(if (isSaving) WearonColors.Line else WearonColors.Ink)
                    .clickable(enabled = !isSaving) {
                        viewModel.save(
                            height = height.toIntOrNull(),
                            weight = weight.toIntOrNull(),
                            bodyType = bodyType,
                            preferredStyle = preferredStyle,
                            onSaved = onSaved
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = WearonColors.Ink)
                } else {
                    Text("저장", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WearonShapes.Card)
            .background(WearonColors.White)
            .border(1.dp, WearonColors.Line, WearonShapes.Card)
            .padding(16.dp)
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun NumberField(
    label: String,
    unit: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(WearonShapes.Image)
            .background(WearonColors.Beige)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, fontSize = 11.sp, color = WearonColors.SubText)
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = value,
                onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(3)) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = WearonColors.Ink),
                cursorBrush = SolidColor(WearonColors.Ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text("—", fontSize = 20.sp, color = WearonColors.SubText)
                    inner()
                }
            )
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = 13.sp, color = WearonColors.SubText, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

// 같은 칩을 다시 누르면 선택 해제 (두 값 모두 선택 사항이므로)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T?) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(WearonShapes.Image)
                    .background(if (isSelected) WearonColors.Ink else WearonColors.White)
                    .border(1.dp, if (isSelected) WearonColors.Ink else WearonColors.Line, WearonShapes.Image)
                    .clickable { onSelect(if (isSelected) null else option) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label(option),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else WearonColors.Ink
                )
            }
        }
    }
}
