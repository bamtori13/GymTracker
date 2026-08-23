package com.gymtracker.app.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.local.entity.Exercise
import com.gymtracker.app.data.local.entity.ExerciseInputType
import com.gymtracker.app.data.local.entity.WorkoutRoutine
import com.gymtracker.app.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutineViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    val routines: StateFlow<List<WorkoutRoutine>> = repository.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExercises: StateFlow<List<Exercise>> = repository.observeAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun exercisesFor(routineId: Long) = repository.observeExercisesForRoutine(routineId)

    /** "+새 운동 추가" 화면 확인 버튼. */
    fun addExercise(
        name: String,
        bodyPart: String,
        inputType: ExerciseInputType,
        onCreated: (exerciseId: Long) -> Unit
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.addExercise(name.trim(), bodyPart, inputType)
            onCreated(id)
        }
    }

    /** "새 루틴 만들기" 화면 확인 버튼. 체크박스로 고른 운동 id들을 그대로 담는다. */
    fun createRoutine(name: String, exerciseIds: List<Long>, onCreated: (routineId: Long) -> Unit) {
        if (name.isBlank() || exerciseIds.isEmpty()) return
        viewModelScope.launch {
            val id = repository.createRoutine(name.trim(), exerciseIds)
            onCreated(id)
        }
    }
}
