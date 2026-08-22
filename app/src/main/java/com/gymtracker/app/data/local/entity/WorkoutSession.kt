package com.gymtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 하루 단위 세션. "오늘" 화면은 dateEpochDay 하나에 매핑되는 세션 한 건을 보여준다.
 * 특정 루틴에 종속되지 않는다 — 하루 안에 여러 루틴/개별 운동이 섞여 들어갈 수 있기 때문.
 * dateEpochDay = java.time.LocalDate.toEpochDay()
 */
@Entity(
    tableName = "workout_session",
    indices = [Index("dateEpochDay", unique = true)]
)
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateEpochDay: Long
)
