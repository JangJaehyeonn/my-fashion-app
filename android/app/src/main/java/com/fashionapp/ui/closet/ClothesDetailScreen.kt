package com.fashionapp.ui.closet

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

// Categories the user can pick (UNCLASSIFIED is set only by the server)
private val EDITABLE_CATEGORIES = listOf(
    ClothesCategory.TOP, ClothesCategory.BOTTOM, ClothesCategory.OUTER, ClothesCategory.SHOES, ClothesCategory.ETC
)

/** FR-02 clothes detail. [onBack] receives true when the clothes was saved or reclassified. */
@Composable
fun ClothesDetailScreen(
    onBack: (changed: Boolean) -> Unit,
    viewModel: ClothesDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val goBack = { onBack(state.changed) }

    BackHandler(onBack = goBack)

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
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = goBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기", tint = WearonColors.Ink)
                }
                Text("옷 상세", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
            }
            when {
                state.isLoading -> CenterBox { CircularProgressIndicator(color = WearonColors.BeigeDeep) }
                state.loadError != null -> CenterBox { LoadError(state.loadError!!, onRetry = viewModel::load) }
                else -> DetailContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun LoadError(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, fontSize = 14.sp, color = WearonColors.Ink)
        Spacer(Modifier.height(12.dp))
        OutlineButton("다시 불러오기", enabled = true, onClick = onRetry)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(state: ClothesDetailState, viewModel: ClothesDetailViewModel) {
    val clothes = state.clothes ?: return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(WearonShapes.Card)
                .background(WearonColors.Beige)
        ) {
            AsyncImage(
                model = clothes.imageUrl,
                contentDescription = clothes.name ?: "옷 사진",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            if (clothes.isUnclassified) {
                UnclassifiedBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
            }
        }
        if (clothes.isUnclassified) {
            Spacer(Modifier.height(10.dp))
            Text(
                "AI가 이 옷을 분류하지 못했어요. 종류를 직접 고르거나 다시 분류해 주세요.",
                fontSize = 13.sp,
                color = WearonColors.Accent,
                lineHeight = 19.sp
            )
        }

        SectionTitle("카테고리")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EDITABLE_CATEGORIES.forEach { category ->
                CategoryChip(
                    label = category.label,
                    selected = state.category == category,
                    enabled = !state.isBusy,
                    onClick = { viewModel.selectCategory(category) }
                )
            }
        }

        SectionTitle("색상")
        DetailTextField(state.color, viewModel::setColor, "예: 블랙", enabled = !state.isBusy)
        SectionTitle("이름")
        DetailTextField(state.name, viewModel::setName, "예: 검정 니트", enabled = !state.isBusy)

        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(
                label = if (state.isReclassifying) "분류하는 중…" else "다시 분류",
                enabled = state.canReclassify,
                onClick = viewModel::reclassify,
                modifier = Modifier.weight(1f)
            )
            PrimaryButton(
                label = if (state.isSaving) "저장하는 중…" else "저장",
                enabled = state.canSave,
                onClick = viewModel::save,
                modifier = Modifier.weight(1.3f)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun UnclassifiedBadge(modifier: Modifier = Modifier) {
    Text(
        "미분류 · 확인 필요",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .clip(WearonShapes.Image)
            .background(WearonColors.Accent)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(22.dp))
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(WearonShapes.Image)
            .background(if (selected) WearonColors.Ink else WearonColors.White)
            .border(1.dp, if (selected) WearonColors.Ink else WearonColors.Line, WearonShapes.Image)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else WearonColors.Ink
        )
    }
}

@Composable
private fun DetailTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = WearonColors.SubText) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        shape = WearonShapes.Card,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = WearonColors.Ink,
            unfocusedBorderColor = WearonColors.Line,
            cursorColor = WearonColors.Ink,
            focusedContainerColor = WearonColors.White,
            unfocusedContainerColor = WearonColors.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(WearonShapes.Card)
            .background(if (enabled) WearonColors.Ink else WearonColors.Line)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun OutlineButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(WearonShapes.Card)
            .background(WearonColors.White)
            .border(1.dp, if (enabled) WearonColors.Ink else WearonColors.Line, WearonShapes.Card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) WearonColors.Ink else WearonColors.SubText
        )
    }
}
