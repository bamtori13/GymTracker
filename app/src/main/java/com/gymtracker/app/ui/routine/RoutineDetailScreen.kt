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
 * 설정 > 루틴 상세: 루틴에 담긴 운동을 확인만 한다.
 * 루틴 생성/운동 구성은 오늘 화면의 "+운동추가" 팝업(새 루틴 만들기)에서 한다.
 */
@Composable
fun RoutineDetailScreen(
    routineId: Long,
    viewModel: RoutineViewModel
) {
    val exercises by viewModel.exercisesFor(routineId).collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("${exercises.size}개 운동", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))

        if (exercises.isEmpty()) {
            Text("운동이 없습니다.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(exercises, key = { it.id }) { exercise ->
                    ExerciseRow(exercise)
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(exercise.bodyPart, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
