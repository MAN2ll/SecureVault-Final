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
    val profile: Profile = Profile.PERSONAL,
    val createdAt: Long = System.currentTimeMillis(),
    val lastChanged: Long = System.currentTimeMillis(),
    val changeIntervalDays: Int = 90,
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0L
) {
    //  Геттер: дешифрует пароль при чтении
    val password: String
        get() = if (CryptoUtils.isEncrypted(encryptedPassword)) {
            CryptoUtils.decrypt(encryptedPassword)
        } else {
            encryptedPassword
        }

    //  Factory-метод для создания с шифрованием
    companion object {
        fun create(
            service: String,
            username: String,
            password: String,
            category: String = "general",
            profile: Profile = Profile.PERSONAL,
            notes: String = "",
            changeIntervalDays: Int = 90
        ): Entry {
            return Entry(
                service = service,
                username = username,
                encryptedPassword = CryptoUtils.encrypt(password),
                category = category,
                profile = profile,
                notes = notes,
                changeIntervalDays = changeIntervalDays
            )
        }
    }

    //  Проверка: просрочен ли пароль
    fun isPasswordExpired(): Boolean {
        val daysSinceChange = (System.currentTimeMillis() - lastChanged) / (1000 * 60 * 60 * 24)
        return daysSinceChange >= changeIntervalDays
    }
    
    //  Дней до истечения (может быть отрицательным)
    fun getDaysUntilExpiry(): Int {
        val daysSinceChange = (System.currentTimeMillis() - lastChanged) / (1000 * 60 * 60 * 24)
        return changeIntervalDays - daysSinceChange.toInt()
    }
    
    //  Статус для цветовой индикации в UI
    fun getExpiryStatus(): ExpiryStatus {
        return when {
            isPasswordExpired() -> ExpiryStatus.EXPIRED
            getDaysUntilExpiry() <= 3 -> ExpiryStatus.CRITICAL
            getDaysUntilExpiry() <= 7 -> ExpiryStatus.WARNING
            else -> ExpiryStatus.OK
        }
    }
    
    //  Проверка блокировки после неудачных попыток
    fun isLocked(): Boolean {
        return System.currentTimeMillis() < lockedUntil
    }
    
    //  Статусы для UI
    enum class ExpiryStatus {
        OK,
        WARNING,
        CRITICAL,
        EXPIRED
    }
} // ← Закрывает data class Entry

//  Профили: отдельный enum вне класса
enum class Profile {
    PERSONAL,
    WORK
} // ← Закрывает enum class Profile
