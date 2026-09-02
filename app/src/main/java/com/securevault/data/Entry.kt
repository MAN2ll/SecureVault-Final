package com.securevault.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0, // Или String, в зависимости от твоей старой версии. Оставь как было.
    
    @ColumnInfo(name = "profile_id")
    val profileId: Int,
    
    @ColumnInfo(name = "service")
    val service: String,
    
    @ColumnInfo(name = "username")
    val username: String,
    
    @ColumnInfo(name = "encrypted_password")
    val encryptedPassword: String,
    
    @ColumnInfo(name = "text_hint")
    val textHint: String? = null,
    
    @ColumnInfo(name = "generation_type")
    val generationType: String = "manual",
    
    @ColumnInfo(name = "mnemonic_phrase_hint")
    val mnemonicPhraseHint: String? = null,
    
    @ColumnInfo(name = "mnemonic_options_json")
    val mnemonicOptionsJson: String? = null,
    
    @ColumnInfo(name = "password_history_json")
    val passwordHistoryJson: String? = null,
    
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    
    @ColumnInfo(name = "rotation_enabled")
    val rotationEnabled: Boolean = false,
    
    @ColumnInfo(name = "rotation_period_months")
    val rotationPeriodMonths: Int = 3,
    
    @ColumnInfo(name = "next_rotation_date")
    val nextRotationDate: Long? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_changed")
    val lastChanged: Long = System.currentTimeMillis(),

    // БЛОК 6: Добавлено поле тегов
    @ColumnInfo(name = "tags_csv")
    val tagsCsv: String = ""
) {
    // БЛОК 6: Вычисляемое свойство для удобной работы с тегами
    val tags: List<String>
        get() = tagsCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun getPasswordHistory(): List<PasswordHistoryItem> {
        return if (passwordHistoryJson.isNullOrBlank()) {
            emptyList()
        } else {
            // Здесь должен быть твой JsonUtils.fromJson
            // Пример: JsonUtils.fromJson(passwordHistoryJson, object : TypeToken<List<PasswordHistoryItem>>() {}.type) ?: emptyList()
            emptyList() // ЗАМЕНИ на свой реальный парсинг, если он был
        }
    }

    companion object {
        fun create(
            service: String,
            username: String,
            password: String, // Этот пароль будет зашифрован в ViewModel
            profileId: Int,
            passwordFingerprint: String? = null,
            tagsCsv: String = "" // БЛОК 6: Параметр по умолчанию
        ): Entry {
            return Entry(
                profileId = profileId,
                service = service,
                username = username,
                encryptedPassword = password, // В реальной жизни здесь должен быть вызов CryptoUtils.encrypt
                tagsCsv = tagsCsv,
                createdAt = System.currentTimeMillis(),
                lastChanged = System.currentTimeMillis()
            )
        }
    }
}
