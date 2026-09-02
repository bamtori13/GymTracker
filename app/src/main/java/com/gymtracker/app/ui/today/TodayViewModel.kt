package com.gymtracker.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.local.entity.Exercise
import com.gymtracker.app.data.local.entity.ExerciseInputType
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
    /** 오늘계획총량: 체크 여부와 상관없이 지금 이 화면에 "입력해 둔" 모든 세트의 합계. */
    val planTotal: Double,
    /** 오늘수행총량: 완료(✓) 체크된 세트만의 합계. */
    val todayTotal: Double,
    /** 직전수행총량: 이 운동이 포함된 가장 최근 이전 날짜 세션의 완료된 세트 합계. */
    val previousTotal: Double,
    /** PR: WEIGHT_REPS면 역대 최고 중량, TIME이면 역대 최장 시간(초). 기록 없으면 null. */
    val prValue: Double?,
    val isExpanded: Boolean = false
)

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val sessionId: Long? = null,
    val cards: List<ExerciseCardUiState> = emptyList(),
    val isPeriod: Boolean = false,
    val isLoading: Boolean = true
)

class TodayViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    // 펼침/접힘은 화면 상태일 뿐 DB에 저장하지 않는다. sessionExerciseId -> expanded
    // 기본값은 "접힘"(false) — 오늘 화면에 추가된 운동은 처음엔 접혀서 보인다.
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
        val current = expandedState[sessionExerciseId] ?: false
        expandedState[sessionExerciseId] = !current
        _uiState.value = _uiState.value.copy(
            cards = _uiState.value.cards.map {
                if (it.sessionExercise.id == sessionExerciseId) it.copy(isExpanded = !current) else it
            }
        )
    }

    /** 운동을 추가하면 직전에 이 운동을 한 날의 세트/메모를 그대로 끌어온다. */
    fun addExerciseToToday(exerciseId: Long) {
        val sessionId = _uiState.value.sessionId ?: return
        val epochDay = _uiState.value.date.toEpochDay()
        viewModelScope.launch {
            repository.addExerciseCopyingPrevious(sessionId, exerciseId, epochDay)
            refreshCards()
        }
    }

    fun addRoutineToToday(routineId: Long) {
        val sessionId = _uiState.value.sessionId ?: return
        val epochDay = _uiState.value.date.toEpochDay()
        viewModelScope.launch {
            repository.getExercisesForRoutine(routineId).forEach { exercise ->
                repository.addExerciseCopyingPrevious(sessionId, exercise.id, epochDay)
            }
            refreshCards()
        }
    }

    /** 오늘 화면에 올려둔 운동들을 그 순서대로 새 루틴으로 저장한다. */
    fun saveTodayAsRoutine(name: String, onSaved: () -> Unit = {}) {
        val exerciseIds = _uiState.value.cards.map { it.exercise.id }
        if (name.isBlank() || exerciseIds.isEmpty()) return
        viewModelScope.launch {
            repository.createRoutine(name.trim(), exerciseIds)
            onSaved()
        }
    }

    fun togglePeriod() {
        val epochDay = _uiState.value.date.toEpochDay()
        viewModelScope.launch {
            repository.togglePeriod(epochDay)
            _uiState.value = _uiState.value.copy(isPeriod = repository.isPeriod(epochDay))
        }
    }

    /**
     * 드래그로 카드를 옮긴다. 화면에는 즉시 반영하고(옮기는 중 끌림 없이 보이도록),
     * DB에는 새 순서를 sortOrder로 다시 매겨 저장한다.
     */
    fun moveCard(fromIndex: Int, toIndex: Int) {
        val cards = _uiState.value.cards
        if (fromIndex !in cards.indices || toIndex !in cards.indices || fromIndex == toIndex) return
        val reordered = cards.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        _uiState.value = _uiState.value.copy(cards = reordered)
        viewModelScope.launch {
            repository.reorderSessionExercises(reordered.map { it.sessionExercise })
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
        val isTime = card.exercise.inputType == ExerciseInputType.TIME
        val nextNumber = (card.sets.maxOfOrNull { it.setNumber } ?: 0) + 1
        val lastSet = card.sets.lastOrNull()
        // 시간 기반 운동에서는 weight 칸이 "강도"(속도/레벨)로 쓰이므로 직전 세트 값을 그대로 이어받는다.
        val defaultWeight = lastSet?.weight ?: if (isTime) 0.0 else card.exercise.currentTargetWeight
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
        val cards = entries.mapNotNull { entry ->
            val exercise = repository.getExercise(entry.exerciseId) ?: return@mapNotNull null
            val sets = repository.getSetsForExerciseInSession(exercise.id, sessionId)
            val isTime = exercise.inputType == ExerciseInputType.TIME

            fun valueOf(s: ExerciseSet): Double = if (isTime) s.reps.toDouble() else s.weight * s.reps

            // 체크 여부와 상관없이, 지금 입력해 둔 모든 세트 값의 합 = "오늘계획총량".
            val planTotal = sets.sumOf(::valueOf)
            // 완료 체크된 세트만 = "오늘수행총량".
            val todayTotal = sets.filter { it.isCompleted }.sumOf(::valueOf)

            val prevSession = repository.getLastSessionWithExerciseBefore(exercise.id, date.toEpochDay())
            val previousTotal = if (prevSession != null) {
                repository.getSetsForExerciseInSession(exercise.id, prevSession.id)
                    .filter { it.isCompleted }
                    .sumOf(::valueOf)
            } else 0.0

            val prValue = if (isTime) {
                repository.getMaxRepsEver(exercise.id)?.toDouble()
            } else {
                repository.getMaxWeightEver(exercise.id)
            }

            ExerciseCardUiState(
                sessionExercise = entry,
                exercise = exercise,
                sets = sets,
                planTotal = planTotal,
                todayTotal = todayTotal,
                previousTotal = previousTotal,
                prValue = prValue,
                isExpanded = expandedState[entry.id] ?: false
            )
        }

        _uiState.value = _uiState.value.copy(
            cards = cards,
            isPeriod = repository.isPeriod(date.toEpochDay()),
            isLoading = false
        )
    }
}
