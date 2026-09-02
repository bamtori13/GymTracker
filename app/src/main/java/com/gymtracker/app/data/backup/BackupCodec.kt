package com.gymtracker.app.data.backup

import com.gymtracker.app.data.local.entity.Exercise
import com.gymtracker.app.data.local.entity.ExerciseInputType
import com.gymtracker.app.data.local.entity.ExerciseSet
import com.gymtracker.app.data.local.entity.PeriodDay
import com.gymtracker.app.data.local.entity.RoutineExercise
import com.gymtracker.app.data.local.entity.SessionExercise
import com.gymtracker.app.data.local.entity.WorkoutRoutine
import com.gymtracker.app.data.local.entity.WorkoutSession
import org.json.JSONArray
import org.json.JSONObject

/** 백업 파일에 담기는 전체 데이터. id까지 그대로 보존해서 관계가 깨지지 않게 한다. */
data class BackupData(
    val exercises: List<Exercise>,
    val routines: List<WorkoutRoutine>,
    val routineExercises: List<RoutineExercise>,
    val sessions: List<WorkoutSession>,
    val sessionExercises: List<SessionExercise>,
    val sets: List<ExerciseSet>,
    val periodDays: List<PeriodDay>
)

/**
 * 백업 JSON 인코더/디코더.
 * 의존성을 늘리지 않으려고 Android 기본 제공 org.json만 쓴다.
 * version은 나중에 스키마가 바뀔 때 분기하기 위한 표식.
 */
object BackupCodec {

    const val VERSION = 1

    fun encode(data: BackupData): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exercises", data.exercises.toJsonArray(::exerciseToJson))
        root.put("routines", data.routines.toJsonArray(::routineToJson))
        root.put("routineExercises", data.routineExercises.toJsonArray(::routineExerciseToJson))
        root.put("sessions", data.sessions.toJsonArray(::sessionToJson))
        root.put("sessionExercises", data.sessionExercises.toJsonArray(::sessionExerciseToJson))
        root.put("sets", data.sets.toJsonArray(::setToJson))
        root.put("periodDays", data.periodDays.toJsonArray(::periodDayToJson))
        return root.toString(2)
    }

    fun decode(json: String): BackupData {
        val root = JSONObject(json)
        return BackupData(
            exercises = root.mapArray("exercises", ::jsonToExercise),
            routines = root.mapArray("routines", ::jsonToRoutine),
            routineExercises = root.mapArray("routineExercises", ::jsonToRoutineExercise),
            sessions = root.mapArray("sessions", ::jsonToSession),
            sessionExercises = root.mapArray("sessionExercises", ::jsonToSessionExercise),
            sets = root.mapArray("sets", ::jsonToSet),
            // version 1 백업에는 없던 항목이므로 없으면 빈 목록.
            periodDays = root.mapArray("periodDays", ::jsonToPeriodDay)
        )
    }

    // --- entity <-> json ---

    private fun exerciseToJson(e: Exercise) = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("bodyPart", e.bodyPart)
        put("inputType", e.inputType.name)
        put("isCustom", e.isCustom)
        put("targetSets", e.targetSets)
        put("minReps", e.minReps)
        put("maxReps", e.maxReps)
        put("weightIncrement", e.weightIncrement)
        put("currentTargetWeight", e.currentTargetWeight)
    }

    private fun jsonToExercise(o: JSONObject) = Exercise(
        id = o.getLong("id"),
        name = o.getString("name"),
        bodyPart = o.getString("bodyPart"),
        inputType = runCatching { ExerciseInputType.valueOf(o.getString("inputType")) }
            .getOrDefault(ExerciseInputType.WEIGHT_REPS),
        isCustom = o.optBoolean("isCustom", false),
        targetSets = o.optInt("targetSets", 4),
        minReps = o.optInt("minReps", 8),
        maxReps = o.optInt("maxReps", 10),
        weightIncrement = o.optDouble("weightIncrement", 2.5),
        currentTargetWeight = o.optDouble("currentTargetWeight", 0.0)
    )

    private fun routineToJson(r: WorkoutRoutine) = JSONObject().apply {
        put("id", r.id)
        put("name", r.name)
        put("sortOrder", r.sortOrder)
    }

    private fun jsonToRoutine(o: JSONObject) = WorkoutRoutine(
        id = o.getLong("id"),
        name = o.getString("name"),
        sortOrder = o.optInt("sortOrder", 0)
    )

    private fun routineExerciseToJson(r: RoutineExercise) = JSONObject().apply {
        put("id", r.id)
        put("routineId", r.routineId)
        put("exerciseId", r.exerciseId)
        put("sortOrder", r.sortOrder)
    }

    private fun jsonToRoutineExercise(o: JSONObject) = RoutineExercise(
        id = o.getLong("id"),
        routineId = o.getLong("routineId"),
        exerciseId = o.getLong("exerciseId"),
        sortOrder = o.optInt("sortOrder", 0)
    )

    private fun sessionToJson(s: WorkoutSession) = JSONObject().apply {
        put("id", s.id)
        put("dateEpochDay", s.dateEpochDay)
    }

    private fun jsonToSession(o: JSONObject) = WorkoutSession(
        id = o.getLong("id"),
        dateEpochDay = o.getLong("dateEpochDay")
    )

    private fun sessionExerciseToJson(s: SessionExercise) = JSONObject().apply {
        put("id", s.id)
        put("sessionId", s.sessionId)
        put("exerciseId", s.exerciseId)
        put("sortOrder", s.sortOrder)
        put("memo", s.memo)
    }

    private fun jsonToSessionExercise(o: JSONObject) = SessionExercise(
        id = o.getLong("id"),
        sessionId = o.getLong("sessionId"),
        exerciseId = o.getLong("exerciseId"),
        sortOrder = o.optInt("sortOrder", 0),
        memo = o.optString("memo", "")
    )

    private fun setToJson(s: ExerciseSet) = JSONObject().apply {
        put("id", s.id)
        put("sessionId", s.sessionId)
        put("exerciseId", s.exerciseId)
        put("setNumber", s.setNumber)
        put("weight", s.weight)
        put("reps", s.reps)
        put("isCompleted", s.isCompleted)
    }

    private fun jsonToSet(o: JSONObject) = ExerciseSet(
        id = o.getLong("id"),
        sessionId = o.getLong("sessionId"),
        exerciseId = o.getLong("exerciseId"),
        setNumber = o.optInt("setNumber", 1),
        weight = o.optDouble("weight", 0.0),
        reps = o.optInt("reps", 0),
        isCompleted = o.optBoolean("isCompleted", false)
    )

    private fun periodDayToJson(p: PeriodDay) = JSONObject().apply {
        put("dateEpochDay", p.dateEpochDay)
    }

    private fun jsonToPeriodDay(o: JSONObject) = PeriodDay(
        dateEpochDay = o.getLong("dateEpochDay")
    )

    // --- helpers ---

    private fun <T> List<T>.toJsonArray(toJson: (T) -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(toJson(it)) } }

    private fun <T> JSONObject.mapArray(key: String, fromJson: (JSONObject) -> T): List<T> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
    }
}
