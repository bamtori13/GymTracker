package com.gymtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PersonalRecordType {
    MAX_WEIGHT,
    MAX_REPS_AT_WEIGHT,
    ESTIMATED_1RM
}

/**
 * PR(자기 최고 기록) 이력. Phase 2의 PR 자동 감지 기능에서 채워진다.
 * Phase 1에서는 테이블만 존재하고 아직 기록되지 않는다.
 */
@Entity(
    tableName = "personal_record",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exerciseId")]
)
data class PersonalRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exerciseId: Long,
    val type: PersonalRecordType,
    val weight: Double,
    val reps: Int,
    val achievedAt: Long
)
