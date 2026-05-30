package com.securevault.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.securevault.utils.CryptoUtils
import java.util.UUID

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val service: String,
    val username: String,
    
    //  Пароль хранится ЗАШИФРОВАННЫМ
    private val _encryptedPassword: String,
    
    val category: String = "general",
    val notes: String = "",
    val isFavorite: Boolean = false,
    
    //  Профиль: рабочий или личный
    val profile: Profile = Profile.PERSONAL,
    
    //  Для напоминаний о смене пароля
    val createdAt: Long = System.currentTimeMillis(),
    val lastChanged: Long = System.currentTimeMillis(),
    val changeIntervalDays: Int = 90, // через сколько дней напомнить
    
    //  Для защиты от подбора
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0L // timestamp, до которого запись заблокирована
) {
    //  Геттер: дешифрует пароль "на лету"
    val password: String
        get() = if (CryptoUtils.isEncrypted(_encryptedPassword)) {
            CryptoUtils.decrypt(_encryptedPassword)
        } else {
            _encryptedPassword // для обратной совместимости
        }

    //  Метод для создания записи с шифрованием
    companion object {
        fun create(
            service: String,
            username: String,
            password: String, // открытый пароль
            category: String = "general",
            profile: Profile = Profile.PERSONAL,
            notes: String = "",
            changeIntervalDays: Int = 90
        ): Entry {
            return Entry(
                service = service,
                username = username,
                _encryptedPassword = CryptoUtils.encrypt(password), // шифруем!
                category = category,
                profile = profile,
                notes = notes,
                changeIntervalDays = changeIntervalDays
            )
        }
    }

    // Проверка: пора ли сменить пароль?
    fun isPasswordExpired(): Boolean {
        val daysSinceChange = (System.currentTimeMillis() - lastChanged) / (1000 * 60 * 60 * 24)
        return daysSinceChange >= changeIntervalDays
    }

    // Проверка: заблокирована ли запись из-за подбора?
    fun isLocked(): Boolean {
        return System.currentTimeMillis() < lockedUntil
    }
}

// Профили для разделения
enum class Profile {
    PERSONAL,
    WORK
}
