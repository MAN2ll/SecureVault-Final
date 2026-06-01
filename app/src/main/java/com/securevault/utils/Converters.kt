package com.securevault.utils

import androidx.room.TypeConverter
import com.securevault.data.Profile
import java.util.*

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
    
    @TypeConverter
    fun fromProfile(profile: Profile): String = profile.name
    
    @TypeConverter
    fun toProfile(value: String): Profile = Profile.valueOf(value)
}
