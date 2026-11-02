package com.fashionapp.ui.signup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
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

/** 아이디 회원가입: 아이디(중복 확인) · 비밀번호 · 비밀번호 확인 · 닉네임. 성공하면 바로 로그인 상태로 홈 이동 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onBack: () -> Unit,
    onSignedUp: () -> Unit,
    viewModel: SignupViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsState()
    val focusManager = LocalFocusManager.current

    fun submit() {
        focusManager.clearFocus()
        viewModel.submit(onSignedUp)
    }

    Scaffold(
        containerColor = WearonColors.Ivory,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("회원가입", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
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
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthTextField(
                    value = form.loginId,
                    onValueChange = viewModel::onLoginIdChange,
                    label = "아이디",
                    contentType = ContentType.NewUsername,
                    error = form.loginIdError,
                    helper = form.loginIdHelper,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = viewModel::checkLoginId,
                    enabled = form.canCheckId,
                    shape = WearonShapes.Card,
                    border = BorderStroke(1.dp, if (form.canCheckId) WearonColors.Ink else WearonColors.Line),
                    modifier = Modifier.padding(top = 8.dp).height(52.dp)
                ) {
                    if (form.idCheck == IdCheck.CHECKING) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WearonColors.Ink)
                    } else {
                        Text("중복 확인", fontSize = 13.sp, color = WearonColors.Ink)
                    }
                }
            }
            AuthTextField(
                value = form.password,
                onValueChange = viewModel::onPasswordChange,
                label = "비밀번호",
                contentType = ContentType.NewPassword,
                isPassword = true,
                error = form.passwordError,
                helper = AuthRules.PASSWORD_RULE
            )
            AuthTextField(
                value = form.passwordConfirm,
                onValueChange = viewModel::onPasswordConfirmChange,
                label = "비밀번호 확인",
                contentType = ContentType.NewPassword,
                isPassword = true,
                error = form.passwordConfirmError
            )
            AuthTextField(
                value = form.nickname,
                onValueChange = viewModel::onNicknameChange,
                label = "닉네임",
                error = form.nicknameError,
                helper = "1~20자",
                imeAction = ImeAction.Done,
                onImeAction = ::submit
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
                    Text("가입하기", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
