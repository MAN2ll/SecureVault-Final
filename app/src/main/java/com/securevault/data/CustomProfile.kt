package com.securevault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_profiles")
data class CustomProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorHex: String = "#2196F3",
    val createdAt: Long = System.currentTimeMillis()
)
