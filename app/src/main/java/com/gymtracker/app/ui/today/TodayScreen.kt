package com.gymtracker.app.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.gymtracker.app.data.local.DefaultExercises
import com.gymtracker.app.data.local.entity.Exercise
import com.gymtracker.app.data.local.entity.ExerciseInputType
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

/** 팝업이 지금 어느 화면을 보여주고 있는지. 팝업(AlertDialog) 자체는 하나만 떠 있고 내용만 바뀐다. */
private enum class AddExerciseStep { MAIN, ADD_EXERCISE, CREATE_ROUTINE }

@Composable
private fun AddExercisePickerDialog(
    routineViewModel: RoutineViewModel,
    onDismiss: () -> Unit,
    onPickExercise: (Long) -> Unit,
    onPickRoutine: (Long) -> Unit
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
                        onCreateRoutineClick = { step = AddExerciseStep.CREATE_ROUTINE }
                    )
                    AddExerciseStep.ADD_EXERCISE -> AddExerciseFormContent(
                        onConfirm = { name, bodyPart, inputType ->
                            routineViewModel.addExercise(name, bodyPart, inputType) { id ->
                                onPickExercise(id)
                            }
                        }
                    )
                    AddExerciseStep.CREATE_ROUTINE -> CreateRoutineFormContent(
                        allExercises = exercises,
                        onConfirm = { name, exerciseIds ->
                            routineViewModel.createRoutine(name, exerciseIds) { id ->
                                onPickRoutine(id)
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
    onCreateRoutineClick: () -> Unit
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
                onCreateNew = onAddExerciseClick
            )
        } else {
            RoutineListContent(
                routines = routines,
                onPick = onPickRoutine,
                onCreateNew = onCreateRoutineClick
            )
        }
    }
}

/** 운동 탭 세부 목록: 검색 + 부위 칩 + 새 운동 추가 + 목록(이름/부위/입력형태). */
@Composable
private fun ExerciseListContent(
    exercises: List<Exercise>,
    onPick: (Long) -> Unit,
    onCreateNew: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedBodyPart by remember { mutableStateOf("전체") }
    val bodyParts = remember(exercises) { listOf("전체") + exercises.map { it.bodyPart }.distinct() }

    val filtered = exercises.filter { ex ->
        (selectedBodyPart == "전체" || ex.bodyPart == selectedBodyPart) &&
            (query.isBlank() || ex.name.contains(query, ignoreCase = true))
    }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("검색") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bodyParts.forEach { part ->
                FilterChip(
                    selected = selectedBodyPart == part,
                    onClick = { selectedBodyPart = part },
                    label = { Text(part) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) {
            Text("+ 새 운동 추가")
        }
        LazyColumn {
            items(filtered, key = { it.id }) { ex ->
                ListItem(
                    headlineContent = { Text(ex.name) },
                    supportingContent = {
                        val inputLabel = if (ex.inputType == ExerciseInputType.TIME) "시간" else "중량/횟수"
                        Text("${ex.bodyPart} · $inputLabel")
                    },
                    modifier = Modifier.clickable { onPick(ex.id) }
                )
            }
        }
    }
}

/** 루틴 탭 세부 목록: 검색 + 새 루틴 만들기 + 루틴 목록. */
@Composable
private fun RoutineListContent(
    routines: List<WorkoutRoutine>,
    onPick: (Long) -> Unit,
    onCreateNew: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = routines.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("검색") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) {
            Text("+ 새 루틴 만들기")
        }
        LazyColumn {
            items(filtered, key = { it.id }) { routine ->
                ListItem(
                    headlineContent = { Text(routine.name) },
                    modifier = Modifier.clickable { onPick(routine.id) }
                )
            }
        }
    }
}

/** 운동 추가 화면: 운동명 / 부위 선택 / 입력방법 선택(중량x횟수 / 시간). */
@Composable
private fun AddExerciseFormContent(
    onConfirm: (name: String, bodyPart: String, inputType: ExerciseInputType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var bodyPart by remember { mutableStateOf(DefaultExercises.BODY_PARTS.first()) }
    var inputType by remember { mutableStateOf(ExerciseInputType.WEIGHT_REPS) }

    Column {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("운동명") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text("부위", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DefaultExercises.BODY_PARTS.forEach { part ->
                FilterChip(
                    selected = bodyPart == part,
                    onClick = { bodyPart = part },
                    label = { Text(part) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("입력방법", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = inputType == ExerciseInputType.WEIGHT_REPS,
                onClick = { inputType = ExerciseInputType.WEIGHT_REPS },
                label = { Text("중량 x 횟수") }
            )
            FilterChip(
                selected = inputType == ExerciseInputType.TIME,
                onClick = { inputType = ExerciseInputType.TIME },
                label = { Text("시간") }
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onConfirm(name.trim(), bodyPart, inputType) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("추가")
        }
    }
}

/** 새 루틴 만들기 화면: 루틴이름 / 운동검색 / 부위선택 / 체크박스 운동목록. */
@Composable
private fun CreateRoutineFormContent(
    allExercises: List<Exercise>,
    onConfirm: (name: String, exerciseIds: List<Long>) -> Unit
) {
    var routineName by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var selectedBodyPart by remember { mutableStateOf("전체") }
    val bodyParts = remember(allExercises) { listOf("전체") + allExercises.map { it.bodyPart }.distinct() }
    val checked = remember { mutableStateListOf<Long>() }

    val filtered = allExercises.filter { ex ->
        (selectedBodyPart == "전체" || ex.bodyPart == selectedBodyPart) &&
            (query.isBlank() || ex.name.contains(query, ignoreCase = true))
    }

    Column {
        OutlinedTextField(
            value = routineName,
            onValueChange = { routineName = it },
            label = { Text("루틴이름") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("운동검색") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bodyParts.forEach { part ->
                FilterChip(
                    selected = selectedBodyPart == part,
                    onClick = { selectedBodyPart = part },
                    label = { Text(part) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
            items(filtered, key = { it.id }) { ex ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (checked.contains(ex.id)) checked.remove(ex.id) else checked.add(ex.id)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked.contains(ex.id),
                        onCheckedChange = {
                            if (it) checked.add(ex.id) else checked.remove(ex.id)
                        }
                    )
                    Text("${ex.name} (${ex.bodyPart})")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onConfirm(routineName.trim(), checked.toList()) },
            enabled = routineName.isNotBlank() && checked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("만들기 (${checked.size}개 운동)")
        }
    }
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
