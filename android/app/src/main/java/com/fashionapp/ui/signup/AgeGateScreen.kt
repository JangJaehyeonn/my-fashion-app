package com.fashionapp.ui.signup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fashionapp.data.model.AgreementItem
import com.fashionapp.data.model.Agreements
import com.fashionapp.ui.common.ErrorRed
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

/**
 * 소셜 로그인 첫 가입(또는 이 확인이 생기기 전에 가입한 사용자)에게 한 번 보여주는 필수 동의 확인:
 * 이용약관 · 개인정보처리방침 · 만 14세 이상 (FR-06). 모두 체크해야 "계속하기"가 활성화된다.
 * 확인하면 서버에 동의가 저장되고, 미만이면 계정을 바로 삭제한다(가입 불가). 뒤로 가기로 건너뛸 수 없다.
 */
@Composable
fun AgeGateScreen(
    agreements: Agreements,
    isWorking: Boolean,
    error: String?,
    onAllChange: (Boolean) -> Unit,
    onItemChange: (AgreementItem, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onUnder14: () -> Unit
) {
    BackHandler(enabled = true) { /* 확인 전에는 앱으로 들어갈 수 없음 */ }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("가입 전에 확인해 주세요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text("Wearon은 만 14세 이상만 가입할 수 있어요.", fontSize = 14.sp, color = WearonColors.SubText)
        Spacer(Modifier.height(24.dp))
        AgreementsSection(
            agreements = agreements,
            error = null,
            enabled = !isWorking,
            onAllChange = onAllChange,
            onItemChange = onItemChange
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 13.sp, color = ErrorRed)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onConfirm,
            enabled = agreements.all && !isWorking,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = WearonShapes.Card,
            colors = ButtonDefaults.buttonColors(containerColor = WearonColors.Ink, disabledContainerColor = WearonColors.Line)
        ) {
            if (isWorking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("계속하기", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onUnder14, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) {
            Text("만 14세 미만이에요 (가입 취소)", fontSize = 13.sp, color = WearonColors.SubText)
        }
    }
}
