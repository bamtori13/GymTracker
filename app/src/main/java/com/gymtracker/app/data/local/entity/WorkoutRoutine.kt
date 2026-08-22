package com.gymtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사용자가 만드는 운동 루틴 (예: "A Day - 상체").
 * Exercise 목록을 담는 상위 컨테이너.
 */
@Entity(tableName = "workout_routine")
data class WorkoutRoutine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0
)
