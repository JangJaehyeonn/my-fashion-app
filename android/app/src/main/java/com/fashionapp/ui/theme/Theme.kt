package com.fashionapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Wearon 브랜드 팔레트 — 화이트/베이지 베이스의 미니멀 톤
object WearonColors {
    val Ink = Color(0xFF1A1A1A)          // 본문 텍스트, 주요 버튼
    val Ivory = Color(0xFFFAF8F5)        // 앱 배경
    val White = Color(0xFFFFFFFF)        // 카드 표면
    val Beige = Color(0xFFEDE6DC)        // 카드 상단 영역, 선택 칩 배경
    val BeigeDeep = Color(0xFFC9B8A3)    // 포인트, 인디케이터
    val Line = Color(0xFFE8E3DC)         // 구분선, 칩 테두리
    val SubText = Color(0xFF8A8580)      // 보조 텍스트
}

private val ColorScheme = lightColorScheme(
    primary = WearonColors.Ink,
    onPrimary = WearonColors.White,
    secondary = WearonColors.BeigeDeep,
    background = WearonColors.Ivory,
    surface = WearonColors.White,
    onBackground = WearonColors.Ink,
    onSurface = WearonColors.Ink,
)

@Composable
fun FashionAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content
    )
}
