package com.gymtracker.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.local.entity.Exercise
import com.gymtracker.app.data.local.entity.ExerciseSet
import com.gymtracker.app.data.local.entity.SessionExercise
import com.gymtracker.app.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 화면에 그려질 운동 카드 하나 (SessionExercise + Exercise + 세트 + 요약치). */
data class ExerciseCardUiState(
    val sessionExercise: SessionExercise,
    val exercise: Exercise,
    val sets: List<ExerciseSet>,
    /** 오늘계획총량: 목표 중량 x (최소~최대 반복 평균) x 목표 세트 수. */
    val planTotal: Double,
    /** 오늘수행총량: 오늘 완료 표시된 세트들의 중량x횟수 합. */
    val todayTotal: Double,
    /** 직전수행총량: 이 운동이 포함된 가장 최근 이전 날짜 세션의 중량x횟수 합. */
    val previousTotal: Double,
    /** PR: 이 운동의 역대 최고 중량(완료된 세트 기준). 기록 없으면 null. */
    val prWeight: Double?,
    val isExpanded: Boolean = true
)

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val sessionId: Long? = null,
    val cards: List<ExerciseCardUiState> = emptyList(),
    val isLoading: Boolean = true
)

class TodayViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    // 펼침/접힘은 화면 상태일 뿐 DB에 저장하지 않는다. sessionExerciseId -> expanded
    private val expandedState = mutableMapOf<Long, Boolean>()

    init {
        loadDate(LocalDate.now())
    }

    fun loadDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(date = date, isLoading = true)
        viewModelScope.launch {
            val sessionId = repository.getOrCreateSessionForDate(date.toEpochDay())
            _uiState.value = _uiState.value.copy(sessionId = sessionId)
            refreshCards()
        }
    }

    fun goToPreviousDay() = loadDate(_uiState.value.date.minusDays(1))
    fun goToNextDay() = loadDate(_uiState.value.date.plusDays(1))
    fun goToToday() = loadDate(LocalDate.now())

    fun toggleExpand(sessionExerciseId: Long) {
        val current = expandedState[sessionExerciseId] ?: true
        expandedState[sessionExerciseId] = !current
        _uiState.value = _uiState.value.copy(
            cards = _uiState.value.cards.map {
                if (it.sessionExercise.id == sessionExerciseId) it.copy(isExpanded = !current) else it
            }
        )
    }

    fun addExerciseToToday(exerciseId: Long) {
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            repository.addExerciseToSession(sessionId, exerciseId)
            refreshCards()
        }
    }

    fun addRoutineToToday(routineId: Long) {
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            repository.addRoutineToSession(sessionId, routineId)
            refreshCards()
        }
    }

    fun removeCard(card: ExerciseCardUiState) {
        viewModelScope.launch {
            repository.removeExerciseFromSession(card.sessionExercise)
            refreshCards()
        }
    }

    fun updateMemo(card: ExerciseCardUiState, memo: String) {
        viewModelScope.launch {
            repository.updateSessionExerciseMemo(card.sessionExercise, memo)
            refreshCards()
        }
    }

    /** +세트추가: 다음 세트 번호로, 직전 세트(또는 지난 기록/목표)를 기본값 삼아 빈 세트를 만든다. */
    fun addSet(card: ExerciseCardUiState) {
        val sessionId = _uiState.value.sessionId ?: return
        val nextNumber = (card.sets.maxOfOrNull { it.setNumber } ?: 0) + 1
        val lastSet = card.sets.lastOrNull()
        val defaultWeight = lastSet?.weight ?: card.exercise.currentTargetWeight
        val defaultReps = lastSet?.reps ?: card.exercise.minReps
        viewModelScope.launch {
            repository.saveSet(
                ExerciseSet(
                    sessionId = sessionId,
                    exerciseId = card.exercise.id,
                    setNumber = nextNumber,
                    weight = defaultWeight,
                    reps = defaultReps,
                    isCompleted = false
                )
            )
            refreshCards()
        }
    }

    fun updateSet(set: ExerciseSet, weight: Double, reps: Int) {
        viewModelScope.launch {
            repository.saveSet(set.copy(weight = weight, reps = reps))
            refreshCards()
        }
    }

    fun toggleSetCompleted(set: ExerciseSet) {
        viewModelScope.launch {
            repository.saveSet(set.copy(isCompleted = !set.isCompleted))
            refreshCards()
        }
    }

    fun deleteSet(set: ExerciseSet) {
        viewModelScope.launch {
            repository.deleteSet(set)
            refreshCards()
        }
    }

    private suspend fun refreshCards() {
        val date = _uiState.value.date
        val sessionId = _uiState.value.sessionId ?: run {
            _uiState.value = _uiState.value.copy(isLoading = false, cards = emptyList())
            return
        }
        val entries = repository.observeSessionExercises(sessionId).first()
        val cards = entries.map { entry ->
            val exercise = repository.getExercise(entry.exerciseId)
                ?: return@map null
            val sets = repository.getSetsForExerciseInSession(exercise.id, sessionId)
            val todayTotal = sets.filter { it.isCompleted }.sumOf { it.weight * it.reps }
            val avgReps = (exercise.minReps + exercise.maxReps) / 2.0
            val planTotal = exercise.currentTargetWeight * avgReps * exercise.targetSets

            val prevSession = repository.getLastSessionWithExerciseBefore(exercise.id, date.toEpochDay())
            val previousTotal = if (prevSession != null) {
                repository.getSetsForExerciseInSession(exercise.id, prevSession.id)
                    .filter { it.isCompleted }
                    .sumOf { it.weight * it.reps }
            } else 0.0

            val prWeight = repository.getMaxWeightEver(exercise.id)

            ExerciseCardUiState(
                sessionExercise = entry,
                exercise = exercise,
                sets = sets,
                planTotal = planTotal,
                todayTotal = todayTotal,
                previousTotal = previousTotal,
                prWeight = prWeight,
                isExpanded = expandedState[entry.id] ?: true
            )
        }.filterNotNull()

        _uiState.value = _uiState.value.copy(cards = cards, isLoading = false)
    }
}
