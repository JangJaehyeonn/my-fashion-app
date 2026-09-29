package com.fashionapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fashionapp.ui.theme.WearonColors

// 탭 개편 중 아직 구현되지 않은 탭(옷장/피팅)의 임시 화면
@Composable
fun ComingSoonScreen(title: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WearonColors.Ivory),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text("곧 만나요", fontSize = 14.sp, color = WearonColors.SubText)
    }
}
