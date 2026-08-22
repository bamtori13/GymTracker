package com.gymtracker.app.ui.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 달력 탭: 날짜를 하나 고르면 "오늘" 탭으로 이동해서 그날 기록을 보여준다.
 * (오늘 화면 안에서도 날짜 텍스트를 눌러 같은 달력을 띄울 수 있다 — 이 탭은 달력 자체를 진입점으로 쓰고 싶을 때를 위한 것.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onDatePicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    Column(Modifier.fillMaxSize()) {
        DatePicker(state = state, modifier = Modifier.weight(1f))
        Button(
            onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    onDatePicked(picked)
                }
            },
            enabled = state.selectedDateMillis != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("이 날짜 기록 보기")
        }
    }
}
