package com.gymtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 생리일 체크. 날짜 하나가 곧 한 행이므로 dateEpochDay를 그대로 PK로 쓴다
 * (unique index를 따로 두거나 중복 검사를 할 필요가 없다).
 * 체크를 끄면 행을 삭제한다 — 별도의 on/off 컬럼을 두지 않는다.
 */
@Entity(tableName = "period_day")
data class PeriodDay(
    @PrimaryKey
    val dateEpochDay: Long
)
