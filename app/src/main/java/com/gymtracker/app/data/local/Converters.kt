package com.gymtracker.app.data.local

import androidx.room.TypeConverter
import com.gymtracker.app.data.local.entity.ExerciseInputType
import com.gymtracker.app.data.local.entity.PersonalRecordType

class Converters {
    @TypeConverter
    fun fromRecordType(type: PersonalRecordType): String = type.name

    @TypeConverter
    fun toRecordType(value: String): PersonalRecordType = PersonalRecordType.valueOf(value)

    @TypeConverter
    fun fromInputType(type: ExerciseInputType): String = type.name

    @TypeConverter
    fun toInputType(value: String): ExerciseInputType = ExerciseInputType.valueOf(value)
}
