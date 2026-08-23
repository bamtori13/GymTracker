package com.gymtracker.app.data.repository

import com.gymtracker.app.data.local.AppDatabase
import com.gymtracker.app.data.local.DefaultExercises
import com.gymtracker.app.data.local.entity.Exercise
import com.gymtracker.app.data.local.entity.ExerciseSet
import com.gymtracker.app.data.local.entity.RoutineExercise
import com.gymtracker.app.data.local.entity.SessionExercise
import com.gymtracker.app.data.local.entity.WorkoutRoutine
import com.gymtracker.app.data.local.entity.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class WorkoutRepository(private val db: AppDatabase) {

    /** 최초 실행 시 exercise 테이블이 비어 있으면 부위별 기본 목록을 한 번 넣어준다. */
    suspend fun seedDefaultExercisesIfNeeded() {
        if (db.exerciseDao().count() == 0) {
            db.exerciseDao().insertAll(DefaultExercises.ALL)
        }
    }

    // --- Routine ---
    fun observeRoutines(): Flow<List<WorkoutRoutine>> = db.routineDao().observeAll()

    suspend fun getRoutine(id: Long): WorkoutRoutine? = db.routineDao().getById(id)

    /** 루틴이름 + 고른 운동 id 목록으로 루틴을 한 번에 만든다 ("새 루틴 만들기" 화면 확인 버튼). */
    suspend fun createRoutine(name: String, exerciseIds: List<Long>): Long {
        val routineId = db.routineDao().insert(WorkoutRoutine(name = name))
        val entries = exerciseIds.mapIndexed { index, exerciseId ->
            RoutineExercise(routineId = routineId, exerciseId = exerciseId, sortOrder = index)
        }
        db.routineExerciseDao().insertAll(entries)
        return routineId
    }

    fun observeExercisesForRoutine(routineId: Long): Flow<List<Exercise>> =
        db.routineExerciseDao().observeExercisesForRoutine(routineId)

    // --- Exercise (전역 카탈로그) ---
    fun observeAllExercises(): Flow<List<Exercise>> = db.exerciseDao().observeAll()

    suspend fun getExercise(id: Long): Exercise? = db.exerciseDao().getById(id)

    /** "+새 운동 추가" 화면 확인 버튼. */
    suspend fun addExercise(
        name: String,
        bodyPart: String,
        inputType: com.gymtracker.app.data.local.entity.ExerciseInputType
    ): Long = db.exerciseDao().insert(
        Exercise(name = name, bodyPart = bodyPart, inputType = inputType, isCustom = true)
    )

    suspend fun updateExercise(exercise: Exercise, name: String, bodyPart: String, inputType: com.gymtracker.app.data.local.entity.ExerciseInputType) =
        db.exerciseDao().update(exercise.copy(name = name, bodyPart = bodyPart, inputType = inputType))

    suspend fun deleteExercise(exercise: Exercise) = db.exerciseDao().delete(exercise)

    suspend fun renameRoutine(routine: WorkoutRoutine, newName: String) =
        db.routineDao().update(routine.copy(name = newName))

    suspend fun deleteRoutine(routine: WorkoutRoutine) = db.routineDao().delete(routine)

    // --- Session (날짜 단위) ---
    suspend fun getOrCreateSessionForDate(dateEpochDay: Long): Long {
        db.sessionDao().getByDate(dateEpochDay)?.let { return it.id }
        return db.sessionDao().insert(WorkoutSession(dateEpochDay = dateEpochDay))
    }

    suspend fun getLastSessionWithExerciseBefore(exerciseId: Long, beforeEpochDay: Long): WorkoutSession? =
        db.sessionDao().getLastSessionWithExerciseBefore(exerciseId, beforeEpochDay)

    // --- SessionExercise ---
    fun observeSessionExercises(sessionId: Long): Flow<List<SessionExercise>> =
        db.sessionExerciseDao().observeForSession(sessionId)

    suspend fun addExerciseToSession(sessionId: Long, exerciseId: Long) {
        if (db.sessionExerciseDao().find(sessionId, exerciseId) != null) return
        val order = db.sessionExerciseDao().countForSession(sessionId)
        db.sessionExerciseDao().insert(SessionExercise(sessionId = sessionId, exerciseId = exerciseId, sortOrder = order))
    }

    suspend fun addRoutineToSession(sessionId: Long, routineId: Long) {
        val exercises = db.routineExerciseDao().observeExercisesForRoutine(routineId).first()
        exercises.forEach { addExerciseToSession(sessionId, it.id) }
    }

    suspend fun removeExerciseFromSession(entry: SessionExercise) = db.sessionExerciseDao().delete(entry)

    suspend fun updateSessionExerciseMemo(entry: SessionExercise, memo: String) =
        db.sessionExerciseDao().update(entry.copy(memo = memo))

    // --- Sets ---
    suspend fun getSetsForExerciseInSession(exerciseId: Long, sessionId: Long): List<ExerciseSet> =
        db.exerciseSetDao().getSetsForExerciseInSession(exerciseId, sessionId)

    suspend fun saveSet(set: ExerciseSet): Long = db.exerciseSetDao().upsert(set)

    suspend fun deleteSet(set: ExerciseSet) = db.exerciseSetDao().delete(set)

    suspend fun getMaxWeightEver(exerciseId: Long): Double? = db.exerciseSetDao().getMaxWeightEver(exerciseId)

    suspend fun getMaxRepsEver(exerciseId: Long): Int? = db.exerciseSetDao().getMaxRepsEver(exerciseId)

    suspend fun getAllSetsForSession(sessionId: Long): List<ExerciseSet> =
        db.exerciseSetDao().getAllForSession(sessionId)
}
