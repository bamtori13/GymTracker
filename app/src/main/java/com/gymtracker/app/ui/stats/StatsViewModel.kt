package com.gymtracker.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.local.dao.SetHistoryRow
import com.gymtracker.app.data.local.entity.ExerciseInputType
import com.gymtracker.app.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 한 날짜에 한 운동을 수행한 결과 요약. 그래프의 점 하나. */
data class SessionPoint(
    val date: LocalDate,
    /** 총량. 중량운동이면 Σ(무게×횟수), 시간운동이면 Σ(초). */
    val volume: Double,
    /** 그날의 최고 추정 1RM. 시간운동이면 그날 최장 시간(초). */
    val best: Double,
    val topWeight: Double,
    val setCount: Int
)

/** 점진적 과부하 판정. */
enum class OverloadVerdict { PROGRESS, HOLD, REGRESS, NOT_ENOUGH_DATA }

/** 운동 하나의 추세 분석 결과. */
data class ExerciseTrend(
    val exerciseId: Long,
    val name: String,
    val bodyPart: String,
    val isTime: Boolean,
    val points: List<SessionPoint>,
    /** 추정 1RM의 주당 변화량 (선형회귀 기울기 × 7일). */
    val perWeekChange: Double,
    /** 최근 4주 볼륨 대비 그 앞 4주 볼륨의 변화율(%). 비교할 데이터가 없으면 null. */
    val volumeChangePercent: Double?,
    val pr: Double,
    val verdict: OverloadVerdict
) {
    val unit: String get() = if (isTime) "초" else "kg"
}

/** 한 주의 총 볼륨 + 부위별 분해. 막대그래프 한 칸. */
data class WeekVolume(
    val weekStart: LocalDate,
    val volume: Double,
    val byBodyPart: Map<String, Double>
)

data class StatsUiState(
    val weeks: List<WeekVolume> = emptyList(),
    val trends: List<ExerciseTrend> = emptyList(),
    val selected: ExerciseTrend? = null,
    val workoutDays: Int = 0,
    val isEmpty: Boolean = true
)

/** 그래프에 보여줄 주 수. */
private const val WEEKS_SHOWN = 12

class StatsViewModel(
    repository: WorkoutRepository
) : ViewModel() {

    private val selectedId = MutableStateFlow<Long?>(null)

    // 주간 볼륨과 운동별 추세를 각각 따로 collect하면 두 번 조회되고 화면이 반쪽 상태로
    // 렌더될 수 있으니, 한 번 읽은 rows에서 둘 다 만든다.
    val uiState: StateFlow<StatsUiState> =
        combine(repository.observeCompletedSetHistory(), selectedId) { rows, picked ->
            val trends = rows.groupBy { it.exerciseId }.values
                .map(::analyze)
                // 최근에 한 운동이 위로.
                .sortedByDescending { it.points.lastOrNull()?.date ?: LocalDate.MIN }
            StatsUiState(
                weeks = toWeeklyVolume(rows),
                trends = trends,
                // 아무것도 안 골랐으면 가장 최근에 한 운동을 보여준다.
                selected = trends.firstOrNull { it.exerciseId == picked } ?: trends.firstOrNull(),
                workoutDays = rows.map { it.dateEpochDay }.distinct().size,
                isEmpty = trends.isEmpty()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    fun selectExercise(exerciseId: Long) { selectedId.value = exerciseId }

    // --- 집계 ---

    /** 한 운동의 완료 세트 전부를 날짜별로 접어서 추세를 뽑는다. */
    private fun analyze(rows: List<SetHistoryRow>): ExerciseTrend {
        val first = rows.first()
        val isTime = first.inputType == ExerciseInputType.TIME

        val points = rows.groupBy { it.dateEpochDay }
            .map { (epochDay, daySets) ->
                SessionPoint(
                    date = LocalDate.ofEpochDay(epochDay),
                    volume = daySets.sumOf { if (isTime) it.reps.toDouble() else it.weight * it.reps },
                    best = daySets.maxOf { if (isTime) it.reps.toDouble() else estimateOneRepMax(it.weight, it.reps) },
                    topWeight = daySets.maxOf { it.weight },
                    setCount = daySets.size
                )
            }
            .sortedBy { it.date }

        val perWeekChange = weeklySlope(points)
        return ExerciseTrend(
            exerciseId = first.exerciseId,
            name = first.exerciseName,
            bodyPart = first.bodyPart,
            isTime = isTime,
            points = points,
            perWeekChange = perWeekChange,
            volumeChangePercent = recentVolumeChangePercent(points),
            pr = points.maxOfOrNull { it.best } ?: 0.0,
            verdict = verdictOf(points, perWeekChange)
        )
    }

    /**
     * Epley 공식으로 추정 1RM. 무게와 횟수를 하나의 "힘" 숫자로 합치기 때문에
     * "무게는 그대로인데 횟수가 늘었다" 같은 진전도 잡아낸다 — 점진적 과부하 판정의 핵심 지표.
     */
    private fun estimateOneRepMax(weight: Double, reps: Int): Double =
        if (reps <= 0) 0.0 else weight * (1 + reps / 30.0)

    /** (경과일, 추정1RM) 최소제곱 회귀 기울기를 주 단위로 환산. */
    private fun weeklySlope(points: List<SessionPoint>): Double {
        if (points.size < 2) return 0.0
        val base = points.first().date
        val xs = points.map { ChronoUnit.DAYS.between(base, it.date).toDouble() }
        val ys = points.map { it.best }
        val meanX = xs.average()
        val meanY = ys.average()
        val denominator = xs.sumOf { (it - meanX) * (it - meanX) }
        if (denominator == 0.0) return 0.0
        val numerator = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) }
        return numerator / denominator * 7.0
    }

    /** 최근 4주 볼륨 합 vs 그 앞 4주 볼륨 합. */
    private fun recentVolumeChangePercent(points: List<SessionPoint>): Double? {
        val last = points.lastOrNull()?.date ?: return null
        val recentFrom = last.minusWeeks(4)
        val priorFrom = last.minusWeeks(8)
        val recent = points.filter { it.date > recentFrom }.sumOf { it.volume }
        val prior = points.filter { it.date > priorFrom && it.date <= recentFrom }.sumOf { it.volume }
        if (prior <= 0.0) return null
        return (recent - prior) / prior * 100.0
    }

    /**
     * 판정 기준: 주당 추정1RM 변화가 "시작 값의 0.5%" 또는 최소 0.4단위를 넘으면 진전/후퇴.
     * 절대값만 쓰면 무거운 운동(스쿼트)은 항상 진전, 가벼운 운동(레터럴레이즈)은 항상 정체로 보인다.
     */
    private fun verdictOf(points: List<SessionPoint>, perWeekChange: Double): OverloadVerdict {
        if (points.size < 3) return OverloadVerdict.NOT_ENOUGH_DATA
        val threshold = maxOf(0.4, points.first().best * 0.005)
        return when {
            perWeekChange > threshold -> OverloadVerdict.PROGRESS
            perWeekChange < -threshold -> OverloadVerdict.REGRESS
            else -> OverloadVerdict.HOLD
        }
    }

    /** 전체 운동을 주 단위(월요일 시작)로 묶어서 최근 WEEKS_SHOWN주만. 빈 주도 0으로 채운다. */
    private fun toWeeklyVolume(rows: List<SetHistoryRow>): List<WeekVolume> {
        if (rows.isEmpty()) return emptyList()
        val byWeek = rows.groupBy { weekStartOf(LocalDate.ofEpochDay(it.dateEpochDay)) }
        val lastWeek = weekStartOf(LocalDate.now())
        return (WEEKS_SHOWN - 1 downTo 0).map { back ->
            val weekStart = lastWeek.minusWeeks(back.toLong())
            val weekRows = byWeek[weekStart].orEmpty()
            WeekVolume(
                weekStart = weekStart,
                volume = weekRows.sumOf { volumeOf(it) },
                byBodyPart = weekRows.groupBy { it.bodyPart }
                    .mapValues { (_, list) -> list.sumOf { volumeOf(it) } }
            )
        }
    }

    /** 시간운동의 초를 중량운동의 kg×회와 그냥 더하면 단위가 섞이니, 초는 1/10로 눌러서 합친다. */
    private fun volumeOf(row: SetHistoryRow): Double =
        if (row.inputType == ExerciseInputType.TIME) row.reps / 10.0 else row.weight * row.reps

    private fun weekStartOf(date: LocalDate): LocalDate =
        date.minusDays(((date.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7).toLong())
}
