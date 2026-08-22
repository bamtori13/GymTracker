package com.gymtracker.app.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gymtracker.app.data.local.entity.Exercise
import com.gymtracker.app.data.local.entity.ExerciseSet
import com.gymtracker.app.data.local.entity.WorkoutRoutine
import com.gymtracker.app.ui.routine.RoutineViewModel
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(state.cards, key = { it.sessionExercise.id }) { card ->
                        ExerciseCard(
                            card = card,
                            onToggleExpand = { todayViewModel.toggleExpand(card.sessionExercise.id) },
                            onRemove = { todayViewModel.removeCard(card) },
                            onAddSet = { todayViewModel.addSet(card) },
                            onSetChanged = { set, weight, reps -> todayViewModel.updateSet(set, weight, reps) },
                            onSetToggle = { set -> todayViewModel.toggleSetCompleted(set) },
                            onSetDelete = { set -> todayViewModel.deleteSet(set) },
                            onMemoChanged = { memo -> todayViewModel.updateMemo(card, memo) }
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = { showAddExercise = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ 운동추가")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
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

@Composable
private fun ExerciseCard(
    card: ExerciseCardUiState,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onAddSet: () -> Unit,
    onSetChanged: (ExerciseSet, Double, Int) -> Unit,
    onSetToggle: (ExerciseSet) -> Unit,
    onSetDelete: (ExerciseSet) -> Unit,
    onMemoChanged: (String) -> Unit
) {
    var memoText by remember(card.sessionExercise.id, card.sessionExercise.memo) {
        mutableStateOf(card.sessionExercise.memo)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            // 헤더: :: (핸들) ▼/▶ 운동이름 ... 삭제
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
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
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "운동 제거")
                }
            }

            if (card.isExpanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "계획 ${fmt(card.planTotal)} · 오늘 ${fmt(card.todayTotal)} · " +
                        "직전 ${fmt(card.previousTotal)} · PR ${card.prWeight?.let { "${fmt(it)}kg" } ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("", modifier = Modifier.width(28.dp))
                    Text("중량(kg)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("횟수", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(72.dp))
                }

                card.sets.forEach { set ->
                    SetRow(
                        set = set,
                        onChanged = { w, r -> onSetChanged(set, w, r) },
                        onToggle = { onSetToggle(set) },
                        onDelete = { onSetDelete(set) }
                    )
                }

                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = memoText,
                    onValueChange = {
                        memoText = it
                        onMemoChanged(it)
                    },
                    label = { Text("메모") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAddSet) {
                    Text("+ 세트추가")
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    set: ExerciseSet,
    onChanged: (weight: Double, reps: Int) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var weightText by remember(set.id) { mutableStateOf(trimNumber(set.weight)) }
    var repsText by remember(set.id) { mutableStateOf(set.reps.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${set.setNumber}", modifier = Modifier.width(28.dp))
        OutlinedTextField(
            value = weightText,
            onValueChange = {
                weightText = it
                it.toDoubleOrNull()?.let { w -> onChanged(w, repsText.toIntOrNull() ?: set.reps) }
            },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        )
        OutlinedTextField(
            value = repsText,
            onValueChange = {
                repsText = it
                it.toIntOrNull()?.let { r -> onChanged(weightText.toDoubleOrNull() ?: set.weight, r) }
            },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        )
        IconToggleButton(checked = set.isCompleted, onCheckedChange = { onToggle() }) {
            Text(if (set.isCompleted) "✓" else "○")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "세트 삭제")
        }
    }
}

@Composable
private fun AddExercisePickerDialog(
    routineViewModel: RoutineViewModel,
    onDismiss: () -> Unit,
    onPickExercise: (Long) -> Unit,
    onPickRoutine: (Long) -> Unit
) {
    val exercises by routineViewModel.allExercises.collectAsState()
    val routines by routineViewModel.routines.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var showQuickAdd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("운동추가") },
        text = {
            Column {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("운동") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("루틴") })
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.heightIn(max = 320.dp)) {
                    if (tab == 0) {
                        ExercisePickList(
                            exercises = exercises,
                            onPick = onPickExercise,
                            onCreateNew = { showQuickAdd = true }
                        )
                    } else {
                        RoutinePickList(routines = routines, onPick = onPickRoutine)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )

    if (showQuickAdd) {
        QuickAddExerciseDialog(
            onDismiss = { showQuickAdd = false },
            onCreate = { name, sets, minReps, maxReps, increment, startWeight ->
                routineViewModel.quickAddExercise(name, sets, minReps, maxReps, increment, startWeight) { id ->
                    onPickExercise(id)
                }
                showQuickAdd = false
            }
        )
    }
}

@Composable
private fun ExercisePickList(
    exercises: List<Exercise>,
    onPick: (Long) -> Unit,
    onCreateNew: () -> Unit
) {
    LazyColumn {
        items(exercises, key = { it.id }) { ex ->
            ListItem(
                headlineContent = { Text(ex.name) },
                supportingContent = { Text("${ex.targetSets}세트 · ${ex.minReps}~${ex.maxReps}회") },
                modifier = Modifier.clickable { onPick(ex.id) }
            )
        }
        item {
            TextButton(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) {
                Text("+ 새 운동 만들기")
            }
        }
    }
}

@Composable
private fun RoutinePickList(routines: List<WorkoutRoutine>, onPick: (Long) -> Unit) {
    LazyColumn {
        items(routines, key = { it.id }) { routine ->
            ListItem(
                headlineContent = { Text(routine.name) },
                modifier = Modifier.clickable { onPick(routine.id) }
            )
        }
    }
}

@Composable
private fun QuickAddExerciseDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, sets: Int, minReps: Int, maxReps: Int, increment: Double, startWeight: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("4") }
    var minReps by remember { mutableStateOf("8") }
    var maxReps by remember { mutableStateOf("10") }
    var increment by remember { mutableStateOf("2.5") }
    var startWeight by remember { mutableStateOf("20") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 운동 만들기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("운동 이름") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sets, onValueChange = { sets = it }, label = { Text("세트 수") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = startWeight, onValueChange = { startWeight = it }, label = { Text("시작 중량(kg)") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = minReps, onValueChange = { minReps = it }, label = { Text("최소 반복") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = maxReps, onValueChange = { maxReps = it }, label = { Text("최대 반복") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = increment, onValueChange = { increment = it }, label = { Text("증량 단위(kg)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        name,
                        sets.toIntOrNull() ?: 4,
                        minReps.toIntOrNull() ?: 8,
                        maxReps.toIntOrNull() ?: 10,
                        increment.toDoubleOrNull() ?: 2.5,
                        startWeight.toDoubleOrNull() ?: 20.0
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarPickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val initialMillis = initialDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    val picked = java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    onPick(picked)
                } else {
                    onDismiss()
                }
            }) { Text("선택") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    ) {
        DatePicker(state = state)
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

private fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
