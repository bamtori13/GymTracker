package com.gymtracker.app.data.local.dao

import androidx.room.*
import com.gymtracker.app.data.local.entity.WorkoutSession

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
}
