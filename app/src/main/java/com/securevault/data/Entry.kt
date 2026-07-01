package com.securevault.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.securevault.utils.CryptoUtils
import java.security.MessageDigest
import java.util.UUID

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "service") val service: String,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "encrypted_password") val encryptedPassword: String,
    @ColumnInfo(name = "profile_id") val profileId: Int,
    @ColumnInfo(name = "url") val url: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "text_hint") val textHint: String? = null,
    @ColumnInfo(name = "rotation_enabled") val rotationEnabled: Boolean = false,
    @ColumnInfo(name = "rotation_period_months") val rotationPeriodMonths: Int = 6,
    @ColumnInfo(name = "next_rotation_date") val nextRotationDate: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_changed") val lastChanged: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "password_history_json") val passwordHistoryJson: String? = null,
    @ColumnInfo(name = "generation_type") val generationType: String = "random"
) {
    val password: String get() = CryptoUtils.decrypt(encryptedPassword)

    fun getPasswordHistory(): List<PasswordHistoryItem> {
        if (passwordHistoryJson.isNullOrBlank()) return emptyList()
        
        val result = mutableListOf<PasswordHistoryItem>()
        
        try {
            val entries = passwordHistoryJson.split("|")
            for (entry in entries) {
                if (entry.isBlank()) continue
                val parts = entry.split(":")
                if (parts.size >= 2) {
                    val hash = parts[0]
                    val date = parts[1].toLongOrNull() ?: 0L
                    val type = if (parts.size >= 3) parts[2] else "unknown"
                    result.add(PasswordHistoryItem(hash, date, type))
                }
            }
            return result
        } catch (e: Exception) {
            return try {
                val legacyResult = mutableListOf<PasswordHistoryItem>()
                val regex = Regex("""\{"password":"([^"]+)","date":(\d+)\}""")
                val matches = regex.findAll(passwordHistoryJson)
                for (match in matches) {
                    val oldPassword = match.groupValues[1]
                    val date = match.groupValues[2].toLongOrNull() ?: 0L
                    val hash = hashPassword(oldPassword)
                    legacyResult.add(PasswordHistoryItem(hash, date, "legacy"))
                }
                legacyResult
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    fun addToPasswordHistory(oldPassword: String, generationType: String = "random"): Entry {
        val hash = hashPassword(oldPassword)
        val date = System.currentTimeMillis()
        val newEntry = "$hash:$date:$generationType"
        
        val currentHistory = getPasswordHistory()
        val updatedHistory = mutableListOf<String>()
        updatedHistory.add(newEntry)
        for (item in currentHistory.take(9)) {
            updatedHistory.add("${item.passwordHash}:${item.date}:${item.type}")
        }
        val json = updatedHistory.joinToString("|")
        
        return this.copy(passwordHistoryJson = json)
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun getDaysUntilRotation(): Int? {
        return nextRotationDate?.let { ((it - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt() }
    }

    fun isPasswordExpired(): Boolean = nextRotationDate?.let { System.currentTimeMillis() > it } ?: false

    companion object {
        fun create(
            service: String, username: String, password: String,
            profileId: Int,
            url: String? = null, notes: String? = null,
            textHint: String? = null,
            rotationEnabled: Boolean = false, rotationPeriodMonths: Int = 6,
            isFavorite: Boolean = false,
            generationType: String = "random"
        ): Entry {
            val encryptedPassword = CryptoUtils.encrypt(password)
            val now = System.currentTimeMillis()
            val nextRotationDate = if (rotationEnabled) {
                now + (rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
            } else null

            return Entry(
                service = service, username = username, encryptedPassword = encryptedPassword,
                profileId = profileId,
                url = url, notes = notes,
                textHint = textHint,
                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationPeriodMonths,
                nextRotationDate = nextRotationDate, createdAt = now, lastChanged = now,
                isFavorite = isFavorite,
                generationType = generationType
            )
        }

        // ✅ НОВЫЙ МЕТОД: создание записи с новым ID для импорта
        fun createWithNewId(
            service: String, username: String, encryptedPassword: String,
            profileId: Int,
            url: String? = null, notes: String? = null,
            textHint: String? = null,
            isFavorite: Boolean = false,
            rotationEnabled: Boolean = false, rotationPeriodMonths: Int = 6,
            nextRotationDate: Long? = null,
            createdAt: Long = System.currentTimeMillis(),
            lastChanged: Long = System.currentTimeMillis(),
            passwordHistoryJson: String? = null,
            generationType: String = "random"
        ): Entry {
            return Entry(
                id = UUID.randomUUID().toString(),
                service = service, username = username, encryptedPassword = encryptedPassword,
                profileId = profileId,
                url = url, notes = notes,
                textHint = textHint,
                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationPeriodMonths,
                nextRotationDate = nextRotationDate, createdAt = createdAt, lastChanged = lastChanged,
                isFavorite = isFavorite,
                passwordHistoryJson = passwordHistoryJson,
                generationType = generationType
            )
        }
    }
}

data class PasswordHistoryItem(
    val passwordHash: String,
    val date: Long,
    val type: String = "unknown"
)
