package com.gymtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 루틴에 속한 개별 운동 (예: Squat, Bench Press).
 *
 * currentTargetWeight / currentTargetReps* : 다음 운동에서 사용할 추천(또는 사용자가 직접 수정한) 목표.
 * Phase 2에서 점진적 과부하 알고리즘이 이 값을 갱신한다. Phase 1에서는 사용자가 직접 초기값을 입력한다.
 */
@Entity(
    tableName = "exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutRoutine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId")]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routineId: Long,
    val name: String,
    val sortOrder: Int = 0,
    val targetSets: Int = 4,
    val minReps: Int = 8,
    val maxReps: Int = 10,
    val weightIncrement: Double = 2.5,
    val currentTargetWeight: Double = 0.0
)
