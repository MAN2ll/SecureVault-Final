package com.securevault.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.securevault.utils.CryptoUtils
import org.json.JSONArray
import org.json.JSONObject
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
    @ColumnInfo(name = "generation_type") val generationType: String = "random",
    @ColumnInfo(name = "password_fingerprint") val passwordFingerprint: String? = null,
    @ColumnInfo(name = "mnemonic_phrase_hint") val mnemonicPhraseHint: String? = null,
    @ColumnInfo(name = "mnemonic_options_json") val mnemonicOptionsJson: String? = null
) {
    val password: String get() = CryptoUtils.decrypt(encryptedPassword)

    fun getPasswordHistory(): List<PasswordHistoryItem> {
        if (passwordHistoryJson.isNullOrBlank()) return emptyList()

        return try {
            val jsonArray = JSONArray(passwordHistoryJson)
            val result = mutableListOf<PasswordHistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(
                    PasswordHistoryItem(
                        encryptedOldPassword = obj.optString("encryptedOldPassword").takeIf { it != "null" && it.isNotEmpty() },
                        encryptedNewPassword = obj.optString("encryptedNewPassword").takeIf { it != "null" && it.isNotEmpty() },
                        passwordHash = obj.optString("passwordHash", ""),
                        passwordFingerprint = obj.optString("passwordFingerprint", ""),
                        date = obj.optLong("date", 0L),
                        type = obj.optString("type", "unknown"),
                        relatedService = obj.optString("relatedService").takeIf { it != "null" && it.isNotEmpty() },
                        relatedEntryId = obj.optString("relatedEntryId").takeIf { it != "null" && it.isNotEmpty() },
                        hint = obj.optString("hint").takeIf { it != "null" && it.isNotEmpty() }
                    )
                )
            }
            result
        } catch (e: Exception) {
            parseLegacyHistory(passwordHistoryJson)
        }
    }

    private fun parseLegacyHistory(json: String): List<PasswordHistoryItem> {
        val result = mutableListOf<PasswordHistoryItem>()
        try {
            val entries = json.split("|")
            for (entry in entries) {
                if (entry.isBlank()) continue
                val parts = entry.split(":")
                if (parts.size >= 2) {
                    val hash = parts[0]
                    val date = parts[1].toLongOrNull() ?: 0L
                    val type = if (parts.size >= 3) parts[2] else "unknown"
                    result.add(
                        PasswordHistoryItem(
                            passwordHash = hash,
                            passwordFingerprint = "",
                            date = date,
                            type = type
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return result
    }

    //  fingerprint передаётся извне (из ViewModel, где есть Context)
    fun addToPasswordHistory(
        oldPassword: String,
        generationType: String,
        oldPasswordFingerprint: String,
        relatedService: String? = null,
        relatedEntryId: String? = null,
        hint: String? = null
    ): Entry {
        val encryptedOld = try {
            CryptoUtils.encrypt(oldPassword)
        } catch (e: Exception) {
            null
        }

        val newItem = PasswordHistoryItem(
            encryptedOldPassword = encryptedOld,
            passwordHash = oldPasswordFingerprint,  // используем fingerprint как hash
            passwordFingerprint = oldPasswordFingerprint,
            date = System.currentTimeMillis(),
            type = generationType,
            relatedService = relatedService,
            relatedEntryId = relatedEntryId,
            hint = hint
        )

        val currentHistory = getPasswordHistory().toMutableList()
        currentHistory.add(0, newItem)
        val trimmed = currentHistory.take(10)

        val jsonArray = JSONArray()
        for (item in trimmed) {
            val obj = JSONObject()
            obj.put("encryptedOldPassword", item.encryptedOldPassword ?: JSONObject.NULL)
            obj.put("passwordHash", item.passwordHash)
            obj.put("passwordFingerprint", item.passwordFingerprint)
            obj.put("date", item.date)
            obj.put("type", item.type)
            obj.put("relatedService", item.relatedService ?: JSONObject.NULL)
            obj.put("relatedEntryId", item.relatedEntryId ?: JSONObject.NULL)
            obj.put("hint", item.hint ?: JSONObject.NULL)
            jsonArray.put(obj)
        }

        return this.copy(passwordHistoryJson = jsonArray.toString())
    }

    fun getDaysUntilRotation(): Int? {
        return nextRotationDate?.let { ((it - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt() }
    }

    fun isPasswordExpired(): Boolean = nextRotationDate?.let { System.currentTimeMillis() > it } ?: false

    companion object {
        // fingerprint передаётся как параметр (строится во ViewModel)
        fun create(
            service: String,
            username: String,
            password: String,
            profileId: Int,
            passwordFingerprint: String,  // обязательный параметр
            url: String? = null,
            notes: String? = null,
            textHint: String? = null,
            rotationEnabled: Boolean = false,
            rotationPeriodMonths: Int = 6,
            isFavorite: Boolean = false,
            generationType: String = "random",
            mnemonicPhraseHint: String? = null,
            mnemonicOptionsJson: String? = null
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
                profileId = profileId,
                url = url,
                notes = notes,
                textHint = textHint,
                rotationEnabled = rotationEnabled,
                rotationPeriodMonths = rotationPeriodMonths,
                nextRotationDate = nextRotationDate,
                createdAt = now,
                lastChanged = now,
                isFavorite = isFavorite,
                generationType = generationType,
                passwordFingerprint = passwordFingerprint,
                mnemonicPhraseHint = mnemonicPhraseHint,
                mnemonicOptionsJson = mnemonicOptionsJson
            )
        }

        fun createWithNewId(
            service: String,
            username: String,
            encryptedPassword: String,
            profileId: Int,
            url: String? = null,
            notes: String? = null,
            textHint: String? = null,
            isFavorite: Boolean = false,
            rotationEnabled: Boolean = false,
            rotationPeriodMonths: Int = 6,
            nextRotationDate: Long? = null,
            createdAt: Long = System.currentTimeMillis(),
            lastChanged: Long = System.currentTimeMillis(),
            passwordHistoryJson: String? = null,
            generationType: String = "random",
            passwordFingerprint: String? = null,
            mnemonicPhraseHint: String? = null,
            mnemonicOptionsJson: String? = null
        ): Entry {
            return Entry(
                id = UUID.randomUUID().toString(),
                service = service,
                username = username,
                encryptedPassword = encryptedPassword,
                profileId = profileId,
                url = url,
                notes = notes,
                textHint = textHint,
                rotationEnabled = rotationEnabled,
                rotationPeriodMonths = rotationPeriodMonths,
                nextRotationDate = nextRotationDate,
                createdAt = createdAt,
                lastChanged = lastChanged,
                isFavorite = isFavorite,
                passwordHistoryJson = passwordHistoryJson,
                generationType = generationType,
                passwordFingerprint = passwordFingerprint,
                mnemonicPhraseHint = mnemonicPhraseHint,
                mnemonicOptionsJson = mnemonicOptionsJson
            )
        }

        fun extractShortPhrase(textHint: String?): String? {
            if (textHint.isNullOrBlank()) return null
            val parts = textHint.split("+")
            val first = parts.firstOrNull()?.trim() ?: return null
            return first.replace(Regex("\\s*\\(.*?\\)\\s*"), "").trim().takeIf { it.isNotBlank() }
        }
    }
}

data class PasswordHistoryItem(
    val encryptedOldPassword: String? = null,
    val encryptedNewPassword: String? = null,
    val passwordHash: String = "",
    val passwordFingerprint: String = "",
    val date: Long = 0L,
    val type: String = "unknown",
    val relatedService: String? = null,
    val relatedEntryId: String? = null,
    val hint: String? = null
)
