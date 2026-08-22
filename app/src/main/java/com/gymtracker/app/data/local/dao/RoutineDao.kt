package com.gymtracker.app.data.local.dao

import androidx.room.*
import com.gymtracker.app.data.local.entity.WorkoutRoutine
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("SELECT * FROM workout_routine ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<WorkoutRoutine>>

    @Query("SELECT * FROM workout_routine WHERE id = :id")
    suspend fun getById(id: Long): WorkoutRoutine?

    @Insert
    suspend fun insert(routine: WorkoutRoutine): Long

    @Update
    suspend fun update(routine: WorkoutRoutine)

    @Delete
    suspend fun delete(routine: WorkoutRoutine)
}
