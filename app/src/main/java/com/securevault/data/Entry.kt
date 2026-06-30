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
    @ColumnInfo(name = "profile_id") val profileId: Int, // ✅ Теперь ID профиля
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
                items.add(PasswordHistoryItem(match.groupValues[1], match.groupValues[2].toLongOrNull() ?: 0L))
            }
            items
        } catch (e: Exception) { emptyList() }
    }

    fun addToPasswordHistory(oldPassword: String): Entry {
        val history = getPasswordHistory().toMutableList()
        history.add(0, PasswordHistoryItem(oldPassword, System.currentTimeMillis()))
        val json = history.take(10).joinToString(",", "[", "]") { """{"password":"${it.password}","date":${it.date}}""" }
        return this.copy(passwordHistoryJson = json)
    }

    fun getDaysUntilRotation(): Int? = nextRotationDate?.let { ((it - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt() }
    fun isPasswordExpired(): Boolean = nextRotationDate?.let { System.currentTimeMillis() > it } ?: false

    companion object {
        fun create(
            service: String, username: String, password: String,
            profileId: Int, // ✅ Теперь обязательный параметр
            url: String? = null, notes: String? = null,
            textHint: String? = null,
            rotationEnabled: Boolean = false, rotationPeriodMonths: Int = 6,
            isFavorite: Boolean = false
        ): Entry {
            val encryptedPassword = CryptoUtils.encrypt(password)
            val now = System.currentTimeMillis()
            return Entry(
                service = service, username = username, encryptedPassword = encryptedPassword,
                profileId = profileId, url = url, notes = notes, textHint = textHint,
                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationPeriodMonths,
                nextRotationDate = if (rotationEnabled) now + (rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000) else null,
                createdAt = now, lastChanged = now, isFavorite = isFavorite
            )
        }
    }
}

data class PasswordHistoryItem(val password: String, val date: Long)
