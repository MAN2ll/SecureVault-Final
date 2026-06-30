package com.securevault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val passwordHash: String, // SHA-256 хэш пароля профиля
    val createdAt: Long = System.currentTimeMillis()
)
