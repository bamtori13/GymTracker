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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** 달력 아래에 한 줄로 보여줄 부위 요약. */
data class BodyPartSummary(
    val bodyPart: String,
    /** 이번 달에 이 부위를 한 날 수. */
    val sessionCount: Int,
    /** 이번 달 총 중량(무게×횟수 합). */
    val totalVolume: Double,
    /** 이번 달 총 시간(초). 시간 기반 운동이 있을 때만 0보다 크다. */
    val totalSeconds: Int,
    /** 마지막으로 이 부위를 한 뒤 지난 날 수. 기록이 아예 없으면 null. */
    val daysSinceLast: Int?
)

/**
 * 달력 탭 상태. 보고 있는 "월"이 바뀌면 그 달 범위만 다시 조회한다.
 * 결과는 날짜 -> 부위 목록으로 접어서 화면이 그대로 색을 칠할 수 있게 만든다.
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
                    .mapValues { (_, parts) -> parts.distinct().sortedBy { orderOf(it) } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val summaries: StateFlow<List<BodyPartSummary>> = _month
        .flatMapLatest { ym ->
            val stats = repository.observeBodyPartMonthStats(
                ym.atDay(1).toEpochDay(),
                ym.atEndOfMonth().toEpochDay()
            )
            combine(stats, repository.observeBodyPartLastDays()) { monthStats, lastDays ->
                val today = LocalDate.now()
                val lastByPart = lastDays.associate { it.bodyPart to it.lastEpochDay }
                // 이번 달에 안 한 부위도 "며칠 쉬었는지"를 보려면 목록에 있어야 하므로 둘을 합집합으로.
                (monthStats.map { it.bodyPart } + lastByPart.keys).distinct()
                    .map { part ->
                        val stat = monthStats.firstOrNull { it.bodyPart == part }
                        BodyPartSummary(
                            bodyPart = part,
                            sessionCount = stat?.dayCount ?: 0,
                            totalVolume = stat?.totalVolume ?: 0.0,
                            totalSeconds = stat?.totalSeconds ?: 0,
                            daysSinceLast = lastByPart[part]?.let {
                                ChronoUnit.DAYS.between(LocalDate.ofEpochDay(it), today).toInt()
                            }
                        )
                    }
                    .sortedBy { orderOf(it.bodyPart) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun goToPreviousMonth() { _month.value = _month.value.minusMonths(1) }
    fun goToNextMonth() { _month.value = _month.value.plusMonths(1) }
    fun goToCurrentMonth() { _month.value = YearMonth.now() }
    fun goToMonth(target: YearMonth) { _month.value = target }

    /** 점/글씨 순서를 항상 같게 만들어서 날짜마다 색 순서가 흔들리지 않게 한다. */
    private fun orderOf(bodyPart: String): Int =
        DefaultExercises.BODY_PARTS.indexOf(bodyPart).let { if (it < 0) Int.MAX_VALUE else it }
}
