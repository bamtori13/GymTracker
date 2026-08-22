package com.gymtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gymtracker.app.data.repository.WorkoutRepository
import com.gymtracker.app.ui.routine.RoutineViewModel
import com.gymtracker.app.ui.today.TodayViewModel

/**
 * DI 프레임워크(Hilt 등) 없이 Repository를 ViewModel에 주입하기 위한 최소 Factory.
 * 프로젝트가 커지면 Hilt로 교체 가능하도록 이 클래스만 격리해둔다.
 */
class ViewModelFactory(private val repository: WorkoutRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            RoutineViewModel::class.java -> RoutineViewModel(repository) as T
            TodayViewModel::class.java -> TodayViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }
}
