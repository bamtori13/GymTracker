
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

/** 루틴 탭 세부 목록: 검색 + 새 루틴 만들기 + 루틴 목록(롱프레스 시 편집/삭제). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineListContent(
    routines: List<WorkoutRoutine>,
    allExercises: List<Exercise>,
    onPick: (Long) -> Unit,
    onCreateNew: () -> Unit,
    onEdit: (WorkoutRoutine, String, List<Long>) -> Unit,
    onDelete: (WorkoutRoutine) -> Unit,
    exercisesForRoutine: (Long) -> Flow<List<Exercise>>
) {
    var query by remember { mutableStateOf("") }
    var menuTargetId by remember { mutableStateOf<Long?>(null) }
    var editTarget by remember { mutableStateOf<WorkoutRoutine?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkoutRoutine?>(null) }
    val filtered = routines.filter { matchesSearch(it.name, query) }
    val listState = rememberLazyListState()

    Column {
        AppTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("검색 (초성 가능)") },
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
                    // 루틴에 든 운동 이름을 두번째 줄에 요약해서 보여준다.
                    // Flow는 remember로 붙들어야 한다 — 매 recomposition마다 새로 만들면
                    // collectAsState가 initial(빈 목록)로 되돌아가면서 재구성이 무한 반복된다.
                    val memberFlow = remember(routine.id) { exercisesForRoutine(routine.id) }
                    val members by memberFlow.collectAsState(initial = emptyList())
                    Column {
                        Box {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onPick(routine.id) },
                                        onLongClick = { menuTargetId = routine.id }
                                    )
                                    // 루틴은 항목 수가 적으니 위아래로 넉넉하게 띄운다.
                                    .padding(horizontal = 12.dp, vertical = 14.dp)
                            ) {
                                Text(routine.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (members.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        members.joinToString(" · ") { it.name },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = menuTargetId == routine.id,
                                onDismissRequest = { menuTargetId = null }
                            ) {
                                DropdownMenuItem(text = { Text("편집") }, onClick = { editTarget = routine; menuTargetId = null })
                                DropdownMenuItem(text = { Text("삭제") }, onClick = { deleteTarget = routine; menuTargetId = null })
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

    editTarget?.let { routine ->
        val memberFlow = remember(routine.id) { exercisesForRoutine(routine.id) }
        val members by memberFlow.collectAsState(initial = emptyList())
        // 5) 루틴 편집도 이름만이 아니라 포함 운동까지 함께 고칠 수 있게 만들기 화면을 그대로 재사용한다.
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("루틴 편집") },
            text = {
                Box(Modifier.heightIn(max = 420.dp)) {
                    RoutineFormContent(
                        allExercises = allExercises,
                        initialName = routine.name,
                        initialCheckedIds = members.map { it.id },
                        confirmLabel = "저장",
                        onConfirm = { name, ids -> onEdit(routine, name, ids); editTarget = null }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { editTarget = null }) { Text("닫기") } }
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
                BodyPartChipRow(
                    parts = bodyParts,
                    selected = bodyPart,
                    onSelect = { bodyPart = it }
                )
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
        BodyPartChipRow(
            parts = DefaultExercises.BODY_PARTS,
            selected = bodyPart,
            onSelect = { bodyPart = it }
        )
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
private fun RoutineFormContent(
    allExercises: List<Exercise>,
    confirmLabel: String,
    initialName: String = "",
    initialCheckedIds: List<Long> = emptyList(),
    onConfirm: (name: String, exerciseIds: List<Long>) -> Unit
) {
    var routineName by remember(initialName) { mutableStateOf(initialName) }
    var query by remember { mutableStateOf("") }
    var selectedBodyPart by remember { mutableStateOf("전체") }
    val bodyParts = remember(allExercises) { listOf("전체") + allExercises.map { it.bodyPart }.distinct() }
    val checked = remember(initialCheckedIds) { mutableStateListOf(*initialCheckedIds.toTypedArray()) }
    val checklistState = rememberLazyListState()
    // 편집 진입 시점의 선택 목록. 이걸로만 정렬해서, 편집 중 체크를 껐다 켤 때
    // 목록 순서가 튀지 않게 한다(현재 checked로 정렬하면 누를 때마다 항목이 이동한다).
    val initiallyChecked = remember(initialCheckedIds) { initialCheckedIds.toSet() }

    val filtered = allExercises
        .filter { ex ->
            (selectedBodyPart == "전체" || ex.bodyPart == selectedBodyPart) &&
                matchesSearch(ex.name, query)
        }
        // 기존에 루틴에 들어 있던 운동을 맨 위로.
        .sortedByDescending { it.id in initiallyChecked }

    fun toggle(id: Long) {
        if (checked.contains(id)) checked.remove(id) else checked.add(id)
    }

    // heightIn(max)만 걸고 목록에 고정 높이를 주면 내용 합이 최대치를 넘어 "만들기" 버튼이
    // 팝업 밖으로 잘려 나간다. 높이를 채운 Column에서 목록만 weight로 늘려 버튼을 항상 아래에 붙인다.
    Column(Modifier.fillMaxHeight()) {
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
            label = { Text("운동검색 (초성 가능)") },
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = checklistState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                items(filtered, key = { it.id }) { ex ->
                    val isChecked = checked.contains(ex.id)
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggle(ex.id) }
                                .padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // onCheckedChange = null → 체크박스는 표시 전용, 실제 토글은 Row의 clickable 하나로만 처리.
                            Checkbox(checked = isChecked, onCheckedChange = null)
                            Text(
                                ex.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            BodyPartBadge(ex.bodyPart)
                        }
                        RowDivider()
                    }
                }
            }
            SimpleVerticalScrollbar(
                listState = checklistState,
                modifier = Modifier
                    .fillMaxHeight()
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
            Text("$confirmLabel (${checked.size}개 운동)")
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
