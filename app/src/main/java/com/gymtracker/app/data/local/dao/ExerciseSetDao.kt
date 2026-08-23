package com.gymtracker.app.data.local.dao

import androidx.room.*
import com.gymtracker.app.data.local.entity.ExerciseSet
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseSetDao {

    @Query("SELECT * FROM exercise_set WHERE sessionId = :sessionId AND exerciseId = :exerciseId ORDER BY setNumber ASC")
    fun observeForExerciseInSession(sessionId: Long, exerciseId: Long): Flow<List<ExerciseSet>>

    @Query("SELECT * FROM exercise_set WHERE sessionId = :sessionId ORDER BY setNumber ASC")
    suspend fun getAllForSession(sessionId: Long): List<ExerciseSet>

    /** 특정 운동의 가장 최근 세션(=지난번) 세트 기록. "지난번" 표시에 사용. */
    @Query(
        "SELECT * FROM exercise_set WHERE exerciseId = :exerciseId AND sessionId = :sessionId ORDER BY setNumber ASC"
    )
    suspend fun getSetsForExerciseInSession(exerciseId: Long, sessionId: Long): List<ExerciseSet>

    @Upsert
    suspend fun upsert(set: ExerciseSet): Long

    @Delete
    suspend fun delete(set: ExerciseSet)

    /** 이 운동 전체 기간 중 완료된 세트의 최고 중량 (간단한 PR 표시용). */
    @Query("SELECT MAX(weight) FROM exercise_set WHERE exerciseId = :exerciseId AND isCompleted = 1")
    suspend fun getMaxWeightEver(exerciseId: Long): Double?

    @Query("SELECT MAX(reps) FROM exercise_set WHERE exerciseId = :exerciseId AND isCompleted = 1")
    suspend fun getMaxRepsEver(exerciseId: Long): Int?
}
