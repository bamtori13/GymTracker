package com.gymtracker.app.data.local.dao

import androidx.room.*
import com.gymtracker.app.data.local.entity.PeriodDay
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodDayDao {

    @Query("SELECT EXISTS(SELECT 1 FROM period_day WHERE dateEpochDay = :dateEpochDay)")
    fun observeIsPeriod(dateEpochDay: Long): Flow<Boolean>

    @Query("SELECT * FROM period_day WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeInRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<PeriodDay>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(day: PeriodDay)

    @Query("DELETE FROM period_day WHERE dateEpochDay = :dateEpochDay")
    suspend fun delete(dateEpochDay: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM period_day WHERE dateEpochDay = :dateEpochDay)")
    suspend fun isPeriod(dateEpochDay: Long): Boolean

    // --- export/import ---
    @Query("SELECT * FROM period_day")
    suspend fun getAll(): List<PeriodDay>

    @Query("DELETE FROM period_day")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(items: List<PeriodDay>)
}
