package com.securevault.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.securevault.utils.CryptoUtils
import java.util.UUID

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "service") val service: String,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "encrypted_password") val encryptedPassword: String,
    
    @ColumnInfo(name = "profile") val profile: Profile = Profile.PERSONAL,
    @ColumnInfo(name = "emoji_hint") val emojiHint: String? = null,
    
    @ColumnInfo(name = "rotation_enabled") val rotationEnabled: Boolean = false,
    @ColumnInfo(name = "rotation_period_months") val rotationPeriodMonths: Int = 6,
    @ColumnInfo(name = "next_rotation_date") val nextRotationDate: Long? = null,
    
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_changed") val lastChanged: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "failed_attempts") val failedAttempts: Int = 0
) {
    // ✅ Расшифрованный пароль (только для чтения, не сохраняй в БД!)
    val password: String
        get() = CryptoUtils.decrypt(encryptedPassword)

    // ✅ Статус истечения пароля
    fun getExpiryStatus(): ExpiryStatus {
        if (!rotationEnabled || nextRotationDate == null) return ExpiryStatus.OK
        
        val daysLeft = getDaysUntilExpiry()
        return when {
            daysLeft < 0 -> ExpiryStatus.EXPIRED
            daysLeft <= 3 -> ExpiryStatus.CRITICAL
            daysLeft <= 7 -> ExpiryStatus.WARNING
            else -> ExpiryStatus.OK
        }
    }

    // ✅ Дней до истечения (может быть отрицательным)
    fun getDaysUntilExpiry(): Int {
        if (nextRotationDate == null) return Int.MAX_VALUE
        val diffMillis = nextRotationDate - System.currentTimeMillis()
        return (diffMillis / (1000 * 60 * 60 * 24)).toInt()
    }

    // ✅ Просрочен ли пароль
    fun isPasswordExpired(): Boolean {
        return nextRotationDate != null && System.currentTimeMillis() > nextRotationDate
    }

    // ✅ Фабричный метод для создания новой записи
    companion object {
        fun create(
            service: String,
            username: String,
            password: String,
            profile: Profile = Profile.PERSONAL,
            emojiHint: String? = null,
            rotationEnabled: Boolean = false,
            rotationPeriodMonths: Int = 6
        ): Entry {
            val encryptedPassword = CryptoUtils.encrypt(password)
            val nextRotationDate = if (rotationEnabled) {
                System.currentTimeMillis() + (rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
            } else null
            
            return Entry(
                service = service,
                username = username,
                encryptedPassword = encryptedPassword,
                profile = profile,
                emojiHint = emojiHint,
                rotationEnabled = rotationEnabled,
                rotationPeriodMonths = rotationPeriodMonths,
                nextRotationDate = nextRotationDate
            )
        }
    }

    // ✅ Статусы истечения пароля
    enum class ExpiryStatus {
        OK,           // Всё хорошо
        WARNING,      // Истекает через 4-7 дней
        CRITICAL,     // Истекает через 1-3 дня
        EXPIRED       // Уже просрочен
    }
}
