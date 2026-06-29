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
    @ColumnInfo(name = "custom_profile_id") val customProfileId: Int? = null,
    @ColumnInfo(name = "url") val url: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "text_hint") val textHint: String? = null,
    @ColumnInfo(name = "rotation_enabled") val rotationEnabled: Boolean = false,
    @ColumnInfo(name = "rotation_period_months") val rotationPeriodMonths: Int = 6,
    @ColumnInfo(name = "next_rotation_date") val nextRotationDate: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_changed") val lastChanged: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "password_history_json") val passwordHistoryJson: String? = null
) {
    val password: String get() = CryptoUtils.decrypt(encryptedPassword)

    fun getPasswordHistory(): List<PasswordHistoryItem> {
        if (passwordHistoryJson.isNullOrBlank()) return emptyList()
        return try {
            val items = mutableListOf<PasswordHistoryItem>()
            val regex = Regex("""\{"password":"([^"]+)","date":(\d+)\}""")
            regex.findAll(passwordHistoryJson).forEach { match ->
                val pwd = match.groupValues[1]
                val date = match.groupValues[2].toLongOrNull() ?: 0L
                items.add(PasswordHistoryItem(pwd, date))
            }
            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addToPasswordHistory(oldPassword: String): Entry {
        val currentHistory = getPasswordHistory().toMutableList()
        currentHistory.add(0, PasswordHistoryItem(oldPassword, System.currentTimeMillis()))
        val limitedHistory = currentHistory.take(10)
        val json = limitedHistory.joinToString(",", "[", "]") { item ->
            """{"password":"${item.password}","date":${item.date}}"""
        }
        return this.copy(passwordHistoryJson = json)
    }

    fun getDaysUntilRotation(): Int? {
        return nextRotationDate?.let { ((it - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt() }
    }

    fun getDaysUntilExpiry(): Int? = getDaysUntilRotation()

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
            profile: Profile = Profile.PERSONAL,
            customProfileId: Int? = null,
            url: String? = null, notes: String? = null,
            textHint: String? = null,
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
                profile = profile, customProfileId = customProfileId,
                url = url, notes = notes,
                textHint = textHint,
                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationPeriodMonths,
                nextRotationDate = nextRotationDate, createdAt = now, lastChanged = now,
                isFavorite = isFavorite
            )
        }
    }

    enum class ExpiryStatus { OK, WARNING, CRITICAL, EXPIRED }
}

data class PasswordHistoryItem(
    val password: String,
    val date: Long
)

enum class Profile(val label: String) {
    PERSONAL("Личное"), WORK("Работа")
}
