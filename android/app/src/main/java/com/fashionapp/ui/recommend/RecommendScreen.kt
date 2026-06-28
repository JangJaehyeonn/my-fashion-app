package com.fashionapp.ui.recommend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fashionapp.data.model.RecommendedOutfit

private val conditionEmoji = mapOf(
    "맑음" to "☀️", "구름많음" to "⛅", "흐림" to "☁️",
    "비" to "🌧️", "눈" to "❄️", "소나기" to "⛈️"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendScreen(viewModel: RecommendViewModel = hiltViewModel()) {
    val weather by viewModel.weather.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val allClothes by viewModel.allClothes.collectAsState()
    val isWeatherLoading by viewModel.isWeatherLoading.collectAsState()
    val isRecommendLoading by viewModel.isRecommendLoading.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("오늘의 코디 추천", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 날씨 카드
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0x22FF6B6B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isWeatherLoading) {
                        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (weather != null) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(text = conditionEmoji[weather!!.condition] ?: "🌤️", fontSize = 48.sp)
                            Column {
                                Text("${weather!!.temperature}°C", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${weather!!.condition} · 습도 ${weather!!.humidity}% · 바람 ${weather!!.windSpeed}m/s",
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            Text("날씨 정보를 불러올 수 없습니다.", color = Color.Gray)
                        }
                    }
                }
            }

            // 추천 버튼
            item {
                Button(
                    onClick = { viewModel.recommend() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isRecommendLoading && !isWeatherLoading && weather != null,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isRecommendLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text(if (recommendations.isEmpty()) "✨  코디 추천받기" else "🔄  다시 추천받기")
                    }
                }
            }

            // 추천 결과
            itemsIndexed(recommendations) { index, outfit ->
                RecommendCard(
                    outfit = outfit,
                    clothes = allClothes.filter { it.id in outfit.clothesIds },
                    isSaved = index in savedIds,
                    onSave = { viewModel.saveOutfit(outfit, index) }
                )
            }
        }
    }
}

@Composable
fun RecommendCard(
    outfit: RecommendedOutfit,
    clothes: List<com.fashionapp.data.model.Clothes>,
    isSaved: Boolean,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 옷 썸네일
            if (clothes.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(clothes) { c ->
                        AsyncImage(
                            model = c.imageUrl,
                            contentDescription = c.category,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SuggestionChip(onClick = {}, label = { Text(outfit.styleTag) })

            Spacer(Modifier.height(8.dp))

            Text(outfit.reason, fontSize = 14.sp, color = Color(0xFF444444), lineHeight = 22.sp)

            Spacer(Modifier.height(14.dp))

            if (isSaved) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                ) {
                    Text("저장됨 ✓")
                }
            } else {
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Text("코디 저장하기")
                }
            }
        }
    }
}
