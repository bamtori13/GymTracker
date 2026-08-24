package com.gymtracker.app.data.local.dao

import androidx.room.*
import com.gymtracker.app.data.local.entity.Exercise
import com.gymtracker.app.data.local.entity.RoutineExercise
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineExerciseDao {

    @Query(
        "SELECT e.* FROM exercise e " +
            "INNER JOIN routine_exercise re ON re.exerciseId = e.id " +
            "WHERE re.routineId = :routineId ORDER BY re.sortOrder ASC"
    )
    fun observeExercisesForRoutine(routineId: Long): Flow<List<Exercise>>

    @Query("SELECT COUNT(*) FROM routine_exercise WHERE routineId = :routineId")
    suspend fun countForRoutine(routineId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: RoutineExercise): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<RoutineExercise>)

    @Query("DELETE FROM routine_exercise WHERE routineId = :routineId")
    suspend fun deleteForRoutine(routineId: Long)
    
    // --- export/import ---
    @Query("SELECT * FROM routine_exercise")
    suspend fun getAll(): List<RoutineExercise>

    @Query("DELETE FROM routine_exercise")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(items: List<RoutineExercise>)
}
