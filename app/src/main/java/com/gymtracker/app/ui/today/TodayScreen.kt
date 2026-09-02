package com.gymtracker.app.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gymtracker.app.data.local.DefaultExercises
import com.gymtracker.app.data.local.entity.Exercise
import com.gymtracker.app.data.local.entity.ExerciseInputType
import com.gymtracker.app.data.local.entity.ExerciseSet
import com.gymtracker.app.data.local.entity.WorkoutRoutine
import com.gymtracker.app.ui.routine.RoutineViewModel
import com.gymtracker.app.ui.theme.PeriodColor
import com.gymtracker.app.ui.theme.bodyPartColor
import com.gymtracker.app.ui.util.matchesSearch
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 첫 화면("오늘"). 날짜 하나를 선택하면 그날 하기로 한(또는 한) 운동들을 카드로 펼쳐서
 * 계획/실적/PR을 한눈에 보고, 세트를 그 자리에서 기록한다.
 */
@Composable
fun TodayScreen(
    todayViewModel: TodayViewModel,
    routineViewModel: RoutineViewModel
) {
    val state by todayViewModel.uiState.collectAsState()
    var showCalendar by remember { mutableStateOf(false) }
    var showAddExercise by remember { mutableStateOf(false) }
    var showSaveRoutine by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DateHeader(
                date = state.date,
                isPeriod = state.isPeriod,
                onPrevious = { todayViewModel.goToPreviousDay() },
                onNext = { todayViewModel.goToNextDay() },
                onDateClick = { showCalendar = true },
                onTodayClick = { todayViewModel.goToToday() },
                onPeriodToggle = { todayViewModel.togglePeriod() }
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                CardList(
                    cards = state.cards,
                    onSwipePrevious = { todayViewModel.goToPreviousDay() },
                    onSwipeNext = { todayViewModel.goToNextDay() },
                    onMove = { from, to -> todayViewModel.moveCard(from, to) },
                    onAddExerciseClick = { showAddExercise = true },
                    onToggleExpand = { todayViewModel.toggleExpand(it) },
                    onRemove = { todayViewModel.removeCard(it) },
                    onAddSet = { todayViewModel.addSet(it) },
                    onSetChanged = { set, weight, reps -> todayViewModel.updateSet(set, weight, reps) },
                    onSetToggle = { todayViewModel.toggleSetCompleted(it) },
                    onSetDelete = { todayViewModel.deleteSet(it) },
                    onMemoChanged = { card, memo -> todayViewModel.updateMemo(card, memo) },
                    onSaveRoutineClick = { showSaveRoutine = true }
                )
            }
        }
    }

    if (showCalendar) {
        CalendarPickerDialog(
            initialDate = state.date,
            onDismiss = { showCalendar = false },
            onPick = { picked ->
                todayViewModel.loadDate(picked)
                showCalendar = false
              }
        )
    }

    if (showSaveRoutine) {
        SaveRoutineDialog(
            // 기본 이름은 부위 + 날짜 — 대개 "가슴·등 9/2" 같은 걸 그대로 쓰게 된다.
            initialName = suggestRoutineName(state),
            onDismiss = { showSaveRoutine = false },
            onConfirm = { name ->
                todayViewModel.saveTodayAsRoutine(name)
                showSaveRoutine = false
            }
        )
    }

    if (showAddExercise) {
        AddExercisePickerDialog(
            routineViewModel = routineViewModel,
            onDismiss = { showAddExercise = false },
            onPickExercise = { exerciseId ->
                todayViewModel.addExerciseToToday(exerciseId)
                showAddExercise = false
            },
            onPickRoutine = { routineId ->
                todayViewModel.addRoutineToToday(routineId)
                showAddExercise = false
            },
            onRoutineCreated = { routineId ->
                // 4) 새 루틴을 "만들기"하면 오늘에는 반영하되, 팝업은 닫지 않고 MAIN 화면으로 되돌아간다.
                todayViewModel.addRoutineToToday(routineId)
            }
        )
    }
}

@Composable
private fun DateHeader(
    date: LocalDate,
    isPeriod: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDateClick: () -> Unit,
    onTodayClick: () -> Unit,
    onPeriodToggle: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("M/d") }
    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 날짜 이동 묶음은 가운데, 생리일 버튼은 우측 상단 고정.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "전날")
            }
            TextButton(onClick = onDateClick) {
                Text(
                    "${date.format(formatter)} ($dayOfWeek)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (date != LocalDate.now()) {
                AssistChip(onClick = onTodayClick, label = { Text("오늘") })
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "다음날")
            }
        }
        PeriodToggleButton(isPeriod = isPeriod, onClick = onPeriodToggle)
    }
}

/** 생리일 체크 버튼. 켜져 있으면 달력 점과 같은 색으로 채워진다. */
@Composable
private fun PeriodToggleButton(isPeriod: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            Modifier
                .size(16.dp)
                .background(
                    if (isPeriod) PeriodColor else Color.Transparent,
                    CircleShape
                )
                .border(
                    1.5.dp,
                    if (isPeriod) PeriodColor else MaterialTheme.colorScheme.outline,
                    CircleShape
                )
        )
    }
}

/**
 * 카드 목록. 두 가지 제스처를 얹는다.
 * - 가로 스와이프: 전날/다음날로 이동. LazyColumn은 세로만 먹으므로 가로 드래그는 여기서 가로챈다.
 * - 카드 롱프레스 후 세로 드래그: 순서 변경. 손가락이 다른 카드 영역에 들어가는 순간 자리를 맞바꾸고,
 *   바뀐 만큼 누적 오프셋을 보정해서 끌고 있는 카드가 손가락 아래에 계속 붙어 있게 한다.
 */
@Composable
private fun CardList(
    cards: List<ExerciseCardUiState>,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onAddExerciseClick: () -> Unit,
    onToggleExpand: (Long) -> Unit,
    onRemove: (ExerciseCardUiState) -> Unit,
    onAddSet: (ExerciseCardUiState) -> Unit,
    onSetChanged: (ExerciseSet, Double, Int) -> Unit,
    onSetToggle: (ExerciseSet) -> Unit,
    onSetDelete: (ExerciseSet) -> Unit,
    onMemoChanged: (ExerciseCardUiState, String) -> Unit,
    onSaveRoutineClick: () -> Unit
) {
    val listState = rememberLazyListState()
    // -1 = 끌고 있는 카드 없음. dragOffset은 끌기 시작한 자리에서 누적된 y 이동량(px).
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val swipeAccum = remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { swipeAccum.value = 0f },
                    onDragEnd = {
                        when {
                            swipeAccum.value > swipeThresholdPx -> onSwipePrevious()
                            swipeAccum.value < -swipeThresholdPx -> onSwipeNext()
                        }
                    },
                    onHorizontalDrag = { _, dx -> swipeAccum.value += dx }
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        itemsIndexed(cards, key = { _, card -> card.sessionExercise.id }) { index, card ->
            // pointerInput 블록은 key(=운동 id)가 그대로면 다시 실행되지 않으므로,
            // 순서가 바뀐 뒤에도 최신 index/개수를 보려면 rememberUpdatedState로 읽어야 한다.
            val currentIndex by rememberUpdatedState(index)
            val cardCount by rememberUpdatedState(cards.size)
            ExerciseCard(
                card = card,
                onToggleExpand = { onToggleExpand(card.sessionExercise.id) },
                onRemove = { onRemove(card) },
                onAddSet = { onAddSet(card) },
                onSetChanged = onSetChanged,
                onSetToggle = onSetToggle,
                onSetDelete = onSetDelete,
                onMemoChanged = { memo -> onMemoChanged(card, memo) },
                modifier = Modifier
                    .zIndex(if (index == draggingIndex) 1f else 0f)
                    .graphicsLayer {
                        val dragging = index == draggingIndex
                        translationY = if (dragging) dragOffset else 0f
                        // 끌고 있는 카드는 살짝 키우고 투명하게 해서 들려 있는 느낌을 준다.
                        val scale = if (dragging) 1.02f else 1f
                        scaleX = scale
                        scaleY = scale
                        alpha = if (dragging) 0.92f else 1f
                    },
                dragHandleModifier = Modifier.pointerInput(card.sessionExercise.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingIndex = currentIndex
                            dragOffset = 0f
                        },
                        onDragEnd = { draggingIndex = -1; dragOffset = 0f },
                        onDragCancel = { draggingIndex = -1; dragOffset = 0f },
                        onDrag = { _, delta ->
                            dragOffset += delta.y
                            val from = draggingIndex
                            if (from < 0) return@detectDragGesturesAfterLongPress
                            val visible = listState.layoutInfo.visibleItemsInfo
                            val current = visible.firstOrNull { it.index == from }
                                ?: return@detectDragGesturesAfterLongPress
                            // 끌고 있는 카드의 "현재 화면상 중심"이 어느 카드 위에 있는지 본다.
                            val center = current.offset + current.size / 2f + dragOffset
                            val target = visible.firstOrNull {
                                it.index != from && it.index < cardCount &&
                                    center >= it.offset && center <= it.offset + it.size
                            } ?: return@detectDragGesturesAfterLongPress
                            // 자리가 바뀌면 카드가 순간이동하므로, 그만큼 오프셋을 빼서 손가락 아래에 유지한다.
                            dragOffset -= (target.offset - current.offset)
                            draggingIndex = target.index
                            onMove(from, target.index)
                        }
                    )
                }
            )
        }
        item {
            OutlinedButton(
                onClick = onAddExerciseClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ 운동추가")
            }
            // 오늘 구성을 그대로 루틴으로 굳혀두는 버튼. 운동이 하나도 없으면 의미가 없어서 숨긴다.
            if (cards.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = onSaveRoutineClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("오늘 운동을 루틴으로 저장 (${cards.size}개)")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}


@Composable
private fun ExerciseCard(
    card: ExerciseCardUiState,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onAddSet: () -> Unit,
    onSetChanged: (ExerciseSet, Double, Int) -> Unit,
    onSetToggle: (ExerciseSet) -> Unit,
    onSetDelete: (ExerciseSet) -> Unit,
    onMemoChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    var memoText by remember(card.sessionExercise.id, card.sessionExercise.memo) {
        mutableStateOf(card.sessionExercise.memo)
    }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    val isTime = card.exercise.inputType == ExerciseInputType.TIME
    val unit = if (isTime) "초" else "kg"

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            // 헤더: ▼/▶ 운동이름 ... 부위배지 삭제
            // 카드 전체가 롱프레스-드래그로 순서 변경 대상 — 짧게 누르면 접기/펼치기.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // clickable을 먼저 두어 드래그 감지기가 안쪽(=이벤트 우선)에 오게 한다.
                    // 그래야 롱프레스 드래그가 탭보다 먼저 이벤트를 소비하고, 손을 뗄 때 접기/펼치기가 오작동하지 않는다.
                    .clickable(onClick = onToggleExpand)
                    .then(dragHandleModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⠿", modifier = Modifier.padding(end = 6.dp), color = MaterialTheme.colorScheme.outline)
                Text(if (card.isExpanded) "▼" else "▶", modifier = Modifier.padding(end = 6.dp))
                Text(
                    card.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                BodyPartBadge(card.exercise.bodyPart)
                IconButton(onClick = { showRemoveConfirm = true }) {
                    Icon(Icons.Filled.Close, contentDescription = "운동 제거")
                }
            }

            // 요약은 접혀 있을 때도 항상 보인다 — 접힌 카드만 보고도 진행 상황을 알 수 있게.
            Text(
                "계획 ${fmt(card.planTotal)}$unit · 오늘 ${fmt(card.todayTotal)}$unit · " +
                    "직전 ${fmt(card.previousTotal)}$unit · PR ${card.prValue?.let { "${fmt(it)}$unit" } ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (card.isExpanded) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("", modifier = Modifier.width(24.dp))
                    // 시간 기반(유산소/플랭크)은 앞칸이 "강도"(속도·레벨·경사), 뒷칸이 "시간(초)".
                    Text(
                        if (isTime) "강도" else "중량(kg)",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        if (isTime) "시간(초)" else "횟수",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.width(64.dp))
                }

                card.sets.forEach { set ->
                    SetRow(
                        set = set,
                        isTime = isTime,
                        onChanged = { w, r -> onSetChanged(set, w, r) },
                        onToggle = { onSetToggle(set) },
                        onDelete = { onSetDelete(set) }
                    )
                }

                TextButton(onClick = onAddSet) {
                    Text("+ 세트추가")
                }

                Spacer(Modifier.height(6.dp))
                AppTextField(
                    value = memoText,
                    onValueChange = {
                        memoText = it
                        onMemoChanged(it)
                    },
                    label = { Text("메모") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showRemoveConfirm) {
        ConfirmDialog(
            title = "운동 삭제",
            message = "'${card.exercise.name}'을(를) 오늘 운동에서 삭제할까요? 입력한 세트도 함께 사라집니다.",
            onDismiss = { showRemoveConfirm = false },
            onConfirm = {
                showRemoveConfirm = false
                onRemove()
            }
        )
    }
}

/** 삭제처럼 되돌릴 수 없는 동작 전에 한 번 물어보는 공용 확인 팝업. */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("삭제") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/**
 * 부위 칩 묶음. Material3 FilterChip은 최소 높이가 32dp로 고정이고 여백도 넉넉해서
 * 부위 7~8개가 한 줄에 안 들어간다. 여기서는 Box+Text로 직접 그려 여백을 줄이고,
 * FlowRow로 자동 줄바꿈해서 두 줄이 되더라도 가로 스크롤이 필요 없게 만든다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyPartChipRow(
    parts: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        parts.forEach { part ->
            BodyPartChip(part = part, selected = part == selected, onClick = { onSelect(part) })
        }
    }
}

/** 부위 필터/선택 칩 하나. 선택되면 그 부위 고유색으로 칠해진다. "전체"는 색이 없으니 기본 색. */
@Composable
private fun BodyPartChip(part: String, selected: Boolean, onClick: () -> Unit) {
    val base = if (part == "전체") MaterialTheme.colorScheme.primary else bodyPartColor(part)
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .background(
                if (selected) base.copy(alpha = 0.22f) else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .border(1.dp, if (selected) base else outline, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            // FilterChip 기본(12dp/8dp)보다 훨씬 좁게.
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            part,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) base else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 목록 우측에 붙는 부위 배지. 부위 고유색을 옅게 깔고 글자는 진하게. */
@Composable
private fun BodyPartBadge(part: String) {
    val color = bodyPartColor(part)
    Text(
        part,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 목록 항목 사이를 나누는 옅은 가로선. */
@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/** 중량 −/+ 버튼 한 번에 움직이는 양(kg). 여기 숫자만 바꾸면 조절 폭이 바뀐다. */
private const val WEIGHT_STEP = 2.5

/** 시간(초) −/+ 버튼 한 번에 움직이는 양. 횟수는 항상 1씩. */
private const val TIME_STEP = 5

/** 유산소 강도(속도/레벨) −/+ 버튼 한 번에 움직이는 양. */
private const val INTENSITY_STEP = 0.5

/** 7) inputType이 TIME이면 중량 칸을 감추고 "시간(초)" 한 칸만 보여준다.
 *  6) 중량/횟수 입력칸은 숫자만 입력되도록 필터링한다.
 *  4) OutlinedTextField 기본 padding(top/bottom 각 8dp)보다 좁은 CompactNumberField를 사용해
 *     오늘 화면 입력칸의 상하 여백을 1dp씩 더 줄인다. */
@Composable
private fun SetRow(
    set: ExerciseSet,
    isTime: Boolean,
    onChanged: (weight: Double, reps: Int) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var weightText by remember(set.id) { mutableStateOf(trimNumber(set.weight)) }
    var repsText by remember(set.id) { mutableStateOf(set.reps.toString()) }

    fun pushWeight(text: String) {
        weightText = text
        onChanged(text.toDoubleOrNull() ?: 0.0, repsText.toIntOrNull() ?: set.reps)
    }

    fun pushReps(text: String) {
        repsText = text
        onChanged(weightText.toDoubleOrNull() ?: set.weight, text.toIntOrNull() ?: 0)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${set.setNumber}", modifier = Modifier.width(24.dp))

        // 시간 기반 운동도 앞칸을 쓴다 — 중량이 아니라 "강도"(러닝 속도, 사이클 레벨 등).
        StepperField(
            value = weightText,
            onValueChange = { pushWeight(it.filter { c -> c.isDigit() || c == '.' }) },
            onStep = { dir ->
                val step = if (isTime) INTENSITY_STEP else WEIGHT_STEP
                val next = ((weightText.toDoubleOrNull() ?: 0.0) + dir * step).coerceAtLeast(0.0)
                pushWeight(trimNumber(next))
            },
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f)
        )
        StepperField(
            value = repsText,
            onValueChange = { pushReps(it.filter { c -> c.isDigit() }) },
            onStep = { dir ->
                val step = if (isTime) TIME_STEP else 1
                val next = ((repsText.toIntOrNull() ?: 0) + dir * step).coerceAtLeast(0)
                pushReps(next.toString())
            },
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        IconToggleButton(
            checked = set.isCompleted,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(32.dp)
        ) {
            Text(if (set.isCompleted) "✓" else "○")
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "세트 삭제")
        }
    }
}

/**
 * 숫자 입력칸 + 좌우 −/+ 버튼. onStep의 인자는 방향(-1 또는 +1)이고
 * 실제 증감폭은 부르는 쪽(중량 2.5 / 횟수 1 / 시간 5)에서 정한다.
 */
@Composable
private fun StepperField(
    value: String,
    onValueChange: (String) -> Unit,
    onStep: (dir: Int) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton("−") { onStep(-1) }
        CompactNumberField(
            value = value,
            onValueChange = onValueChange,
            keyboardType = keyboardType,
            modifier = Modifier.weight(1f)
        )
        StepButton("+") { onStep(1) }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * OutlinedTextField 저수준 API(BasicTextField + DecorationBox)를 직접 써서
 * 기본 contentPadding(대략 top/bottom 8dp)보다 좁은 top/bottom 7dp로 그린다.
 * 숫자 입력용이라 keyboardType만 받는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 커서/선택 범위까지 다뤄야 "포커스 시 전체 선택"이 되므로 TextFieldValue로 들고 있는다.
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    // ± 버튼처럼 바깥에서 값이 바뀐 경우엔 텍스트만 갈아끼우고 커서는 끝으로 보낸다.
    if (field.text != value) {
        field = TextFieldValue(value, TextRange(value.length))
    }

    BasicTextField(
        value = field,
        onValueChange = {
            field = it
            if (it.text != value) onValueChange(it.text)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        ),
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.onFocusChanged {
            if (it.isFocused) {
                keyboardController?.show()
                // 지우지 않고 바로 새 숫자를 칠 수 있게 전체 선택 상태로 만든다.
                field = field.copy(selection = TextRange(0, field.text.length))
            }
        },
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                colors = colors,
                // 기본값(top/bottom 8dp)보다 1dp씩 더 줄인 값. 더 줄이려면 여기 숫자만 바꾸면 된다.
                // 좌우는 −/+ 버튼에 자리를 내주려고 좁게 잡는다.
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 7.dp)
            )
        }
    )
}

/**
 * OutlinedTextField를 그대로 감싸되, 포커스를 받는 순간 소프트 키보드를 명시적으로 띄운다.
 * Dialog(AlertDialog) 안의 텍스트필드는 포커스는 잡히는데 IME가 자동으로 안 뜨는 경우가 있어서
 * (Compose Dialog 창의 알려진 동작) 포커스 변화를 직접 감지해서 keyboard.show()를 호출한다.
 * 이 파일의 일반 텍스트 입력칸(검색/운동명/루틴이름/메모)은 전부 이 함수를 쓴다.
 */
@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = singleLine,
        modifier = modifier.onFocusChanged { if (it.isFocused) keyboardController?.show() }
    )
}

/** 팝업이 지금 어느 화면을 보여주고 있는지. 팝업(AlertDialog) 자체는 하나만 떠 있고 내용만 바뀐다. */
private enum class AddExerciseStep { MAIN, ADD_EXERCISE, CREATE_ROUTINE }

@Composable
private fun AddExercisePickerDialog(
    routineViewModel: RoutineViewModel,
    onDismiss: () -> Unit,
    onPickExercise: (Long) -> Unit,
    onPickRoutine: (Long) -> Unit,
    onRoutineCreated: (Long) -> Unit
) {
    val exercises by routineViewModel.allExercises.collectAsState()
    val routines by routineViewModel.routines.collectAsState()
    var step by remember { mutableStateOf(AddExerciseStep.MAIN) }
    var mainTab by remember { mutableStateOf(0) } // 0: 운동, 1: 루틴

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    AddExerciseStep.MAIN -> "운동추가"
                    AddExerciseStep.ADD_EXERCISE -> "새 운동 추가"
                    AddExerciseStep.CREATE_ROUTINE -> "새 루틴 만들기"
                }
            )
        },
        text = {
            Box(Modifier.heightIn(max = 420.dp)) {
                when (step) {
                    AddExerciseStep.MAIN -> MainPickerContent(
                        selectedTab = mainTab,
                        onTabChange = { mainTab = it },
                        exercises = exercises,
                        routines = routines,
                        onPickExercise = onPickExercise,
                        onPickRoutine = onPickRoutine,
                        onAddExerciseClick = { step = AddExerciseStep.ADD_EXERCISE },
                        onCreateRoutineClick = { step = AddExerciseStep.CREATE_ROUTINE },
                        onEditExercise = { ex, name, bodyPart, inputType ->
                            routineViewModel.updateExercise(ex, name, bodyPart, inputType)
                        },
                        onDeleteExercise = { ex -> routineViewModel.deleteExercise(ex) },
                        onEditRoutine = { r, name, ids -> routineViewModel.updateRoutine(r, name, ids) },
                        onDeleteRoutine = { r -> routineViewModel.deleteRoutine(r) },
                        exercisesForRoutine = { id -> routineViewModel.exercisesFor(id) }
                    )
                    AddExerciseStep.ADD_EXERCISE -> AddExerciseFormContent(
                        onConfirm = { name, bodyPart, inputType ->
                            routineViewModel.addExercise(name, bodyPart, inputType) { id ->
                                onPickExercise(id)
                            }
                        }
                    )
                    AddExerciseStep.CREATE_ROUTINE -> RoutineFormContent(
                        allExercises = exercises,
                        confirmLabel = "만들기",
                        onConfirm = { name, exerciseIds ->
                            routineViewModel.createRoutine(name, exerciseIds) { id ->
                                onRoutineCreated(id)
                                // 4) 만들고 나면 팝업을 닫지 않고 MAIN(운동추가) 화면으로 되돌아간다.
                                step = AddExerciseStep.MAIN
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            if (step != AddExerciseStep.MAIN) {
                TextButton(onClick = { step = AddExerciseStep.MAIN }) { Text("뒤로") }
            } else {
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        }
    )
}

/** 탭 메인화면: 운동 | 루틴 탭 + 선택한 탭에 따른 세부 목록. */
@Composable
private fun MainPickerContent(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    exercises: List<Exercise>,
    routines: List<WorkoutRoutine>,
    onPickExercise: (Long) -> Unit,
    onPickRoutine: (Long) -> Unit,
    onAddExerciseClick: () -> Unit,
    onCreateRoutineClick: () -> Unit,
    onEditExercise: (Exercise, String, String, ExerciseInputType) -> Unit,
    onDeleteExercise: (Exercise) -> Unit,
    onEditRoutine: (WorkoutRoutine, String, List<Long>) -> Unit,
    onDeleteRoutine: (WorkoutRoutine) -> Unit,
    exercisesForRoutine: (Long) -> Flow<List<Exercise>>
) {
    Column {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { onTabChange(0) }, text = { Text("운동") })
            Tab(selected = selectedTab == 1, onClick = { onTabChange(1) }, text = { Text("루틴") })
        }
        Spacer(Modifier.height(8.dp))
        if (selectedTab == 0) {
            ExerciseListContent(
                exercises = exercises,
                onPick = onPickExercise,
                onCreateNew = onAddExerciseClick,
                onEdit = onEditExercise,
                onDelete = onDeleteExercise
            )
        } else {
            RoutineListContent(
                routines = routines,
                allExercises = exercises,
                onPick = onPickRoutine,
                onCreateNew = onCreateRoutineClick,
                onEdit = onEditRoutine,
                onDelete = onDeleteRoutine,
                exercisesForRoutine = exercisesForRoutine
            )
        }
    }
}

/**
 * 운동 탭 세부 목록.
 * 2) 롱프레스 시 편집/삭제 메뉴, 3) 이름·부위·입력방식이 한 줄에, 목록은 고정 높이로 스크롤 가능.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseListContent(
    exercises: List<Exercise>,
    onPick: (Long) -> Unit,
    onCreateNew: () -> Unit,
    onEdit: (Exercise, String, String, ExerciseInputType) -> Unit,
    onDelete: (Exercise) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedBodyPart by remember { mutableStateOf("전체") }
    val bodyParts = remember(exercises) { listOf("전체") + exercises.map { it.bodyPart }.distinct() }
    var menuTargetId by remember { mutableStateOf<Long?>(null) }
    var editTarget by remember { mutableStateOf<Exercise?>(null) }
    var deleteTarget by remember { mutableStateOf<Exercise?>(null) }
    val listState = rememberLazyListState()

    val filtered = exercises.filter { ex ->
        (selectedBodyPart == "전체" || ex.bodyPart == selectedBodyPart) &&
            matchesSearch(ex.name, query)
    }

    Column {
        AppTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("검색 (초성 가능)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        BodyPartChipRow(
            parts = bodyParts,
            selected = selectedBodyPart,
            onSelect = { selectedBodyPart = it }
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) {
            Text("+ 새 운동 추가")
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 260.dp)
            ) {
                items(filtered, key = { it.id }) { ex ->
                    Column {
                        val inputLabel = if (ex.inputType == ExerciseInputType.TIME) "시간" else "중량/횟수"
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onPick(ex.id) },
                                        onLongClick = { menuTargetId = ex.id }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 이름은 좌측정렬로 남은 폭을 다 쓰고, 부위/입력방식은 우측정렬로 붙는다.
                                Text(
                                    ex.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                BodyPartBadge(ex.bodyPart)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    inputLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = menuTargetId == ex.id,
                                onDismissRequest = { menuTargetId = null }
                            ) {
                                DropdownMenuItem(text = { Text("편집") }, onClick = { editTarget = ex; menuTargetId = null })
                                DropdownMenuItem(text = { Text("삭제") }, onClick = { deleteTarget = ex; menuTargetId = null })
                            }
                        }
                        RowDivider()
                    }
                }
            }
            SimpleVerticalScrollbar(
                listState = listState,
                modifier = Modifier
                    .heightIn(max = 260.dp)
                    .padding(start = 2.dp)
            )
        }
    }

    editTarget?.let { ex ->
        ExerciseEditDialog(
            exercise = ex,
            onDismiss = { editTarget = null },
            onConfirm = { newName, bodyPart, inputType ->
                onEdit(ex, newName, bodyPart, inputType)
                editTarget = null
            }
        )
    }

    deleteTarget?.let { ex ->
        ConfirmDialog(
            title = "운동 삭제",
            message = "'${ex.name}'을(를) 운동 목록에서 삭제할까요?",
            onDismiss = { deleteTarget = null },
            onConfirm = { onDelete(ex); deleteTarget = null }
        )
    }
}
/** 오늘 구성을 루틴으로 저장할 때 이름만 받는 팝업. */
@Composable
private fun SaveRoutineDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("루틴으로 저장") },
        text = {
            Column {
                Text(
                    "지금 오늘 화면에 있는 운동들이 그 순서대로 새 루틴이 됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("루틴이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("저장")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/** "가슴·등 9/2"처럼 오늘 한 부위와 날짜로 기본 이름을 만들어 준다. */
private fun suggestRoutineName(state: TodayUiState): String {
    val parts = state.cards.map { it.exercise.bodyPart }.distinct().take(3)
    val date = state.date.format(DateTimeFormatter.ofPattern("M/d"))
    return if (parts.isEmpty()) date else "${parts.joinToString("·")} $date"
}
