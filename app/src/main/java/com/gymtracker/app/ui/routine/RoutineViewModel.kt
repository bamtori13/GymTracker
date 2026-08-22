package com.gymtracker.app.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.local.entity.Exercise
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

    fun createRoutine(name: String, onCreated: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createRoutine(name.trim())
            onCreated(id)
        }
    }

    fun addExercise(
        routineId: Long,
        name: String,
        sets: Int,
        minReps: Int,
        maxReps: Int,
        weightIncrement: Double,
        startWeight: Double,
        sortOrder: Int
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addExercise(
                Exercise(
                    routineId = routineId,
                    name = name.trim(),
                    sortOrder = sortOrder,
                    targetSets = sets,
                    minReps = minReps,
                    maxReps = maxReps,
                    weightIncrement = weightIncrement,
                    currentTargetWeight = startWeight
                )
            )
        }
    }

    fun exercisesFor(routineId: Long) = repository.observeExercises(routineId)

    /** Today 화면의 "새 운동 만들기"에서 사용. 별도 루틴 선택 없이 기본 루틴에 바로 추가한다. */
    fun quickAddExercise(
        name: String,
        sets: Int,
        minReps: Int,
        maxReps: Int,
        weightIncrement: Double,
        startWeight: Double,
        onCreated: (exerciseId: Long) -> Unit
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val routineId = repository.getOrCreateDefaultRoutine()
            val id = repository.addExercise(
                Exercise(
                    routineId = routineId,
                    name = name.trim(),
                    sortOrder = 0,
                    targetSets = sets,
                    minReps = minReps,
                    maxReps = maxReps,
                    weightIncrement = weightIncrement,
                    currentTargetWeight = startWeight
                )
            )
            onCreated(id)
        }
    }
}
