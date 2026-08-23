package com.gymtracker.app.data.local.dao

import androidx.room.*
import com.gymtracker.app.data.local.entity.Exercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercise ORDER BY bodyPart ASC, name ASC")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Insert
    suspend fun insertAll(exercises: List<Exercise>)

    @Update
    suspend fun update(exercise: Exercise)

    @Delete
    suspend fun delete(exercise: Exercise)
}
