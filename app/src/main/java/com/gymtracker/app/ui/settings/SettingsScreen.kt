package com.gymtracker.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gymtracker.app.ui.home.HomeScreen
import com.gymtracker.app.ui.routine.RoutineDetailScreen
import com.gymtracker.app.ui.routine.RoutineViewModel

private object SettingsRoutes {
    const val ROUTINE_LIST = "settings/routines"
    const val ROUTINE_DETAIL = "settings/routines/{routineId}"
    fun routineDetail(id: Long) = "settings/routines/$id"
}

/**
 * 설정 탭: 지금은 "루틴 관리"(운동 카탈로그 편집)만 제공한다.
 * 오늘 화면의 +운동추가에서 고르는 운동/루틴 목록이 바로 여기서 만든 것이다.
 */
@Composable
fun SettingsScreen(routineViewModel: RoutineViewModel) {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = SettingsRoutes.ROUTINE_LIST) {
        composable(SettingsRoutes.ROUTINE_LIST) {
            HomeScreen(
                viewModel = routineViewModel,
                onRoutineClick = { id -> navController.navigate(SettingsRoutes.routineDetail(id)) }
            )
        }
        composable(SettingsRoutes.ROUTINE_DETAIL) {
            val routineId = navController.currentBackStackEntry
                ?.arguments?.getString("routineId")?.toLongOrNull() ?: 0L
            RoutineDetailScreen(routineId = routineId, viewModel = routineViewModel)
        }
    }
}
