package com.gymtracker.app.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
    var showRemoveConfirm by remember { mutableStateOf(false) }
    val isTime = card.exercise.inputType == ExerciseInputType.TIME
    val unit = if (isTime) "초" else "kg"

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
                IconButton(onClick = { showRemoveConfirm = true }) {
                    Icon(Icons.Filled.Close, contentDescription = "운동 제거")
                }
            }

            if (card.isExpanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "계획 ${fmt(card.planTotal)}$unit · 오늘 ${fmt(card.todayTotal)}$unit · " +
                        "직전 ${fmt(card.previousTotal)}$unit · PR ${card.prValue?.let { "${fmt(it)}$unit" } ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("", modifier = Modifier.width(28.dp))
                    if (isTime) {
                        Text("시간(초)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text("중량(kg)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        Text("횟수", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(72.dp))
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${set.setNumber}", modifier = Modifier.width(28.dp))

        if (!isTime) {
            CompactNumberField(
                value = weightText,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '.' }
                    weightText = filtered
                    filtered.toDoubleOrNull()?.let { w -> onChanged(w, repsText.toIntOrNull() ?: set.reps) }
                },
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            )
        }
        CompactNumberField(
            value = repsText,
            onValueChange = { input ->
                val filtered = input.filter { it.isDigit() }
                repsText = filtered
                filtered.toIntOrNull()?.let { r ->
                    onChanged(if (isTime) 0.0 else (weightText.toDoubleOrNull() ?: set.weight), r)
                }
            },
            keyboardType = KeyboardType.Number,
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

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.onFocusChanged { if (it.isFocused) keyboardController?.show() },
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
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
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
                        onRenameRoutine = { r, name -> routineViewModel.renameRoutine(r, name) },
                        onDeleteRoutine = { r -> routineViewModel.deleteRoutine(r) }
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
    onRenameRoutine: (WorkoutRoutine, String) -> Unit,
    onDeleteRoutine: (WorkoutRoutine) -> Unit
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
                onPick = onPickRoutine,
                onCreateNew = onCreateRoutineClick,
                onRename = onRenameRoutine,
                onDelete = onDeleteRoutine
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
            (query.isBlank() || ex.name.contains(query, ignoreCase = true))
    }

    Column {
        AppTextField(
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
        Row(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 260.dp)
            ) {
                items(filtered, key = { it.id }) { ex ->
                    Box {
                        val inputLabel = if (ex.inputType == ExerciseInputType.TIME) "시간" else "중량/횟수"
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
                            Text(
                                "${ex.name} · ${ex.bodyPart} · $inputLabel",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
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

/** 루틴 탭 세부 목록: 검색 + 새 루틴 만들기 + 루틴 목록(롱프레스 시 편집/삭제). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineListContent(
    routines: List<WorkoutRoutine>,
    onPick: (Long) -> Unit,
    onCreateNew: () -> Unit,
    onRename: (WorkoutRoutine, String) -> Unit,
    onDelete: (WorkoutRoutine) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var menuTargetId by remember { mutableStateOf<Long?>(null) }
    var editTarget by remember { mutableStateOf<WorkoutRoutine?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkoutRoutine?>(null) }
    val filtered = routines.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    val listState = rememberLazyListState()

    Column {
        AppTextField(
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
        Row(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 260.dp)
            ) {
                items(filtered, key = { it.id }) { routine ->
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onPick(routine.id) },
                                    onLongClick = { menuTargetId = routine.id }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(routine.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(
                            expanded = menuTargetId == routine.id,
                            onDismissRequest = { menuTargetId = null }
                        ) {
                            DropdownMenuItem(text = { Text("편집") }, onClick = { editTarget = routine; menuTargetId = null })
                            DropdownMenuItem(text = { Text("삭제") }, onClick = { deleteTarget = routine; menuTargetId = null })
                        }
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

    editTarget?.let { routine ->
        RenameDialog(
            initialName = routine.name,
            title = "루틴 이름 편집",
            onDismiss = { editTarget = null },
            onConfirm = { newName -> onRename(routine, newName); editTarget = null }
        )
    }

    deleteTarget?.let { routine ->
        ConfirmDialog(
            title = "루틴 삭제",
            message = "'${routine.name}' 루틴을 삭제할까요?",
            onDismiss = { deleteTarget = null },
            onConfirm = { onDelete(routine); deleteTarget = null }
        )
    }
}

@Composable
private fun RenameDialog(
    initialName: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/**
 * 2) 운동 롱프레스 > 편집: 이름뿐 아니라 부위 / 입력방법(중량x횟수, 시간)도 함께 편집한다.
 * AddExerciseFormContent와 같은 구성이지만 초기값을 기존 운동 값으로 채워서 보여준다.
 */
@Composable
private fun ExerciseEditDialog(
    exercise: Exercise,
    onDismiss: () -> Unit,
    onConfirm: (name: String, bodyPart: String, inputType: ExerciseInputType) -> Unit
) {
    var name by remember { mutableStateOf(exercise.name) }
    var bodyPart by remember { mutableStateOf(exercise.bodyPart) }
    var inputType by remember { mutableStateOf(exercise.inputType) }
    val bodyParts = remember { (DefaultExercises.BODY_PARTS + exercise.bodyPart).distinct() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("운동 편집") },
        text = {
            Column {
                AppTextField(
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
                    bodyParts.forEach { part ->
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), bodyPart, inputType) },
                enabled = name.isNotBlank()
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
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
        AppTextField(
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

/**
 * 새 루틴 만들기 화면: 루틴이름 / 운동검색 / 부위선택 / 체크박스 운동목록.
 * 3) 버그 수정: Row의 clickable과 Checkbox의 onCheckedChange가 동시에 토글을 실행해서
 *    탭 위치에 따라 두 번 토글(선택→즉시 해제)되던 문제. Checkbox는 표시 전용(onCheckedChange = null)으로
 *    바꾸고, 토글은 Row의 clickable 한 곳에서만 처리한다.
 */
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
    val checklistState = rememberLazyListState()

    val filtered = allExercises.filter { ex ->
        (selectedBodyPart == "전체" || ex.bodyPart == selectedBodyPart) &&
            (query.isBlank() || ex.name.contains(query, ignoreCase = true))
    }

    fun toggle(id: Long) {
        if (checked.contains(id)) checked.remove(id) else checked.add(id)
    }

    Column {
        AppTextField(
            value = routineName,
            onValueChange = { routineName = it },
            label = { Text("루틴이름") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        AppTextField(
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
        Row(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = checklistState,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 220.dp)
            ) {
                items(filtered, key = { it.id }) { ex ->
                    val isChecked = checked.contains(ex.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggle(ex.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // onCheckedChange = null → 체크박스는 표시 전용, 실제 토글은 Row의 clickable 하나로만 처리.
                        Checkbox(checked = isChecked, onCheckedChange = null)
                        Text("${ex.name} (${ex.bodyPart})")
                    }
                }
            }
            SimpleVerticalScrollbar(
                listState = checklistState,
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .padding(start = 2.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        val canCreate = routineName.isNotBlank() && checked.isNotEmpty()
        Button(
            onClick = { onConfirm(routineName.trim(), checked.toList()) },
            enabled = canCreate,
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

/**
 * 3) LazyColumn 옆에 붙여서 스크롤 위치/비율을 얇은 막대로 보여준다.
 * Compose(Material3)에는 Android용 기본 스크롤바 컴포넌트가 없어서 직접 그린다.
 * 목록이 한 화면에 다 들어오면(스크롤 필요 없으면) 아무것도 그리지 않는다.
 */
@Composable
private fun SimpleVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleCount = layoutInfo.visibleItemsInfo.size
    if (totalItems == 0 || visibleCount >= totalItems) return

    val thumbFraction = (visibleCount.toFloat() / totalItems).coerceIn(0.08f, 1f)
    val topFraction = (listState.firstVisibleItemIndex.toFloat() / totalItems).coerceIn(0f, 1f - thumbFraction)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(4.dp)
    ) {
        val trackHeight = maxHeight
        Box(
            modifier = Modifier
                .offset(y = trackHeight * topFraction)
                .height(trackHeight * thumbFraction)
                .width(4.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
        )
    }
}
