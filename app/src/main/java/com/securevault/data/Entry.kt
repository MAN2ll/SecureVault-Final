package com.securevault.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.securevault.utils.AccessMode
import com.securevault.utils.CryptoUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// Функция на верхнем уровне для вызовов вида `extractShortPhrase(textHint)`
fun extractShortPhrase(textHint: String?): String? {
    if (textHint.isNullOrBlank()) return null
    val parts = textHint.split("+")
    val first = parts.firstOrNull()?.trim() ?: return null
    return first.replace(Regex("\\s*\\(.*?\\)\\s*"), "").trim().takeIf { it.isNotBlank() }
}

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
    @ColumnInfo(name = "mnemonic_options_json") val mnemonicOptionsJson: String? = null,
    @ColumnInfo(name = "password_access_mode") val passwordAccessMode: String = AccessMode.INHERIT.value,
    
    //  БЛОК 6: Новое поле для тегов (по умолчанию пустая строка)
    @ColumnInfo(name = "tags_csv") val tagsCsv: String = ""
) {
    val password: String get() = CryptoUtils.decrypt(encryptedPassword)

    //  БЛОК 6: Вычисляемое свойство для удобной работы с тегами в UI
    val tags: List<String>
        get() = tagsCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun getPasswordHistory(): List<PasswordHistoryItem> = com.securevault.utils.JsonUtils.readHistory(passwordHistoryJson)

    fun addToPasswordHistory(
        oldPassword: String,
        generationType: String,
        oldPasswordFingerprint: String,
        relatedService: String? = null,
        relatedEntryId: String? = null,
        hint: String? = null
    ): Entry {
        val encryptedOld = CryptoUtils.encrypt(oldPassword)
        val newItem = PasswordHistoryItem(
            encryptedOldPassword = encryptedOld,
            passwordHash = oldPasswordFingerprint,
            passwordFingerprint = oldPasswordFingerprint,
            date = System.currentTimeMillis(),
            type = generationType,
            relatedService = relatedService,
            relatedEntryId = relatedEntryId,
            hint = hint
        )
        val currentHistory = getPasswordHistory().toMutableList()
        currentHistory.add(0, newItem)
        return this.copy(passwordHistoryJson = com.securevault.utils.JsonUtils.toJson(currentHistory))
    }

    fun getDaysUntilRotation(): Int? = nextRotationDate?.let { ((it - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt() }
    fun isPasswordExpired(): Boolean = nextRotationDate?.let { System.currentTimeMillis() > it } ?: false

    companion object {
        // Дублируем в companion object для вызовов вида `Entry.extractShortPhrase(textHint)`
        @JvmStatic
        fun extractShortPhrase(textHint: String?): String? {
            return com.securevault.data.extractShortPhrase(textHint)
        }

        fun create(
            service: String, username: String, password: String, profileId: Int,
            passwordFingerprint: String, url: String? = null, notes: String? = null,
            textHint: String? = null, rotationEnabled: Boolean = false, rotationPeriodMonths: Int = 6,
            isFavorite: Boolean = false, generationType: String = "random",
            mnemonicPhraseHint: String? = null, mnemonicOptionsJson: String? = null,
            passwordAccessMode: String = AccessMode.INHERIT.value,
            tagsCsv: String = "" //  БЛОК 6: Добавлен параметр с дефолтным значением
        ): Entry {
            val encryptedPassword = CryptoUtils.encrypt(password)
            val now = System.currentTimeMillis()
            val nextRotationDate = if (rotationEnabled) now + (rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000) else null
            return Entry(
                service = service, username = username, encryptedPassword = encryptedPassword,
                profileId = profileId, url = url, notes = notes, textHint = textHint,
                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationPeriodMonths,
                nextRotationDate = nextRotationDate, createdAt = now, lastChanged = now,
                isFavorite = isFavorite, generationType = generationType,
                passwordFingerprint = passwordFingerprint, mnemonicPhraseHint = mnemonicPhraseHint,
                mnemonicOptionsJson = mnemonicOptionsJson, passwordAccessMode = passwordAccessMode,
                tagsCsv = tagsCsv //  БЛОК 6: Передан в конструктор
            )
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
