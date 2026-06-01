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
    
    // Ротация паролей
    @ColumnInfo(name = "rotation_enabled") val rotationEnabled: Boolean = false,
    @ColumnInfo(name = "rotation_period_months") val rotationPeriodMonths: Int = 6,
    @ColumnInfo(name = "next_rotation_date") val nextRotationDate: Long? = null,
    
    // Временные метки
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_changed") val lastChanged: Long = System.currentTimeMillis(),
    
    // Дополнительные поля функционала
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "failed_attempts") val failedAttempts: Int = 0,
    @ColumnInfo(name = "notes") val notes: String? = null
) {
    // Расшифрованный пароль (только для чтения)
    val password: String
        get() = CryptoUtils.decrypt(encryptedPassword)

    // Статус истечения
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

    // Дней до истечения
    fun getDaysUntilExpiry(): Int {
        if (nextRotationDate == null) return Int.MAX_VALUE
        val diffMillis = nextRotationDate - System.currentTimeMillis()
        return (diffMillis / (1000 * 60 * 60 * 24)).toInt()
    }

    // Просрочен ли
    fun isPasswordExpired(): Boolean {
        return nextRotationDate != null && System.currentTimeMillis() > nextRotationDate
    }

    // Фабричный метод
    companion object {
        fun create(
            service: String,
            username: String,
            password: String,
            profile: Profile = Profile.PERSONAL,
            emojiHint: String? = null,
            rotationEnabled: Boolean = false,
            rotationPeriodMonths: Int = 6,
            isFavorite: Boolean = false,
            notes: String? = null
        ): Entry {
            val encryptedPassword = CryptoUtils.encrypt(password)
            val now = System.currentTimeMillis()
            val nextRotationDate = if (rotationEnabled) {
                now + (rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
            } else null
            
            return Entry(
                service = service,
                username = username,
                encryptedPassword = encryptedPassword,
                profile = profile,
                emojiHint = emojiHint,
                rotationEnabled = rotationEnabled,
                rotationPeriodMonths = rotationPeriodMonths,
                nextRotationDate = nextRotationDate,
                createdAt = now,
                lastChanged = now,
                isFavorite = isFavorite,
                notes = notes
            )
        }
    }

    enum class ExpiryStatus { OK, WARNING, CRITICAL, EXPIRED }
}
