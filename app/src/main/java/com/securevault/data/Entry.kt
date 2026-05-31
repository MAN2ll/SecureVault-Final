package com.securevault.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.securevault.utils.CryptoUtils
import java.util.UUID

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val service: String,
    val username: String,
    
    @ColumnInfo(name = "encrypted_password")
    val encryptedPassword: String,
    
    val category: String = "general",
    val notes: String = "",
    val isFavorite: Boolean = false,
    
    //  Профиль: рабочий или личный
    val profile: Profile = Profile.PERSONAL,
    
    //  Для напоминаний о смене пароля
    val createdAt: Long = System.currentTimeMillis(),
    val lastChanged: Long = System.currentTimeMillis(),
    val changeIntervalDays: Int = 90,
    
    //  Для защиты от подбора
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0L
) {
    //  Геттер: дешифрует пароль "на лету"
    val password: String
        get() = if (CryptoUtils.isEncrypted(encryptedPassword)) {
            CryptoUtils.decrypt(encryptedPassword)
        }
