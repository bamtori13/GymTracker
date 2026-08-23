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
 * 설정 > 루틴 목록. 루틴 생성은 오늘 화면 "+운동추가 > 루틴 탭 > 새 루틴 만들기"에서만 한다
 * (운동을 체크박스로 골라야 하므로 이름만 입력하는 방식은 더 이상 지원하지 않는다).
 */
@Composable
fun HomeScreen(
    viewModel: RoutineViewModel,
    onRoutineClick: (Long) -> Unit
) {
    val routines by viewModel.routines.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("내 루틴", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (routines.isEmpty()) {
            Text("아직 루틴이 없습니다. 오늘 화면의 +운동추가 > 루틴 탭에서 만들어보세요.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(routines, key = { it.id }) { routine ->
                    RoutineCard(routine = routine, onClick = { onRoutineClick(routine.id) })
                }
            }
        }
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
