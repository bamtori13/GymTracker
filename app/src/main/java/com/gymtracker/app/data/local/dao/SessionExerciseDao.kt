package com.gymtracker.app.data.local.dao

import androidx.room.*
import com.gymtracker.app.data.local.entity.SessionExercise
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionExerciseDao {

    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId ORDER BY sortOrder ASC")
    fun observeForSession(sessionId: Long): Flow<List<SessionExercise>>

    @Query("SELECT COUNT(*) FROM session_exercise WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId AND exerciseId = :exerciseId LIMIT 1")
    suspend fun find(sessionId: Long, exerciseId: Long): SessionExercise?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: SessionExercise): Long

    @Update
    suspend fun update(entry: SessionExercise)

    @Delete
    suspend fun delete(entry: SessionExercise)
}
