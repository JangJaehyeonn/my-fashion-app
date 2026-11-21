package com.fashionapp.ui.signup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fashionapp.BuildConfig
import com.fashionapp.data.model.AgreementItem
import com.fashionapp.data.model.Agreements
import com.fashionapp.data.model.LegalPage
import com.fashionapp.ui.common.ErrorRed
import com.fashionapp.ui.theme.WearonColors

/** "전체 동의" + 이용약관 · 개인정보처리방침 · 만 14세 이상 — ID signup and social first-login screens share this. */
@Composable
fun AgreementsSection(
    agreements: Agreements,
    error: String?,
    enabled: Boolean,
    onAllChange: (Boolean) -> Unit,
    onItemChange: (AgreementItem, Boolean) -> Unit
) {
    val context = LocalContext.current
    Column {
        CheckRow(
            label = "전체 동의",
            checked = agreements.all,
            enabled = enabled,
            bold = true,
            onCheckedChange = onAllChange
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(WearonColors.Line))
        AgreementItem.entries.forEach { item ->
            CheckRow(
                label = item.label,
                checked = agreements.isChecked(item),
                enabled = enabled,
                onCheckedChange = { onItemChange(item, it) },
                trailing = item.link?.let { page -> { ViewLink(onClick = { openLegalPage(context, page) }) } }
            )
        }
        error?.let { Text(it, fontSize = 12.sp, color = ErrorRed, modifier = Modifier.padding(start = 12.dp, top = 2.dp)) }
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    bold: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = CheckboxDefaults.colors(checkedColor = WearonColors.Ink)
            )
            Text(
                label,
                fontSize = if (bold) 15.sp else 14.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = WearonColors.Ink
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun ViewLink(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("보기", fontSize = 13.sp, color = WearonColors.SubText, textDecoration = TextDecoration.Underline)
    }
}

/** Opens the server's public legal page in an external browser (release/debug server base URL). */
fun openLegalPage(context: Context, page: LegalPage) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page.url(BuildConfig.OAUTH2_BASE_URL))))
    }
}
