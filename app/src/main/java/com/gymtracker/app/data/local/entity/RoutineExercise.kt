package com.gymtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * "루틴 만들기"에서 체크박스로 고른 운동들을 루틴에 연결하는 조인 테이블.
 * 운동 하나가 여러 루틴에 동시에 속할 수 있다.
 */
@Entity(
    tableName = "routine_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutRoutine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["routineId", "exerciseId"], unique = true)]
)
data class RoutineExercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routineId: Long,
    val exerciseId: Long,
    val sortOrder: Int = 0
)
