package com.gymtracker.app.data.local

import androidx.room.TypeConverter
import com.gymtracker.app.data.local.entity.PersonalRecordType

class Converters {
    @TypeConverter
    fun fromRecordType(type: PersonalRecordType): String = type.name

    @TypeConverter
    fun toRecordType(value: String): PersonalRecordType = PersonalRecordType.valueOf(value)
}
