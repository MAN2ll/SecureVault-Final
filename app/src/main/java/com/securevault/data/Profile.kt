package com.securevault.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.securevault.utils.AccessMode

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    @ColumnInfo(name = "password_salt") val passwordSalt: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    // Режим защиты просмотра паролей записей с INHERIT
    @ColumnInfo(name = "password_access_mode") val passwordAccessMode: String = AccessMode.PIN_REQUIRED.value,
    // Режим входа в сам профиль
    @ColumnInfo(name = "profile_access_mode") val profileAccessMode: String = AccessMode.PIN_REQUIRED.value
)
