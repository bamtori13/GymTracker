package com.gymtracker.app.ui.routine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymtracker.app.data.local.entity.Exercise

/**
 * 루틴 상세 화면(설정 > 루틴 관리): 루틴에 속한 운동 목록(템플릿)을 관리한다.
 * 실제로 "오늘 이 루틴을 한다"는 오늘 화면의 +운동추가 > 루틴 탭에서 선택한다.
 */
@Composable
fun RoutineDetailScreen(
    routineId: Long,
    viewModel: RoutineViewModel
) {
    val exercises by viewModel.exercisesFor(routineId).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Text("+") }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("${exercises.size}개 운동", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))

            if (exercises.isEmpty()) {
                Text("+ 버튼으로 운동을 추가하세요 (예: Squat, Bench Press).")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(exercises, key = { it.id }) { exercise ->
                        ExerciseRow(exercise)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddExerciseDialog(
            nextOrder = exercises.size,
            onDismiss = { showAddDialog = false },
            onAdd = { name, sets, minReps, maxReps, increment, startWeight, order ->
                viewModel.addExercise(routineId, name, sets, minReps, maxReps, increment, startWeight, order)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "목표 ${exercise.currentTargetWeight}kg × ${exercise.minReps}~${exercise.maxReps}, " +
                    "${exercise.targetSets}세트",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AddExerciseDialog(
    nextOrder: Int,
    onDismiss: () -> Unit,
    onAdd: (name: String, sets: Int, minReps: Int, maxReps: Int, increment: Double, startWeight: Double, order: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("4") }
    var minReps by remember { mutableStateOf("8") }
    var maxReps by remember { mutableStateOf("10") }
    var increment by remember { mutableStateOf("2.5") }
    var startWeight by remember { mutableStateOf("20") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("운동 추가") },
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
                    onAdd(
                        name,
                        sets.toIntOrNull() ?: 4,
                        minReps.toIntOrNull() ?: 8,
                        maxReps.toIntOrNull() ?: 10,
                        increment.toDoubleOrNull() ?: 2.5,
                        startWeight.toDoubleOrNull() ?: 20.0,
                        nextOrder
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
