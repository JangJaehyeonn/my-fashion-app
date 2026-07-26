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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fashionapp.data.model.Situation
import com.fashionapp.data.model.SituationOutfitSuggestion

private val conditionEmoji = mapOf(
    "맑음" to "☀️", "구름많음" to "⛅", "흐림" to "☁️",
    "비" to "🌧️", "눈" to "❄️", "소나기" to "⛈️"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendScreen(viewModel: RecommendViewModel = hiltViewModel()) {
    val weather by viewModel.weather.collectAsState()
    val isWeatherLoading by viewModel.isWeatherLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedSituation by viewModel.selectedSituation.collectAsState()
    val situationRecommendations by viewModel.situationRecommendations.collectAsState()
    val isSituationLoading by viewModel.isSituationLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var expanded by remember { mutableStateOf(false) }

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
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("날씨 정보를 불러올 수 없습니다.", color = Color.Gray)
                            OutlinedButton(onClick = { viewModel.loadWeather() }) {
                                Text("다시 시도")
                            }
                        }
                    }
                }
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSituation.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("상황") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Situation.entries.forEach { situation ->
                            DropdownMenuItem(
                                text = { Text(situation.label) },
                                onClick = {
                                    viewModel.selectSituation(situation)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { viewModel.recommendBySituation() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isSituationLoading && weather != null,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSituationLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text(if (situationRecommendations.isEmpty()) "✨  코디 추천받기" else "🔄  다시 추천받기")
                    }
                }
            }

            items(situationRecommendations) { outfit ->
                SituationRecommendCard(outfit)
            }
        }
    }
}

@Composable
private fun SituationRecommendCard(outfit: SituationOutfitSuggestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SuggestionChip(onClick = {}, label = { Text(outfit.styleTag) })
            Spacer(Modifier.height(8.dp))
            Text(outfit.description, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(outfit.reason, fontSize = 14.sp, color = Color(0xFF444444), lineHeight = 22.sp)

            if (outfit.shoppingSuggestions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    outfit.shoppingSuggestions.forEach { suggestion ->
                        Text(
                            "🛍️ ${suggestion.site}에서 '${suggestion.searchKeyword}' 검색해보세요",
                            fontSize = 13.sp,
                            color = Color(0xFF1A73E8)
                        )
                    }
                }
            }
        }
    }
}
