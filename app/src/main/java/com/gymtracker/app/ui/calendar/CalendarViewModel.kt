package com.gymtracker.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.local.DefaultExercises
import com.gymtracker.app.data.repository.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/**
 * 달력 탭 상태. 보고 있는 "월"이 바뀌면 그 달 범위만 다시 조회한다.
 * 결과는 날짜 -> 부위 목록으로 접어서 화면이 그대로 점을 찍을 수 있게 만든다.
 */
class CalendarViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val bodyPartsByDate: StateFlow<Map<LocalDate, List<String>>> = _month
        .flatMapLatest { ym ->
            // 달력 그리드는 앞뒤 달을 물고 있으므로 여유 있게 ±7일 넓혀서 가져온다.
            val from = ym.atDay(1).minusDays(7).toEpochDay()
            val to = ym.atEndOfMonth().plusDays(7).toEpochDay()
            repository.observeBodyPartsInRange(from, to).map { rows ->
                rows.groupBy({ LocalDate.ofEpochDay(it.dateEpochDay) }, { it.bodyPart })
                    .mapValues { (_, parts) -> parts.sortedBy { orderOf(it) } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun goToPreviousMonth() { _month.value = _month.value.minusMonths(1) }
    fun goToNextMonth() { _month.value = _month.value.plusMonths(1) }
    fun goToCurrentMonth() { _month.value = YearMonth.now() }

    /** 점 순서를 항상 같게 만들어서 날짜마다 색 순서가 흔들리지 않게 한다. */
    private fun orderOf(bodyPart: String): Int =
        DefaultExercises.BODY_PARTS.indexOf(bodyPart).let { if (it < 0) Int.MAX_VALUE else it }
}
