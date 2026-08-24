package com.gymtracker.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymtracker.app.data.local.DefaultExercises
import com.gymtracker.app.ui.theme.bodyPartColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 달력 탭: 운동이 기록된 날짜에 그날 한 부위들의 색 점을 찍어서 한 달을 한눈에 본다.
 * Material3 DatePicker는 날짜 칸을 꾸밀 수 없어서 월 그리드를 직접 그린다.
 * 날짜를 누르면 "오늘" 탭으로 넘어가 그날 기록을 보여준다.
 */
@Composable
fun CalendarScreen(
    calendarViewModel: CalendarViewModel,
    onDatePicked: (LocalDate) -> Unit
) {
    val month by calendarViewModel.month.collectAsState()
    val bodyPartsByDate by calendarViewModel.bodyPartsByDate.collectAsState()
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        MonthHeader(
            month = month,
            onPrevious = { calendarViewModel.goToPreviousMonth() },
            onNext = { calendarViewModel.goToNextMonth() },
            onCurrent = { calendarViewModel.goToCurrentMonth() }
        )
        WeekdayHeader()
        MonthGrid(
            month = month,
            today = today,
            bodyPartsByDate = bodyPartsByDate,
            onDateClick = onDatePicked
        )
        Spacer(Modifier.height(12.dp))
        BodyPartLegend()
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
        }
        Text(
            "${month.year}년 ${month.monthValue}월",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (month != YearMonth.now()) {
            Spacer(Modifier.width(6.dp))
            AssistChip(onClick = onCurrent, label = { Text("이번달") })
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "다음 달")
        }
    }
}

/** 월요일 시작. 토/일은 색을 달리해서 주말을 구분한다. */
private val WEEK_START = DayOfWeek.MONDAY
private val WEEKDAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        WEEKDAY_LABELS.forEachIndexed { index, label ->
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = weekdayColor(index),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun weekdayColor(indexFromMonday: Int): Color = when (indexFromMonday) {
    5 -> MaterialTheme.colorScheme.primary          // 토
    6 -> MaterialTheme.colorScheme.error            // 일
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    bodyPartsByDate: Map<LocalDate, List<String>>,
    onDateClick: (LocalDate) -> Unit
) {
    // 그리드 첫 칸은 1일이 속한 주의 월요일. 그 뒤로 7칸씩 끊어서 이번 달이 다 들어갈 만큼만 그린다.
    val firstOfMonth = month.atDay(1)
    val leadingDays = (firstOfMonth.dayOfWeek.value - WEEK_START.value + 7) % 7
    val gridStart = firstOfMonth.minusDays(leadingDays.toLong())
    val weekCount = ((leadingDays + month.lengthOfMonth()) + 6) / 7

    Column(Modifier.fillMaxWidth()) {
        repeat(weekCount) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { dayOfWeek ->
                    val date = gridStart.plusDays((week * 7 + dayOfWeek).toLong())
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        bodyParts = bodyPartsByDate[date].orEmpty(),
                        onClick = { onDateClick(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    bodyParts: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayColor = when {
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        date.dayOfWeek == DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
        date.dayOfWeek == DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .height(52.dp)
            .padding(2.dp)
            .then(
                if (isToday) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = dayColor
        )
        Spacer(Modifier.height(3.dp))
        // 부위 색 점. 4개까지만 찍고 넘치면 마지막 칸을 회색 점으로 표시한다.
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            bodyParts.take(4).forEach { part ->
                Dot(bodyPartColor(part), dimmed = !inMonth)
            }
            if (bodyParts.size > 4) {
                Dot(MaterialTheme.colorScheme.onSurfaceVariant, dimmed = !inMonth)
            }
        }
    }
}

@Composable
private fun Dot(color: Color, dimmed: Boolean) {
    Box(
        Modifier
            .size(6.dp)
            .background(color.copy(alpha = if (dimmed) 0.3f else 1f), CircleShape)
    )
}

/** 어떤 색이 어떤 부위인지 알려주는 범례. 가로로 넘치면 스크롤. */
@Composable
private fun BodyPartLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DefaultExercises.BODY_PARTS.forEach { part ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(bodyPartColor(part), dimmed = false)
                Spacer(Modifier.width(4.dp))
                Text(
                    part,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
