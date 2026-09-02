package com.gymtracker.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gymtracker.app.ui.theme.bodyPartColor
import com.gymtracker.app.ui.util.matchesSearch
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * 통계 탭 — "점진적 과부하가 되고 있나?"에 답하는 화면.
 *
 * 세 층으로 본다.
 * 1) 주간 총 볼륨 막대: 훈련량 자체가 늘고 있는지 (부위별로 색 분해).
 * 2) 운동 선택 칩 + 판정 카드: 고른 운동이 진전/정체/후퇴인지와 근거 숫자.
 * 3) 추정 1RM 꺾은선 + 세션별 볼륨 막대: 그 판정이 어떤 흐름에서 나왔는지.
 */
@Composable
fun StatsScreen(statsViewModel: StatsViewModel) {
    val state by statsViewModel.uiState.collectAsState()

    if (state.isEmpty) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("아직 분석할 기록이 없습니다", style = MaterialTheme.typography.titleMedium)
            Text(
                "오늘 화면에서 세트를 완료(✓) 체크하면 여기에 쌓입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WeeklyVolumeCard(weeks = state.weeks, workoutDays = state.workoutDays)

        ExercisePicker(
            trends = state.trends,
            selectedId = state.selected?.exerciseId,
            onSelect = { statsViewModel.selectExercise(it) }
        )

        state.selected?.let { trend ->
            VerdictCard(trend)
            OneRepMaxCard(trend)
            SessionVolumeCard(trend)
        }
    }
}

// ---------------------------------------------------------------- 주간 볼륨

@Composable
private fun WeeklyVolumeCard(weeks: List<WeekVolume>, workoutDays: Int) {
    if (weeks.isEmpty()) return
    val max = weeks.maxOfOrNull { it.volume } ?: 0.0
    val recent = weeks.lastOrNull()?.volume ?: 0.0
    val previous = weeks.getOrNull(weeks.lastIndex - 1)?.volume ?: 0.0

    SectionCard(
        title = "주간 총 볼륨",
        subtitle = "최근 ${weeks.size}주 · 운동한 날 ${workoutDays}일"
    ) {
        Text(
            "이번 주 ${fmt(recent)} " + weekDelta(recent, previous),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            weeks.forEach { week ->
                StackedWeekBar(week = week, max = max, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val label = DateTimeFormatter.ofPattern("M/d")
            Text(
                weeks.first().weekStart.format(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                weeks.last().weekStart.format(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        BodyPartLegend(weeks.flatMap { it.byBodyPart.keys }.distinct())
    }
}

/** 한 주 막대. 부위별로 색을 쌓아서 어느 부위에 볼륨이 몰렸는지 같이 보인다. */
@Composable
private fun StackedWeekBar(week: WeekVolume, max: Double, modifier: Modifier = Modifier) {
    val fraction = if (max <= 0.0) 0f else (week.volume / max).toFloat()
    // 부위 색이 매번 뒤바뀌지 않게 이름순으로 고정한다.
    val segments = week.byBodyPart.entries.sortedBy { it.key }
    val emptyColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Bottom
    ) {
        if (week.volume <= 0.0) {
            // 쉰 주도 자리를 남겨서 "빠진 주"가 눈에 보이게 한다.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(emptyColor, RoundedCornerShape(1.dp))
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction.coerceAtLeast(0.02f))
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                    segments.forEach { (part, value) ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight((value / week.volume).toFloat().coerceAtLeast(0.01f))
                                .background(bodyPartColor(part))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyPartLegend(parts: List<String>) {
    if (parts.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        parts.sorted().forEach { part ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(bodyPartColor(part), RoundedCornerShape(2.dp))
                )
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

// ---------------------------------------------------------------- 운동 선택

/**
 * 운동 선택 드롭다운. 칩을 옆으로 늘어놓으면 운동이 늘수록 못 찾으므로,
 * 눌러서 펼치고 검색(초성 포함)으로 좁히는 방식으로 바꿨다.
 */
@Composable
private fun ExercisePicker(
    trends: List<ExerciseTrend>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val selected = trends.firstOrNull { it.exerciseId == selectedId }
    val filtered = trends.filter { matchesSearch(it.name, query) }

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            selected?.let {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(bodyPartColor(it.bodyPart), CircleShape)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                selected?.name ?: "운동 선택",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("▼", style = MaterialTheme.typography.labelMedium)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false; query = "" },
            modifier = Modifier.heightIn(max = 380.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("검색 (초성 가능)") },
                singleLine = true,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
            )
            if (filtered.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("일치하는 운동이 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {},
                    enabled = false
                )
            }
            filtered.forEach { trend ->
                val color = bodyPartColor(trend.bodyPart)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(color, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                trend.name,
                                fontWeight = if (trend.exerciseId == selectedId) FontWeight.Bold
                                else FontWeight.Normal
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${trend.bodyPart} · ${trend.points.size}회",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelect(trend.exerciseId)
                        expanded = false
                        query = ""
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 판정

@Composable
private fun VerdictCard(trend: ExerciseTrend) {
    val (label, color, explanation) = when (trend.verdict) {
        OverloadVerdict.PROGRESS -> Triple(
            "진전 중",
            Color(0xFF2E7D32),
            "추정 1RM이 주당 ${fmt(trend.perWeekChange)}${trend.unit}씩 오르고 있습니다."
        )
        OverloadVerdict.HOLD -> Triple(
            "정체",
            Color(0xFFEF6C00),
            "추정 1RM이 거의 그대로입니다(주당 ${fmt(trend.perWeekChange)}${trend.unit}). " +
                "무게·횟수·세트 중 하나를 올려볼 시점입니다."
        )
        OverloadVerdict.REGRESS -> Triple(
            "후퇴",
            Color(0xFFC62828),
            "추정 1RM이 주당 ${fmt(trend.perWeekChange)}${trend.unit}씩 내려가고 있습니다. " +
                "회복이나 볼륨 과다를 점검해보세요."
        )
        OverloadVerdict.NOT_ENOUGH_DATA -> Triple(
            "데이터 부족",
            MaterialTheme.colorScheme.outline,
            "추세를 보려면 이 운동을 최소 3번은 기록해야 합니다 (현재 ${trend.points.size}회)."
        )
    }

    SectionCard(title = trend.name, subtitle = trend.bodyPart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier
                    .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Metric("PR", "${fmt(trend.pr)}${trend.unit}", Modifier.weight(1f))
            Metric("기록 횟수", "${trend.points.size}회", Modifier.weight(1f))
            Metric(
                "최근 4주 볼륨",
                trend.volumeChangePercent?.let { "${if (it >= 0) "+" else ""}${fmt(it)}%" } ?: "-",
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- 그래프

@Composable
private fun OneRepMaxCard(trend: ExerciseTrend) {
    val title = if (trend.isTime) "최장 시간 추세" else "추정 1RM 추세"
    SectionCard(
        title = title,
        subtitle = if (trend.isTime) "그날 가장 오래 버틴 세트" else "Epley: 무게 × (1 + 횟수/30)"
    ) {
        LineChart(
            values = trend.points.map { it.best },
            color = bodyPartColor(trend.bodyPart),
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )
        AxisLabels(trend)
    }
}

@Composable
private fun SessionVolumeCard(trend: ExerciseTrend) {
    val max = trend.points.maxOfOrNull { it.volume } ?: 0.0
    SectionCard(title = "세션별 볼륨", subtitle = if (trend.isTime) "합계 초" else "무게 × 횟수 합계") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val color = bodyPartColor(trend.bodyPart)
            trend.points.forEach { point ->
                val fraction = if (max <= 0.0) 0f else (point.volume / max).toFloat()
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                        .background(color.copy(alpha = 0.7f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                )
            }
        }
        AxisLabels(trend)
    }
}

@Composable
private fun AxisLabels(trend: ExerciseTrend) {
    val formatter = DateTimeFormatter.ofPattern("M/d")
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            trend.points.first().date.format(formatter),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            trend.points.last().date.format(formatter),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 꺾은선 그래프. Compose에 기본 차트가 없어서 Canvas로 직접 그린다.
 * y축은 데이터의 min~max에 맞춰 늘려서(0부터 그리지 않고) 작은 변화도 보이게 한다.
 */
@Composable
private fun LineChart(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val fillColor = color.copy(alpha = 0.15f)

    Canvas(modifier) {
        val min = values.min()
        val max = values.max()
        // 전 구간이 같은 값이면 0으로 나누지 않도록 폭을 1 준다.
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val stepX = if (values.size == 1) 0f else size.width / (values.size - 1)

        fun pointAt(index: Int): Offset {
            val x = if (values.size == 1) size.width / 2f else index * stepX
            val y = (size.height * (1 - (values[index] - min) / span)).toFloat()
            return Offset(x, y)
        }

        // 기준선 3개 (최소/중간/최대)
        listOf(0f, 0.5f, 1f).forEach { ratio ->
            val y = size.height * ratio
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        val line = Path()
        values.indices.forEach { index ->
            val point = pointAt(index)
            if (index == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
        }

        // 선 아래를 옅게 채워서 추세 방향이 한눈에 들어오게 한다.
        val area = Path().apply {
            addPath(line)
            lineTo(pointAt(values.lastIndex).x, size.height)
            lineTo(pointAt(0).x, size.height)
            close()
        }
        drawPath(area, fillColor)
        drawPath(line, color, style = Stroke(width = 3f))

        values.indices.forEach { index ->
            val point = pointAt(index)
            drawCircle(color, radius = 4f, center = point)
        }

        // 마지막 점만 크게 강조.
        drawCircle(color, radius = 7f, center = pointAt(values.lastIndex))
    }
}

// ---------------------------------------------------------------- 공통

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

private fun weekDelta(recent: Double, previous: Double): String {
    if (previous <= 0.0) return ""
    val diff = (recent - previous) / previous * 100.0
    val arrow = if (diff >= 0) "▲" else "▼"
    return "($arrow ${fmt(abs(diff))}% vs 지난주)"
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
