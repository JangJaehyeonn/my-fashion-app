package com.fashionapp.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

val ErrorRed = Color(0xFFD64545)

/**
 * 아이디·비밀번호·닉네임 입력 칸. [error]가 있으면 칸 아래에 빨간 안내, 없고 [helper]가 있으면 회색 안내.
 * [isPassword]면 입력을 마스킹하고 비밀번호 키보드를 쓴다.
 * [contentType]은 Android Autofill 힌트 — 저장된 아이디·비밀번호 제안(Username/Password)과
 * 새 계정 저장 제안(NewUsername/NewPassword)에 쓰인다 (Compose 1.8+의 semantics 기반 Autofill).
 */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    error: String? = null,
    helper: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
    contentType: ContentType? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = error != null,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Ascii,
                autoCorrectEnabled = false,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            trailingIcon = trailing,
            shape = WearonShapes.Card,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WearonColors.Ink,
                unfocusedBorderColor = WearonColors.Line,
                focusedLabelColor = WearonColors.Ink,
                cursorColor = WearonColors.Ink,
                focusedContainerColor = WearonColors.White,
                unfocusedContainerColor = WearonColors.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (contentType != null) Modifier.semantics { this.contentType = contentType } else Modifier)
        )
        val note = error ?: helper
        if (note != null) {
            Text(
                note,
                fontSize = 12.sp,
                color = if (error != null) ErrorRed else WearonColors.SubText,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}
