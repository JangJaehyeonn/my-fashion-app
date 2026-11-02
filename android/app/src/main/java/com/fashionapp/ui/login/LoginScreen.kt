package com.fashionapp.ui.login

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fashionapp.BuildConfig
import com.fashionapp.ui.common.AuthTextField
import com.fashionapp.ui.common.ErrorRed
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

/**
 * 로그인: 위쪽은 아이디·비밀번호, 아래쪽은 기존 Google·Kakao 버튼.
 * [message]·[isLoggingIn]은 OAuth 딥링크 처리 결과(MainViewModel), 아이디 로그인 상태는 [LoginViewModel].
 */
@Composable
fun LoginScreen(
    message: String? = null,
    isLoggingIn: Boolean = false,
    onLoginStart: () -> Unit = {},
    onLoggedIn: () -> Unit = {},
    onSignUp: (() -> Unit)? = null,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val oauth2BaseUrl = BuildConfig.OAUTH2_BASE_URL
    val loginId by viewModel.loginId.collectAsState()
    val password by viewModel.password.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val busy = isLoading || isLoggingIn

    // client=app: 로그인이 실패하면 서버가 웹이 아니라 앱 딥링크(?error=...)로 돌려보내도록 표시
    fun launchLogin(provider: String) {
        onLoginStart()
        CustomTabsIntent.Builder().build().launchUrl(
            context,
            Uri.parse("$oauth2BaseUrl/oauth2/authorization/$provider?client=app")
        )
    }

    fun submit() {
        focusManager.clearFocus()
        onLoginStart()
        viewModel.login(onLoggedIn)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "👗", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Wearon", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            text = "AI가 추천하는 오늘의 코디",
            fontSize = 16.sp,
            color = Color(0xFF888888),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        AuthTextField(
            value = loginId,
            onValueChange = viewModel::onLoginIdChange,
            label = "아이디",
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        AuthTextField(
            value = password,
            onValueChange = viewModel::onPasswordChange,
            label = "비밀번호",
            isPassword = true,
            enabled = !busy,
            imeAction = ImeAction.Done,
            onImeAction = ::submit,
            modifier = Modifier.fillMaxWidth()
        )

        // 아이디 로그인 오류가 우선, 없으면 OAuth 결과 안내 — 버튼 위치가 흔들리지 않게 고정 높이
        Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WearonColors.Ink)
                error != null -> Text(text = error!!, fontSize = 13.sp, color = ErrorRed, textAlign = TextAlign.Center)
                message != null -> Text(text = message, fontSize = 13.sp, color = ErrorRed, textAlign = TextAlign.Center)
            }
        }

        Button(
            onClick = ::submit,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = WearonShapes.Card,
            colors = ButtonDefaults.buttonColors(containerColor = WearonColors.Ink, disabledContainerColor = WearonColors.Line)
        ) {
            Text("로그인", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        if (onSignUp != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "아이디로 회원가입",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = WearonColors.Ink,
                modifier = Modifier.clickable(enabled = !busy, onClick = onSignUp).padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f).height(1.dp).background(WearonColors.Line))
            Text("또는", fontSize = 12.sp, color = WearonColors.SubText, modifier = Modifier.padding(horizontal = 12.dp))
            Box(Modifier.weight(1f).height(1.dp).background(WearonColors.Line))
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Google 로그인
        Button(
            onClick = { launchLogin("google") },
            enabled = !busy,
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
            enabled = !busy,
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
