package com.gymtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 하루 세션에 "오늘 할(했던) 운동"으로 추가된 항목.
 * "+운동추가" 팝업에서 개별 운동을 고르거나 루틴을 골라 여러 개를 한 번에 추가하면 이 테이블에 행이 생긴다.
 * 같은 세션에 같은 운동이 중복으로 들어가지 않도록 (sessionId, exerciseId) unique index를 둔다.
 */
@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId", "exerciseId"], unique = true)]
)
data class SessionExercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val sortOrder: Int = 0,
    val memo: String = ""
)
