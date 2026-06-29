package com.securevault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_profiles")
data class CustomProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
