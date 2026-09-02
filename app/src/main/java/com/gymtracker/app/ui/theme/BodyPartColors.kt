package com.gymtracker.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 부위별 고정 색. 목록의 부위 배지와 부위 필터칩에 같은 색을 써서
 * "이 색 = 이 부위"가 화면 어디서나 같은 뜻이 되도록 한다.
 * 부위를 새로 만들면 여기에 없으므로 회색(fallback)으로 나온다.
 */
private val BODY_PART_COLORS = mapOf(
    "가슴" to Color(0xFFE53935),
    "등" to Color(0xFF1E88E5),
    "어깨" to Color(0xFFFB8C00),
    "하체" to Color(0xFF43A047),
    "팔" to Color(0xFF8E24AA),
    "코어" to Color(0xFF00897B),
    "유산소" to Color(0xFF3949AB)
)

private val FallbackBodyPartColor = Color(0xFF757575)

fun bodyPartColor(bodyPart: String): Color =
    BODY_PART_COLORS[bodyPart] ?: FallbackBodyPartColor
/** 생리일 표시 색. 오늘 화면 버튼과 달력 점이 같은 색을 쓴다. */
val PeriodColor = Color(0xFFE91E63)
