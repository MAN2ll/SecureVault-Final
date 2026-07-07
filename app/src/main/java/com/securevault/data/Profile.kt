package com.securevault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val passwordHash: String,
    val passwordSalt: String, // : соль для хэширования
    val createdAt: Long = System.currentTimeMillis()
)
