package com.gymtracker.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * 기본 Material3 Typography 대비 lineHeight를 줄여서 Text/ListItem/TextField 등
 * 모든 텍스트의 위아래 내부 여백을 살짝 좁힌다.
 * 여기 숫자(lineHeight)를 더 줄이면 더 좁아지고, 늘리면 다시 넓어진다 — 조절 지점은 이 파일 하나뿐이다.
 */
val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 15.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp)
)
