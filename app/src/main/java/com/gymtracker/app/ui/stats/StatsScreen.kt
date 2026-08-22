package com.gymtracker.app.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** 통계 탭. History/그래프는 Phase 3에서 구현 예정. */
@Composable
fun StatsScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("통계 화면은 준비 중입니다", style = MaterialTheme.typography.titleMedium)
        Text("운동별 History와 중량 변화 그래프가 이곳에 표시될 예정입니다.", style = MaterialTheme.typography.bodyMedium)
    }
}
