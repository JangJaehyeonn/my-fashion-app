package com.fashionapp.ui.diagnosis

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fashionapp.data.model.SimilarStyleSuggestion
import com.fashionapp.data.model.StyleDiagnosis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosisScreen(viewModel: DiagnosisViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val isDiagnosing by viewModel.isDiagnosing.collectAsState()
    val result by viewModel.result.collectAsState()
    val history by viewModel.history.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.diagnose(context, it) }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("내 옷 진단", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Button(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isDiagnosing,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isDiagnosing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("📸  코디 사진으로 진단받기")
                    }
                }
            }

            result?.let { diagnosis ->
                item { DiagnosisResultCard(diagnosis) }
            }

            item {
                Text("지난 진단 기록", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            if (history.isEmpty()) {
                item {
                    Text("아직 진단 기록이 없습니다.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                items(history) { diagnosis ->
                    DiagnosisHistoryRow(diagnosis)
                }
            }
        }
    }
}

@Composable
private fun DiagnosisResultCard(diagnosis: StyleDiagnosis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(
                    model = diagnosis.imageUrl,
                    contentDescription = "진단한 코디 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                )
                ScoreBadge(diagnosis.score)
            }
            Spacer(Modifier.height(12.dp))
            Text(diagnosis.feedback, fontSize = 14.sp, color = Color(0xFF444444), lineHeight = 22.sp)

            if (diagnosis.similarStyles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("비슷한 스타일 추천", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                diagnosis.similarStyles.forEach { style ->
                    SimilarStyleRow(style)
                }
            }
        }
    }
}

@Composable
private fun SimilarStyleRow(style: SimilarStyleSuggestion) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        SuggestionChip(onClick = {}, label = { Text(style.styleTag) })
        Spacer(Modifier.height(4.dp))
        Text(style.description, fontSize = 13.sp, color = Color(0xFF666666))
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color(0x221A73E8), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A73E8))
            Text("점", fontSize = 11.sp, color = Color(0xFF1A73E8))
        }
    }
}

@Composable
private fun DiagnosisHistoryRow(diagnosis: StyleDiagnosis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = diagnosis.imageUrl,
                contentDescription = "진단 기록 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
            )
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text("${diagnosis.score}점", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    diagnosis.feedback,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
        }
    }
}
