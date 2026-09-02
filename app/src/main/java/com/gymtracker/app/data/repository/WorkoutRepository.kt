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
import com.gymtracker.app.data.local.entity.PeriodDay
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

    /** 루틴에 든 운동을 한 번만 읽어온다 (오늘 화면에 루틴을 펼쳐 넣을 때). */
    suspend fun getExercisesForRoutine(routineId: Long): List<Exercise> =
        db.routineExerciseDao().observeExercisesForRoutine(routineId).first()

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

    // --- 생리일 ---
    fun observeIsPeriod(dateEpochDay: Long): Flow<Boolean> =
        db.periodDayDao().observeIsPeriod(dateEpochDay)

    fun observePeriodDaysInRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<PeriodDay>> =
        db.periodDayDao().observeInRange(fromEpochDay, toEpochDay)

    suspend fun isPeriod(dateEpochDay: Long): Boolean = db.periodDayDao().isPeriod(dateEpochDay)

    /** 체크되어 있으면 끄고, 없으면 켠다. */
    suspend fun togglePeriod(dateEpochDay: Long) {
        if (db.periodDayDao().isPeriod(dateEpochDay)) {
            db.periodDayDao().delete(dateEpochDay)
        } else {
            db.periodDayDao().insert(PeriodDay(dateEpochDay))
        }
    }

    // --- SessionExercise ---
    fun observeSessionExercises(sessionId: Long): Flow<List<SessionExercise>> =
        db.sessionExerciseDao().observeForSession(sessionId)

    suspend fun addExerciseToSession(sessionId: Long, exerciseId: Long) {
        if (db.sessionExerciseDao().find(sessionId, exerciseId) != null) return
        val order = db.sessionExerciseDao().countForSession(sessionId)
        db.sessionExerciseDao().insert(SessionExercise(sessionId = sessionId, exerciseId = exerciseId, sortOrder = order))
    }

    /**
     * 오늘에 운동을 추가하면서 직전에 이 운동을 했던 날의 기록(세트 구성 + 메모)을 그대로 복사한다.
     * 완료 체크만 풀어서 넣는다 — 지난번 값이 "오늘의 목표"로 깔리고, 실제로 한 것만 다시 체크하면 된다.
     * 이미 오늘에 있는 운동이면 아무것도 하지 않는다(중복 추가로 기록을 덮어쓰지 않도록).
     */
    suspend fun addExerciseCopyingPrevious(sessionId: Long, exerciseId: Long, dateEpochDay: Long) {
        if (db.sessionExerciseDao().find(sessionId, exerciseId) != null) return
        addExerciseToSession(sessionId, exerciseId)
        val entry = db.sessionExerciseDao().find(sessionId, exerciseId) ?: return

        val previous = db.sessionDao().getLastSessionWithExerciseBefore(exerciseId, dateEpochDay)
        val previousSets = previous
            ?.let { db.exerciseSetDao().getSetsForExerciseInSession(exerciseId, it.id) }
            .orEmpty()

        if (previousSets.isEmpty()) {
            // 처음 하는 운동(또는 지난번에 세트를 안 넣은 경우) — 빈 1세트만 만들어 준다.
            insertDefaultFirstSet(sessionId, exerciseId)
            return
        }

        previous?.let { prev ->
            val previousMemo = db.sessionExerciseDao().find(prev.id, exerciseId)?.memo ?: ""
            if (previousMemo.isNotBlank()) {
                db.sessionExerciseDao().update(entry.copy(memo = previousMemo))
            }
        }
        previousSets.forEach { set ->
            // id = 0 이어야 새 행으로 들어간다.
            db.exerciseSetDao().upsert(set.copy(id = 0, sessionId = sessionId, isCompleted = false))
        }
    }

    /** 세트가 하나도 없을 때 깔아주는 기본 1세트. */
    suspend fun insertDefaultFirstSet(sessionId: Long, exerciseId: Long) {
        if (db.exerciseSetDao().getSetsForExerciseInSession(exerciseId, sessionId).isNotEmpty()) return
        val exercise = db.exerciseDao().getById(exerciseId) ?: return
        val isTime = exercise.inputType == com.gymtracker.app.data.local.entity.ExerciseInputType.TIME
        db.exerciseSetDao().upsert(
            ExerciseSet(
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = 1,
                // 시간 기반 운동은 weight 칸이 "강도"라서 목표중량을 쓰지 않고 0에서 시작한다.
                weight = if (isTime) 0.0 else exercise.currentTargetWeight,
                reps = exercise.minReps,
                isCompleted = false
            )
        )
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
            sets = db.exerciseSetDao().getAll(),
            periodDays = db.periodDayDao().getAll()
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
            db.periodDayDao().deleteAll()

            db.exerciseDao().restoreAll(data.exercises)
            db.routineDao().restoreAll(data.routines)
            db.sessionDao().restoreAll(data.sessions)
            db.routineExerciseDao().restoreAll(data.routineExercises)
            db.sessionExerciseDao().restoreAll(data.sessionExercises)
            db.exerciseSetDao().restoreAll(data.sets)
            db.periodDayDao().restoreAll(data.periodDays)
        }
    }
}
