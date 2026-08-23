package com.gymtracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gymtracker.app.data.local.AppDatabase
import com.gymtracker.app.data.repository.WorkoutRepository
import com.gymtracker.app.ui.ViewModelFactory
import com.gymtracker.app.ui.calendar.CalendarScreen
import com.gymtracker.app.ui.routine.RoutineViewModel
import com.gymtracker.app.ui.settings.SettingsScreen
import com.gymtracker.app.ui.stats.StatsScreen
import com.gymtracker.app.ui.theme.AppTypography
import com.gymtracker.app.ui.today.TodayScreen
import com.gymtracker.app.ui.today.TodayViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(applicationContext)
        val repository = WorkoutRepository(db)
        val factory = ViewModelFactory(repository)

        lifecycleScope.launch { repository.seedDefaultExercisesIfNeeded() }

        setContent {
            MaterialTheme(typography = AppTypography) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GymTrackerApp(factory = factory)
                }
            }
        }
    }
}

private enum class BottomTab(val route: String, val label: String) {
    TODAY("tab/today", "오늘"),
    CALENDAR("tab/calendar", "달력"),
    STATS("tab/stats", "통계"),
    SETTINGS("tab/settings", "설정")
}

@Composable
fun GymTrackerApp(factory: ViewModelFactory) {
    val navController: NavHostController = rememberNavController()
    // 여러 탭에서 공유하는 ViewModel들 (Activity scope에서 한 번만 생성).
    val routineViewModel: RoutineViewModel = viewModel(factory = factory)
    val todayViewModel: TodayViewModel = viewModel(factory = factory)

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination

            NavigationBar {
                listOf(
                    Triple(BottomTab.TODAY, Icons.Filled.Today, BottomTab.TODAY.label),
                    Triple(BottomTab.CALENDAR, Icons.Filled.CalendarMonth, BottomTab.CALENDAR.label),
                    Triple(BottomTab.STATS, Icons.Filled.ShowChart, BottomTab.STATS.label),
                    Triple(BottomTab.SETTINGS, Icons.Filled.Settings, BottomTab.SETTINGS.label)
                ).forEach { (tab, icon, label) ->
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = BottomTab.TODAY.route,
            modifier = Modifier.Companion
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(BottomTab.TODAY.route) {
                TodayScreen(todayViewModel = todayViewModel, routineViewModel = routineViewModel)
            }
            composable(BottomTab.CALENDAR.route) {
                CalendarScreen(
                    onDatePicked = { date ->
                        todayViewModel.loadDate(date)
                        navController.navigate(BottomTab.TODAY.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(BottomTab.STATS.route) {
                StatsScreen()
            }
            composable(BottomTab.SETTINGS.route) {
                SettingsScreen(routineViewModel = routineViewModel)
            }
        }
    }
}
