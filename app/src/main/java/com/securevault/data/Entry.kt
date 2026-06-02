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
    @ColumnInfo(name = "category") val category: String = "Общее",
    @ColumnInfo(name = "url") val url: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "emoji_hint") val emojiHint: String? = null,
    @ColumnInfo(name = "text_hint") val textHint: String? = null,
    @ColumnInfo(name = "quick_tags") val quickTags: String? = null,
    @ColumnInfo(name = "rotation_enabled") val rotationEnabled: Boolean = false,
    @ColumnInfo(name = "rotation_period_months") val rotationPeriodMonths: Int = 6,
    @ColumnInfo(name = "next_rotation_date") val nextRotationDate: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_changed") val lastChanged: Long = System.currentTimeMillis()
) {
    val password: String get() = CryptoUtils.decrypt(encryptedPassword)

    fun getDaysUntilRotation(): Int? {
        return nextRotationDate?.let { ((it - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt() }
    }

    fun isPasswordExpired(): Boolean = nextRotationDate?.let { System.currentTimeMillis() > it } ?: false

    fun getExpiryStatus(): ExpiryStatus {
        if (!rotationEnabled || nextRotationDate == null) return ExpiryStatus.OK
        val daysLeft = getDaysUntilRotation() ?: return ExpiryStatus.OK
        return when {
            daysLeft < 0 -> ExpiryStatus.EXPIRED
            daysLeft <= 3 -> ExpiryStatus.CRITICAL
            daysLeft <= 7 -> ExpiryStatus.WARNING
            else -> ExpiryStatus.OK
        }
    }

    companion object {
        fun create(
            service: String, username: String, password: String,
            profile: Profile = Profile.PERSONAL, category: String = "Общее",
            url: String? = null, notes: String? = null,
            emojiHint: String? = null, textHint: String? = null, quickTags: String? = null,
            rotationEnabled: Boolean = false, rotationPeriodMonths: Int = 6,
            isFavorite: Boolean = false
        ): Entry {
            val encryptedPassword = CryptoUtils.encrypt(password)
            val now = System.currentTimeMillis()
            val nextRotationDate = if (rotationEnabled) {
                now + (rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
            } else null
            
            return Entry(
                service = service, username = username, encryptedPassword = encryptedPassword,
                profile = profile, category = category, url = url, notes = notes,
                emojiHint = emojiHint, textHint = textHint, quickTags = quickTags,
                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationPeriodMonths,
                nextRotationDate = nextRotationDate, createdAt = now, lastChanged = now,
                isFavorite = isFavorite
            )
        }
    }

    enum class ExpiryStatus { OK, WARNING, CRITICAL, EXPIRED }
}

enum class Profile(val label: String) {
    PERSONAL("Личное"), WORK("Работа")
}

object Categories {
    val PERSONAL = listOf("Общее", "Финансы", "Соцсети", "Почта", "Развлечения", "Покупки")
    val WORK = listOf("Общее", "Корпоративные", "Проекты", "Сервисы", "Документы")
    fun getFor(profile: Profile) = if (profile == Profile.PERSONAL) PERSONAL else WORK
}

object QuickTags {
    val TAGS = listOf(
        "🐱 кот", "🏠 дом", "💼 работа", "🚗 машина",
        "🔑 ключ", "⭐ звезда", "❤️ сердце", "🔥 огонь",
        "📚 книга", "📱 телефон", "💰 деньги", "🏦 банк"
    )
}
