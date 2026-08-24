package com.gymtracker.app.data.repository

import androidx.room.withTransaction
import com.gymtracker.app.data.backup.BackupCodec
import com.gymtracker.app.data.backup.BackupData
import com.gymtracker.app.data.local.AppDatabase
import com.gymtracker.app.data.local.DefaultExercises
import com.gymtracker.app.data.local.dao.BodyPartLastDay
import com.gymtracker.app.data.local.dao.BodyPartMonthStat
import com.gymtracker.app.data.local.dao.DayBodyPart
import com.gymtracker.app.data.local.dao.SetHistoryRow
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

    /** 루틴 편집: 이름과 포함 운동 목록을 통째로 갈아끼운다. */
    suspend fun updateRoutine(routine: WorkoutRoutine, newName: String, exerciseIds: List<Long>) {
        db.routineDao().update(routine.copy(name = newName))
        db.routineExerciseDao().deleteForRoutine(routine.id)
        db.routineExerciseDao().insertAll(
            exerciseIds.mapIndexed { index, exerciseId ->
                RoutineExercise(routineId = routine.id, exerciseId = exerciseId, sortOrder = index)
            }
        )
    }

    suspend fun deleteRoutine(routine: WorkoutRoutine) = db.routineDao().delete(routine)

    // --- Session (날짜 단위) ---
    suspend fun getOrCreateSessionForDate(dateEpochDay: Long): Long {
        db.sessionDao().getByDate(dateEpochDay)?.let { return it.id }
        return db.sessionDao().insert(WorkoutSession(dateEpochDay = dateEpochDay))
    }

    suspend fun getLastSessionWithExerciseBefore(exerciseId: Long, beforeEpochDay: Long): WorkoutSession? =
        db.sessionDao().getLastSessionWithExerciseBefore(exerciseId, beforeEpochDay)

    /** 달력용: 기간 안에 운동이 기록된 날짜별 부위 목록. */
    fun observeBodyPartsInRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<DayBodyPart>> =
        db.sessionDao().observeBodyPartsInRange(fromEpochDay, toEpochDay)

    /** 달력 하단 요약용: 이 달 부위별 시행횟수/총량. */
    fun observeBodyPartMonthStats(fromEpochDay: Long, toEpochDay: Long): Flow<List<BodyPartMonthStat>> =
        db.sessionDao().observeBodyPartMonthStats(fromEpochDay, toEpochDay)

    /** 달력 하단 요약용: 부위별 마지막 수행일(전체 기간). */
    fun observeBodyPartLastDays(): Flow<List<BodyPartLastDay>> =
        db.sessionDao().observeBodyPartLastDays()

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

    /** 카드 순서 저장: 넘어온 순서 그대로 sortOrder를 0..n으로 다시 매긴다. */
    suspend fun reorderSessionExercises(ordered: List<SessionExercise>) =
        db.sessionExerciseDao().updateAll(
            ordered.mapIndexed { index, entry -> entry.copy(sortOrder = index) }
        )

    // --- Sets ---
    suspend fun getSetsForExerciseInSession(exerciseId: Long, sessionId: Long): List<ExerciseSet> =
        db.exerciseSetDao().getSetsForExerciseInSession(exerciseId, sessionId)

    suspend fun saveSet(set: ExerciseSet): Long = db.exerciseSetDao().upsert(set)

    suspend fun deleteSet(set: ExerciseSet) = db.exerciseSetDao().delete(set)

    suspend fun getMaxWeightEver(exerciseId: Long): Double? = db.exerciseSetDao().getMaxWeightEver(exerciseId)

    suspend fun getMaxRepsEver(exerciseId: Long): Int? = db.exerciseSetDao().getMaxRepsEver(exerciseId)

    suspend fun getAllSetsForSession(sessionId: Long): List<ExerciseSet> =
        db.exerciseSetDao().getAllForSession(sessionId)

    // --- 통계 ---
    /** 완료 체크된 모든 세트를 날짜/운동 정보와 함께. 통계 화면이 여기서 집계한다. */
    fun observeCompletedSetHistory(): Flow<List<SetHistoryRow>> =
        db.exerciseSetDao().observeCompletedSetHistory()

    // --- 백업 ---
    suspend fun exportBackup(): String = BackupCodec.encode(
        BackupData(
            exercises = db.exerciseDao().getAll(),
            routines = db.routineDao().getAll(),
            routineExercises = db.routineExerciseDao().getAll(),
            sessions = db.sessionDao().getAll(),
            sessionExercises = db.sessionExerciseDao().getAll(),
            sets = db.exerciseSetDao().getAll()
        )
    )

    /**
     * 백업 복원: 기존 데이터를 전부 지우고 파일 내용으로 갈아끼운다.
     * 삭제/삽입 순서는 외래키 방향을 따라야 한다 (자식 먼저 지우고, 부모 먼저 넣는다).
     * 트랜잭션으로 감싸서 중간에 실패하면 원래 데이터가 그대로 남는다.
     */
    suspend fun importBackup(json: String) {
        val data = BackupCodec.decode(json)
        db.withTransaction {
            db.exerciseSetDao().deleteAll()
            db.sessionExerciseDao().deleteAll()
            db.routineExerciseDao().deleteAll()
            db.sessionDao().deleteAll()
            db.routineDao().deleteAll()
            db.exerciseDao().deleteAll()

            db.exerciseDao().restoreAll(data.exercises)
            db.routineDao().restoreAll(data.routines)
            db.sessionDao().restoreAll(data.sessions)
            db.routineExerciseDao().restoreAll(data.routineExercises)
            db.sessionExerciseDao().restoreAll(data.sessionExercises)
            db.exerciseSetDao().restoreAll(data.sets)
        }
    }
}
