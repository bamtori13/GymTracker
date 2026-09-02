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
