package com.gymtracker.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymtracker.app.data.local.entity.WorkoutRoutine
import com.gymtracker.app.ui.routine.RoutineViewModel

/**
 * 홈 화면: "오늘 뭘 해야 하지?"에 답한다.
 * Phase 1에서는 루틴 목록을 보여주고, 선택하면 상세(운동 목록 + 시작 버튼)로 이동한다.
 */
@Composable
fun HomeScreen(
    viewModel: RoutineViewModel,
    onRoutineClick: (Long) -> Unit
) {
    val routines by viewModel.routines.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("내 루틴", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (routines.isEmpty()) {
                Text("아직 루틴이 없습니다. + 버튼으로 루틴을 만들어보세요.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(routines, key = { it.id }) { routine ->
                        RoutineCard(routine = routine, onClick = { onRoutineClick(routine.id) })
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRoutineDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createRoutine(name) {}
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun RoutineCard(routine: WorkoutRoutine, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(routine.name, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun CreateRoutineDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 루틴") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("루틴 이름 (예: A Day - 상체)") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("만들기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
