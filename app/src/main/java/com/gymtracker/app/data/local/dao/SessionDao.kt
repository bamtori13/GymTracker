package com.gymtracker.app.data.local.dao

import androidx.room.*
import com.gymtracker.app.data.local.entity.WorkoutSession
import kotlinx.coroutines.flow.Flow

/** 달력에 색 점을 찍기 위한 projection: "이 날짜에 이 부위 운동이 있었다" 한 줄. */
data class DayBodyPart(val dateEpochDay: Long, val bodyPart: String)

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
}
