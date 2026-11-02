package com.fashionapp.ui.login

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fashionapp.BuildConfig

@Composable
fun LoginScreen(
    message: String? = null,
    isLoggingIn: Boolean = false,
    onLoginStart: () -> Unit = {}
) {
    val context = LocalContext.current
    val oauth2BaseUrl = BuildConfig.OAUTH2_BASE_URL

    fun launchLogin(provider: String) {
        onLoginStart()
        CustomTabsIntent.Builder().build().launchUrl(
            context,
            Uri.parse("$oauth2BaseUrl/oauth2/authorization/$provider")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "👗", fontSize = 72.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Wearon",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = "AI가 추천하는 오늘의 코디",
            fontSize = 16.sp,
            color = Color(0xFF888888),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        // 코드 교환 중이거나 실패 안내가 있을 때만 표시 — 버튼 위치가 크게 흔들리지 않게 고정 높이
        Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            when {
                isLoggingIn -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF1A1A1A))
                message != null -> Text(text = message, fontSize = 13.sp, color = Color(0xFFD64545), textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Google 로그인
        Button(
            onClick = { launchLogin("google") },
            enabled = !isLoggingIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF1A1A1A)
            )
        ) {
            Text(text = "🔵  Google로 계속하기", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Kakao 로그인
        Button(
            onClick = { launchLogin("kakao") },
            enabled = !isLoggingIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFEE500),
                contentColor = Color(0xFF1A1A1A)
            )
        ) {
            Text(text = "💬  카카오로 계속하기", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
