package com.gymtracker.app.data.local.dao

import androidx.room.*
import com.gymtracker.app.data.local.entity.ExerciseInputType
import com.gymtracker.app.data.local.entity.ExerciseSet
import kotlinx.coroutines.flow.Flow

/**
 * 통계용 평면 projection: "완료된 세트 한 줄 = 날짜 + 운동정보 + 무게/횟수".
 * 주간 볼륨, e1RM 추세 같은 집계는 이 목록을 Kotlin에서 접어서 만든다
 * (SQL로 다 계산하면 쿼리가 여러 개로 갈라지고 화면 요구가 바뀔 때마다 손대야 한다).
 */
data class SetHistoryRow(
    val dateEpochDay: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val bodyPart: String,
    val inputType: ExerciseInputType,
    val weight: Double,
    val reps: Int
)

@Dao
interface ExerciseSetDao {

    @Query(
        "SELECT s.dateEpochDay AS dateEpochDay, e.id AS exerciseId, e.name AS exerciseName, " +
            "e.bodyPart AS bodyPart, e.inputType AS inputType, es.weight AS weight, es.reps AS reps " +
            "FROM exercise_set es " +
            "INNER JOIN workout_session s ON s.id = es.sessionId " +
            "INNER JOIN exercise e ON e.id = es.exerciseId " +
            "WHERE es.isCompleted = 1 " +
            "ORDER BY s.dateEpochDay ASC"
    )
    fun observeCompletedSetHistory(): Flow<List<SetHistoryRow>>

    @Query("SELECT * FROM exercise_set")
    suspend fun getAll(): List<ExerciseSet>

    @Query("DELETE FROM exercise_set")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(items: List<ExerciseSet>)

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
