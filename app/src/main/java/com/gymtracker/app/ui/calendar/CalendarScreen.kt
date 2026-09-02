package com.gymtracker.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymtracker.app.ui.theme.PeriodColor
import com.gymtracker.app.ui.theme.bodyPartColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 달력 탭: 운동이 기록된 날짜에 그날 한 부위를 색 글씨로 적어서 한 달을 한눈에 본다.
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
    val summaries by calendarViewModel.summaries.collectAsState()
    val periodDates by calendarViewModel.periodDates.collectAsState()
    var showMonthPicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        MonthHeader(
            month = month,
            onPrevious = { calendarViewModel.goToPreviousMonth() },
            onNext = { calendarViewModel.goToNextMonth() },
            onCurrent = { calendarViewModel.goToCurrentMonth() },
            onMonthClick = { showMonthPicker = true }
        )
        WeekdayHeader()
        MonthGrid(
            month = month,
            today = today,
            bodyPartsByDate = bodyPartsByDate,
            periodDates = periodDates,
            onDateClick = onDatePicked
        )
        Spacer(Modifier.height(12.dp))
        MonthSummary(month = month, summaries = summaries)
        Spacer(Modifier.height(12.dp))
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            initial = month,
            onDismiss = { showMonthPicker = false },
            onPick = {
                calendarViewModel.goToMonth(it)
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
    onMonthClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
        }
        // 년월을 누르면 월 선택 팝업 — 먼 달로 한 번에 건너간다.
        TextButton(onClick = onMonthClick) {
            Text(
                "${month.year}년 ${month.monthValue}월",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (month != YearMonth.now()) {
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
    periodDates: Set<LocalDate>,
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
                        isPeriod = date in periodDates,
                        onClick = { onDateClick(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 날짜 칸에 들어가는 부위 글씨 크기 — 좁은 칸에 두 줄 넣으려고 아주 작게 잡는다. */
private val PART_LABEL_SIZE = 9.sp

/** 부위 글씨는 최대 두 개까지, 그 이상은 "+N"으로. */
private const val MAX_PART_LABELS = 2

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    bodyParts: List<String>,
    isPeriod: Boolean,
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
            // 날짜 밑에 부위 글씨 두 줄이 들어갈 높이.
            .height(74.dp)
            .padding(1.dp)
            .then(
                if (isToday) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(top = 3.dp, start = 1.dp, end = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 생리일이면 날짜 옆에 빨간 점.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = dayColor
            )
            if (isPeriod) {
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier
                        .size(5.dp)
                        .background(
                            PeriodColor.copy(alpha = if (inMonth) 1f else 0.35f),
                            CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        bodyParts.take(MAX_PART_LABELS).forEach { part ->
            val color = bodyPartColor(part)
            Text(
                part,
                fontSize = PART_LABEL_SIZE,
                lineHeight = 11.sp,
                color = if (inMonth) color else color.copy(alpha = 0.35f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color.copy(alpha = if (inMonth) 0.14f else 0.05f),
                        RoundedCornerShape(3.dp)
                    )
            )
            Spacer(Modifier.height(1.dp))
        }
        if (bodyParts.size > MAX_PART_LABELS) {
            Text(
                "+${bodyParts.size - MAX_PART_LABELS}",
                fontSize = PART_LABEL_SIZE,
                lineHeight = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ------------------------------------------------------------- 월 선택 팝업

/** 월 선택 팝업: 년도를 ◀ ▶로 옮기고 1~12월을 격자에서 고른다. */
@Composable
private fun MonthPickerDialog(
    initial: YearMonth,
    onDismiss: () -> Unit,
    onPick: (YearMonth) -> Unit
) {
    var year by remember { mutableIntStateOf(initial.year) }
    val currentMonth = YearMonth.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("월 선택") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = { year-- }) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "이전 해")
                    }
                    Text(
                        "${year}년",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { year++ }) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "다음 해")
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 3줄 x 4칸.
                repeat(3) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(4) { column ->
                            val monthValue = row * 4 + column + 1
                            val target = YearMonth.of(year, monthValue)
                            MonthCell(
                                label = "${monthValue}월",
                                selected = target == initial,
                                isCurrent = target == currentMonth,
                                onClick = { onPick(target) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun MonthCell(
    label: String,
    selected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .padding(2.dp)
            .height(40.dp)
            .background(
                if (selected) primary.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .then(
                if (isCurrent && !selected) Modifier.border(1.dp, primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ------------------------------------------------------------- 월간 요약

@Composable
private fun MonthSummary(month: YearMonth, summaries: List<BodyPartSummary>) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${month.monthValue}월 부위별 요약",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "시행횟수 · 총량 · 마지막 수행 후 경과일",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (summaries.isEmpty()) {
                Text(
                    "아직 기록이 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            summaries.forEach { summary ->
                SummaryRow(summary)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun SummaryRow(summary: BodyPartSummary) {
    val color = bodyPartColor(summary.bodyPart)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            summary.bodyPart,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(48.dp)
        )
        Text(
            "${summary.sessionCount}회",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(40.dp)
        )
        Text(
            totalLabel(summary),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            elapsedLabel(summary.daysSinceLast),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 중량 운동은 kg, 시간 운동은 분으로. 둘 다 있으면 나란히 적는다. */
private fun totalLabel(summary: BodyPartSummary): String {
    val parts = buildList {
        if (summary.totalVolume > 0) add("${fmt(summary.totalVolume)}kg")
        if (summary.totalSeconds > 0) add("${summary.totalSeconds / 60}분")
    }
    return if (parts.isEmpty()) "-" else parts.joinToString(" · ")
}

private fun elapsedLabel(daysSinceLast: Int?): String = when {
    daysSinceLast == null -> "기록 없음"
    daysSinceLast == 0 -> "오늘"
    else -> "${daysSinceLast}일 전"
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
