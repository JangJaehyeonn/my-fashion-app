package com.fashionapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Wearon 브랜드 팔레트 — 아이보리/베이지 베이스 + 블랙 포인트 (무신사·지그재그류 미니멀 톤)
object WearonColors {
    val Ink = Color(0xFF1A1A1A)          // 본문 텍스트, 주요 버튼
    val Ivory = Color(0xFFFAFAF7)        // 앱 배경
    val White = Color(0xFFFFFFFF)        // 카드 표면
    val Beige = Color(0xFFF5F0E8)        // 이미지 플레이스홀더, 강조 영역 배경
    val BeigeDeep = Color(0xFFD8CCBB)    // 포인트, 인디케이터
    val Line = Color(0xFFE9E6E0)         // 구분선, 칩 테두리
    val SubText = Color(0xFF8C8882)      // 보조 텍스트
}

// 모서리 — 쇼핑몰 앱처럼 각진 느낌을 위해 작게 유지
object WearonShapes {
    val Image = RoundedCornerShape(4.dp)
    val Card = RoundedCornerShape(8.dp)
}

private val ColorScheme = lightColorScheme(
    primary = WearonColors.Ink,
    onPrimary = WearonColors.White,
    secondary = WearonColors.BeigeDeep,
    background = WearonColors.Ivory,
    surface = WearonColors.White,
    onBackground = WearonColors.Ink,
    onSurface = WearonColors.Ink,
    outline = WearonColors.Line,
)

// 기본 산세리프(한글은 시스템 Noto Sans CJK)에 자간을 살짝 좁혀 정돈된 인상으로
private val Sans = FontFamily.SansSerif
private val WearonTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = (-0.3).sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = (-0.1).sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = (-0.2).sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = (-0.1).sp),
)

@Composable
fun FashionAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = WearonTypography,
        content = content
    )
}
