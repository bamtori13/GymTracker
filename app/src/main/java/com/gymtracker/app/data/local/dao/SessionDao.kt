package com.gymtracker.app.data.local.dao

import androidx.room.*
import com.gymtracker.app.data.local.entity.WorkoutSession
import kotlinx.coroutines.flow.Flow

/** 달력에 색 점을 찍기 위한 projection: "이 날짜에 이 부위 운동이 있었다" 한 줄. */
data class DayBodyPart(val dateEpochDay: Long, val bodyPart: String)

/** 달력 아래 부위별 월간 요약 한 줄. */
data class BodyPartMonthStat(
    val bodyPart: String,
    /** 이 달에 이 부위를 한 날 수 = 시행횟수. */
    val dayCount: Int,
    /** 완료 세트의 무게×횟수 합 (중량 운동만). */
    val totalVolume: Double,
    /** 완료 세트의 시간 합 (시간 기반 운동만, 초). */
    val totalSeconds: Int
)

/** 부위별 "마지막으로 한 날" (기간 제한 없음). 경과일 계산용. */
data class BodyPartLastDay(val bodyPart: String, val lastEpochDay: Long)

@Dao
interface SessionDao {

    @Query("SELECT * FROM workout_session WHERE dateEpochDay = :dateEpochDay LIMIT 1")
    suspend fun getByDate(dateEpochDay: Long): WorkoutSession?

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    /** 오늘(=dateEpochDay) 이전 날짜 중 이 운동이 포함된 가장 최근 세션. "직전수행총량" 계산에 사용. */
    @Query(
        "SELECT s.* FROM workout_session s " +
            "INNER JOIN session_exercise se ON se.sessionId = s.id " +
            "WHERE se.exerciseId = :exerciseId AND s.dateEpochDay < :beforeEpochDay " +
            "ORDER BY s.dateEpochDay DESC LIMIT 1"
    )
    suspend fun getLastSessionWithExerciseBefore(exerciseId: Long, beforeEpochDay: Long): WorkoutSession?

    /**
     * 달력용: 기간 안에서 "운동이 실제로 들어간" 날짜와 그 날의 부위들.
     * session_exercise를 INNER JOIN하므로 날짜만 넘겨보고 만들어진 빈 세션은 걸러진다.
     */
    @Query(
        "SELECT DISTINCT s.dateEpochDay AS dateEpochDay, e.bodyPart AS bodyPart " +
            "FROM workout_session s " +
            "INNER JOIN session_exercise se ON se.sessionId = s.id " +
            "INNER JOIN exercise e ON e.id = se.exerciseId " +
            "WHERE s.dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay"
    )
    fun observeBodyPartsInRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<DayBodyPart>>

    /**
     * 부위별 월간 요약. session_exercise 기준으로 "한 날"을 세므로 달력의 색 표시와 개수가 일치한다.
     * 총량은 완료 체크된 세트만 더하고, 중량/시간을 섞지 않도록 inputType으로 갈라서 각각 합친다.
     */
    @Query(
        "SELECT e.bodyPart AS bodyPart, " +
            "COUNT(DISTINCT s.dateEpochDay) AS dayCount, " +
            "COALESCE(SUM(CASE WHEN es.isCompleted = 1 AND e.inputType = 'WEIGHT_REPS' " +
            "THEN es.weight * es.reps ELSE 0 END), 0) AS totalVolume, " +
            "COALESCE(SUM(CASE WHEN es.isCompleted = 1 AND e.inputType = 'TIME' " +
            "THEN es.reps ELSE 0 END), 0) AS totalSeconds " +
            "FROM session_exercise se " +
            "INNER JOIN workout_session s ON s.id = se.sessionId " +
            "INNER JOIN exercise e ON e.id = se.exerciseId " +
            "LEFT JOIN exercise_set es ON es.sessionId = se.sessionId AND es.exerciseId = se.exerciseId " +
            "WHERE s.dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay " +
            "GROUP BY e.bodyPart"
    )
    fun observeBodyPartMonthStats(fromEpochDay: Long, toEpochDay: Long): Flow<List<BodyPartMonthStat>>

    @Query(
        "SELECT e.bodyPart AS bodyPart, MAX(s.dateEpochDay) AS lastEpochDay " +
            "FROM session_exercise se " +
            "INNER JOIN workout_session s ON s.id = se.sessionId " +
            "INNER JOIN exercise e ON e.id = se.exerciseId " +
            "GROUP BY e.bodyPart"
    )
    fun observeBodyPartLastDays(): Flow<List<BodyPartLastDay>>

    // --- export/import ---
    @Query("SELECT * FROM workout_session")
    suspend fun getAll(): List<WorkoutSession>

    @Query("DELETE FROM workout_session")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(items: List<WorkoutSession>)
}
