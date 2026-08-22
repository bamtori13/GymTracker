package com.gymtracker.app.data.repository

import com.gymtracker.app.data.local.AppDatabase
import com.gymtracker.app.data.local.entity.Exercise
import com.gymtracker.app.data.local.entity.ExerciseSet
import com.gymtracker.app.data.local.entity.SessionExercise
import com.gymtracker.app.data.local.entity.WorkoutRoutine
import com.gymtracker.app.data.local.entity.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * ViewModel은 DAO를 직접 알지 못하고 이 Repository만 사용한다.
 * 향후 원격 백업/동기화(예: Google Drive)를 추가할 때 이 계층만 확장하면 된다.
 */
class WorkoutRepository(private val db: AppDatabase) {

    // --- Routine (운동 템플릿 묶음) ---
    fun observeRoutines(): Flow<List<WorkoutRoutine>> = db.routineDao().observeAll()

    suspend fun createRoutine(name: String, sortOrder: Int = 0): Long =
        db.routineDao().insert(WorkoutRoutine(name = name, sortOrder = sortOrder))

    suspend fun getRoutine(id: Long): WorkoutRoutine? = db.routineDao().getById(id)

    /** Today 화면에서 "새 운동 만들기"로 빠르게 추가할 때 붙여둘 기본 루틴. 없으면 생성한다. */
    suspend fun getOrCreateDefaultRoutine(): Long {
        val existing = db.routineDao().observeAll().first().find { it.name == DEFAULT_ROUTINE_NAME }
        return existing?.id ?: db.routineDao().insert(WorkoutRoutine(name = DEFAULT_ROUTINE_NAME, sortOrder = -1))
    }

    // --- Exercise (운동 카탈로그) ---
    fun observeExercises(routineId: Long): Flow<List<Exercise>> =
        db.exerciseDao().observeByRoutine(routineId)

    fun observeAllExercises(): Flow<List<Exercise>> = db.exerciseDao().observeAll()

    suspend fun getExercisesOnce(routineId: Long): List<Exercise> =
        db.exerciseDao().observeByRoutine(routineId).first()

    suspend fun addExercise(exercise: Exercise): Long = db.exerciseDao().insert(exercise)

    suspend fun updateExercise(exercise: Exercise) = db.exerciseDao().update(exercise)

    suspend fun getExercise(id: Long): Exercise? = db.exerciseDao().getById(id)

    // --- Session (날짜 단위) ---
    suspend fun getOrCreateSessionForDate(dateEpochDay: Long): Long {
        db.sessionDao().getByDate(dateEpochDay)?.let { return it.id }
        return db.sessionDao().insert(WorkoutSession(dateEpochDay = dateEpochDay))
    }

    suspend fun getSessionForDateOrNull(dateEpochDay: Long): WorkoutSession? =
        db.sessionDao().getByDate(dateEpochDay)

    suspend fun getLastSessionWithExerciseBefore(exerciseId: Long, beforeEpochDay: Long): WorkoutSession? =
        db.sessionDao().getLastSessionWithExerciseBefore(exerciseId, beforeEpochDay)

    // --- SessionExercise (그날 화면에 보이는 운동 카드) ---
    fun observeSessionExercises(sessionId: Long): Flow<List<SessionExercise>> =
        db.sessionExerciseDao().observeForSession(sessionId)

    suspend fun addExerciseToSession(sessionId: Long, exerciseId: Long) {
        if (db.sessionExerciseDao().find(sessionId, exerciseId) != null) return
        val order = db.sessionExerciseDao().countForSession(sessionId)
        db.sessionExerciseDao().insert(SessionExercise(sessionId = sessionId, exerciseId = exerciseId, sortOrder = order))
    }

    /** 루틴을 통째로 추가: 루틴에 속한 모든 운동을 오늘 세션에 (중복 없이) 넣는다. */
    suspend fun addRoutineToSession(sessionId: Long, routineId: Long) {
        val exercises = db.exerciseDao().observeByRoutine(routineId).first()
        exercises.forEach { addExerciseToSession(sessionId, it.id) }
    }

    suspend fun removeExerciseFromSession(entry: SessionExercise) = db.sessionExerciseDao().delete(entry)

    suspend fun updateSessionExerciseMemo(entry: SessionExercise, memo: String) =
        db.sessionExerciseDao().update(entry.copy(memo = memo))

    // --- Sets ---
    fun observeSets(sessionId: Long, exerciseId: Long): Flow<List<ExerciseSet>> =
        db.exerciseSetDao().observeForExerciseInSession(sessionId, exerciseId)

    suspend fun getSetsForExerciseInSession(exerciseId: Long, sessionId: Long): List<ExerciseSet> =
        db.exerciseSetDao().getSetsForExerciseInSession(exerciseId, sessionId)

    suspend fun saveSet(set: ExerciseSet): Long = db.exerciseSetDao().upsert(set)

    suspend fun deleteSet(set: ExerciseSet) = db.exerciseSetDao().delete(set)

    suspend fun getMaxWeightEver(exerciseId: Long): Double? = db.exerciseSetDao().getMaxWeightEver(exerciseId)

    suspend fun getAllSetsForSession(sessionId: Long): List<ExerciseSet> =
        db.exerciseSetDao().getAllForSession(sessionId)

    companion object {
        const val DEFAULT_ROUTINE_NAME = "기본 운동"
    }
}
