package com.gymtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ExerciseInputType {
    WEIGHT_REPS, // 중량 x 횟수
    TIME         // 시간
}

/**
 * 운동 카탈로그(전역). 더 이상 특정 루틴에 종속되지 않는다.
 * 루틴은 RoutineExercise 조인 테이블로 이 운동들을 여러 개 골라 담는다.
 */
@Entity(tableName = "exercise")
data class Exercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val bodyPart: String,               // 가슴/등/어깨/하체/팔/코어/유산소 등
    val inputType: ExerciseInputType = ExerciseInputType.WEIGHT_REPS,
    val isCustom: Boolean = false,      // 기본 제공 목록이면 false, 사용자가 추가했으면 true
    val targetSets: Int = 4,
    val minReps: Int = 8,
    val maxReps: Int = 10,
    val weightIncrement: Double = 2.5,
    val currentTargetWeight: Double = 0.0
)
