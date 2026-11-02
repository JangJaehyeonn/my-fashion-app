package com.fashionapp.ui.mypage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fashionapp.data.model.AuthRules
import com.fashionapp.ui.common.AuthTextField
import com.fashionapp.ui.common.ErrorRed
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

/** 비밀번호 변경 (아이디 가입 사용자만 마이 탭에서 진입). 성공하면 이 기기는 새 토큰으로 로그인 유지 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsState()
    val focusManager = LocalFocusManager.current

    fun submit() {
        focusManager.clearFocus()
        viewModel.submit(onChanged)
    }

    Scaffold(
        containerColor = WearonColors.Ivory,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("비밀번호 변경", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WearonColors.Ivory)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AuthTextField(
                value = form.current,
                onValueChange = viewModel::onCurrentChange,
                label = "현재 비밀번호",
                isPassword = true,
                error = form.currentFieldError
            )
            AuthTextField(
                value = form.new,
                onValueChange = viewModel::onNewChange,
                label = "새 비밀번호",
                isPassword = true,
                error = form.newError,
                helper = AuthRules.PASSWORD_RULE
            )
            AuthTextField(
                value = form.confirm,
                onValueChange = viewModel::onConfirmChange,
                label = "새 비밀번호 확인",
                isPassword = true,
                error = form.confirmError,
                imeAction = ImeAction.Done,
                onImeAction = ::submit
            )
            Text(
                "바꾸면 다른 기기에서는 다시 로그인해야 해요.",
                fontSize = 12.sp,
                color = WearonColors.SubText
            )
            form.generalError?.let { Text(it, fontSize = 13.sp, color = ErrorRed) }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = ::submit,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = WearonShapes.Card,
                colors = ButtonDefaults.buttonColors(containerColor = WearonColors.Ink, disabledContainerColor = WearonColors.Line)
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("변경하기", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
