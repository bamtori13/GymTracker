package com.gymtracker.app.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import com.gymtracker.app.ui.theme.bodyPartColor
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DateHeader(
                date = state.date,
                onPrevious = { todayViewModel.goToPreviousDay() },
                onNext = { todayViewModel.goToNextDay() },
                onDateClick = { showCalendar = true },
                onTodayClick = { todayViewModel.goToToday() }
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
                    onMemoChanged = { card, memo -> todayViewModel.updateMemo(card, memo) }
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
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDateClick: () -> Unit,
    onTodayClick: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("M/d") }
    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
    onMemoChanged: (ExerciseCardUiState, String) -> Unit
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
            Spacer(Modifier.height(8.dp))
        }
    }
}

  
