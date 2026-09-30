package com.fashionapp.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import com.fashionapp.data.model.Situation
import com.fashionapp.data.model.Weather
import com.fashionapp.ui.theme.WearonColors

// 홈 칩 노출 순서 — 가장 자주 쓰는 상황을 앞에 둔다
private val HOME_SITUATIONS = listOf(
    Situation.DAILY, Situation.WORK, Situation.DATE,
    Situation.EXERCISE, Situation.TRAVEL, Situation.INTERVIEW
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onGoToCloset: () -> Unit = {}
) {
    val weather by viewModel.weather.collectAsState()
    val isWeatherLoading by viewModel.isWeatherLoading.collectAsState()
    val selectedSituation by viewModel.selectedSituation.collectAsState()
    val looks by viewModel.looks.collectAsState()
    val isRecommending by viewModel.isRecommending.collectAsState()
    val isClosetEmpty by viewModel.isClosetEmpty.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
                .padding(vertical = 20.dp)
                .animateContentSize()
        ) {
            Text(
                "Wearon",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                color = WearonColors.Ink,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(20.dp))
            WeatherCard(
                weather = weather,
                isLoading = isWeatherLoading,
                onRetry = viewModel::loadWeather,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(28.dp))
            Text(
                "오늘은 어떤 날인가요?",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WearonColors.Ink,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
            SituationChips(
                selected = selectedSituation,
                onSelect = viewModel::selectSituation
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::recommend,
                enabled = weather != null && !isRecommending,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WearonColors.Ink,
                    disabledContainerColor = WearonColors.Line
                )
            ) {
                if (isRecommending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("오늘의 코디 추천", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (isClosetEmpty) {
                Spacer(Modifier.height(24.dp))
                ClosetEmptyCard(onGoToCloset = onGoToCloset, modifier = Modifier.padding(horizontal = 20.dp))
            } else if (looks.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                LookPager(looks = looks, situation = selectedSituation)
            }
        }
    }
}

@Composable
private fun WeatherCard(
    weather: Weather?,
    isLoading: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WearonColors.White)
            .border(1.dp, WearonColors.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = WearonColors.BeigeDeep)
                Spacer(Modifier.width(12.dp))
                Text("날씨를 불러오는 중…", color = WearonColors.SubText, fontSize = 14.sp)
            }
            weather == null -> {
                Text("날씨 정보를 불러올 수 없어요", color = WearonColors.SubText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("다시 시도", color = WearonColors.Ink) }
            }
            else -> {
                Text(weatherEmoji(weather.condition), fontSize = 36.sp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("오늘 서울", fontSize = 12.sp, color = WearonColors.SubText)
                    Text(weather.condition, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
                    Text(
                        "습도 ${weather.humidity}% · 바람 ${"%.1f".format(weather.windSpeed)}m/s",
                        fontSize = 12.sp,
                        color = WearonColors.SubText
                    )
                }
                Text(
                    "${weather.temperature.toInt()}°",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    color = WearonColors.Ink
                )
            }
        }
    }
}

@Composable
private fun SituationChips(selected: Situation, onSelect: (Situation) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HOME_SITUATIONS.forEach { situation ->
            val isSelected = situation == selected
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) WearonColors.Ink else WearonColors.White)
                    .border(1.dp, if (isSelected) WearonColors.Ink else WearonColors.Line, CircleShape)
                    .clickable { onSelect(situation) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    situation.label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else WearonColors.Ink
                )
            }
        }
    }
}

@Composable
private fun LookPager(looks: List<LookCard>, situation: Situation) {
    val pagerState = rememberPagerState(pageCount = { looks.size })
    // 추천이 새로 오면 첫 장부터 다시 보여줌
    LaunchedEffect(looks) { pagerState.scrollToPage(0) }

    Column {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text("오늘의 추천 코디", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
            Spacer(Modifier.width(6.dp))
            Text("${situation.label} · ${looks.size}", fontSize = 13.sp, color = WearonColors.SubText)
        }
        Spacer(Modifier.height(14.dp))

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top
        ) { page ->
            LookCardView(index = page, look = looks[page])
        }

        Spacer(Modifier.height(14.dp))
        PagerDots(count = looks.size, current = pagerState.currentPage)
    }
}

@Composable
private fun LookCardView(index: Int, look: LookCard) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(WearonColors.White)
            .border(1.dp, WearonColors.Line, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WearonColors.Beige)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "LOOK %02d".format(index + 1),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    color = WearonColors.Ink
                )
                Spacer(Modifier.weight(1f))
                if (look.styleTag.isNotBlank()) {
                    Text(
                        look.styleTag,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WearonColors.Ink,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(WearonColors.White)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            // 조합을 구성하는 내 옷 사진을 아우터 → 상의 → 하의 → 신발 순으로 나란히 배치
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                look.items.forEach { item ->
                    Column(modifier = Modifier.weight(1f)) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(WearonColors.White)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(item.category.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
                        Text(
                            item.name ?: item.color ?: "",
                            fontSize = 11.sp,
                            color = WearonColors.SubText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Text(
            look.reason,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = WearonColors.SubText,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ClosetEmptyCard(onGoToCloset: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(WearonColors.Beige)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("👕", fontSize = 36.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "옷장에 옷을 등록하면\n내 옷으로 코디를 추천해드려요",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp,
            color = WearonColors.Ink,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onGoToCloset,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, WearonColors.Ink),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = WearonColors.White, contentColor = WearonColors.Ink)
        ) {
            Text("옷장으로 가기", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PagerDots(count: Int, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { i ->
            val active = i == current
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(if (active) 18.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (active) WearonColors.Ink else WearonColors.BeigeDeep.copy(alpha = 0.5f))
            )
        }
    }
}

private fun weatherEmoji(condition: String) = when (condition) {
    "맑음" -> "☀️"
    "구름많음" -> "☁️"
    "비" -> "🌧️"
    "천둥번개" -> "⛈️"
    "눈" -> "❄️"
    "안개", "황사" -> "🌫️"
    else -> "🌤️"
}
