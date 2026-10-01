package com.fashionapp.ui.closet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

/**
 * 쇼핑몰 상품 주소를 붙여넣어 옷장에 등록하는 다이얼로그.
 * 이미지 추출은 피팅 탭과 같은 ProductPageRepository(공개 상품 페이지의 og:image 등)를 쓰고, 로그인/구매 내역 접근은 하지 않는다.
 */
@Composable
fun UrlRegisterDialog(
    state: UrlRegisterState,
    onUrlChange: (String) -> Unit,
    onFetch: () -> Unit,
    onSelectImage: (String) -> Unit,
    onRegister: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WearonColors.White,
        title = { Text("상품 주소로 등록", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(WearonShapes.Card)
                        .background(WearonColors.Ivory)
                        .border(1.dp, WearonColors.Line, WearonShapes.Card)
                        .padding(start = 12.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (state.url.isEmpty()) {
                            Text("무신사·29CM 등 상품 링크", fontSize = 13.sp, color = WearonColors.SubText)
                        }
                        BasicTextField(
                            value = state.url,
                            onValueChange = onUrlChange,
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = WearonColors.Ink),
                            cursorBrush = SolidColor(WearonColors.Ink),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus(); onFetch() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    val canFetch = !state.isFetching && state.url.isNotBlank()
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(WearonShapes.Image)
                            .background(if (canFetch) WearonColors.Ink else WearonColors.Line)
                            .clickable(enabled = canFetch) { focusManager.clearFocus(); onFetch() }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isFetching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WearonColors.Ink)
                        } else {
                            Text("불러오기", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }

                state.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 12.sp, color = Color(0xFFD64545))
                }

                state.product?.let { page ->
                    Spacer(Modifier.height(14.dp))
                    page.title?.let {
                        Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WearonColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        page.imageUrls.forEach { url ->
                            val selected = url == state.selectedImageUrl
                            AsyncImage(
                                model = url,
                                contentDescription = "상품 이미지 후보",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 78.dp, height = 98.dp)
                                    .clip(WearonShapes.Image)
                                    .background(WearonColors.Beige)
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) WearonColors.Ink else WearonColors.Line,
                                        WearonShapes.Image
                                    )
                                    .clickable { onSelectImage(url) }
                            )
                        }
                    }
                    if (page.imageUrls.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        Text("옷만 나온 사진을 고르면 분류가 더 정확해요", fontSize = 11.sp, color = WearonColors.SubText)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRegister, enabled = state.selectedImageUrl != null) {
                Text("등록", fontWeight = FontWeight.Bold, color = if (state.selectedImageUrl != null) WearonColors.Ink else WearonColors.SubText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = WearonColors.Ink) }
        }
    )
}
