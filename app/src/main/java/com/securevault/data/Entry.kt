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
    
    // Дата создания и последнего изменения
    val createdAt: Long = System.currentTimeMillis(),
    val lastChanged: Long = System.currentTimeMillis(),
    
    // Настройки срока действия пароля
    val changeIntervalDays: Int = 90,
    
    // Поля для защиты от подбора
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0L,
    
    // --- НОВЫЕ ПОЛЯ ДЛЯ МНЕМОНИЧЕСКОГО ГЕНЕРАТОРА ---
    
    // Эмодзи-подсказка (не входит в пароль, хранится отдельно)
    val emojiHint: String? = null,
    
    // Включена ли ротация пароля
    val rotationEnabled: Boolean = false,
    
    // Период ротации в месяцах (3, 6, 12)
    val rotationPeriodMonths: Int = 6,
    
    // Дата последней смены пароля (для расчёта следующей ротации)
    val lastRotationDate: Long? = null,
    
    // История хешей паролей (для проверки уникальности при ротации)
    // Хранится как JSON-строка или список, разделённый запятыми
    val passwordHistoryHashes: String = "" 
) {
    // Геттер для расшифровки пароля
    val password: String
        get() = if (CryptoUtils.isEncrypted(encryptedPassword)) {
            CryptoUtils.decrypt(encryptedPassword)
        } else {
            encryptedPassword
        }

    companion object {
        fun create(
            service: String,
            username: String,
            password: String,
            category: String = "general",
            profile: Profile = Profile.PERSONAL,
            notes: String = "",
            changeIntervalDays: Int = 90,
            emojiHint: String? = null,
            rotationEnabled: Boolean = false,
            rotationPeriodMonths: Int = 6
        ): Entry {
            return Entry(
                service = service,
                username = username,
                encryptedPassword = CryptoUtils.encrypt(password),
                category = category,
                profile = profile,
                notes = notes,
                changeIntervalDays = changeIntervalDays,
                emojiHint = emojiHint,
                rotationEnabled = rotationEnabled,
                rotationPeriodMonths = rotationPeriodMonths,
                lastRotationDate = System.currentTimeMillis()
            )
        }
    }

    // Проверка: просрочен ли пароль по сроку действия
    fun isPasswordExpired(): Boolean {
        val daysSinceChange = (System.currentTimeMillis() - lastChanged) / (1000 * 60 * 60 * 24)
        return daysSinceChange >= changeIntervalDays
    }
    
    // Дней до истечения срока
    fun getDaysUntilExpiry(): Int {
        val daysSinceChange = (System.currentTimeMillis() - lastChanged) / (1000 * 60 * 60 * 24)
        return changeIntervalDays - daysSinceChange.toInt()
    }
    
    // Статус для UI
    fun getExpiryStatus(): ExpiryStatus {
        return when {
            isPasswordExpired() -> ExpiryStatus.EXPIRED
            getDaysUntilExpiry() <= 3 -> ExpiryStatus.CRITICAL
            getDaysUntilExpiry() <= 7 -> ExpiryStatus.WARNING
            else -> ExpiryStatus.OK
        }
    }
    
    // Проверка блокировки
    fun isLocked(): Boolean {
        return System.currentTimeMillis() < lockedUntil
    }

    // --- МЕТОДЫ ДЛЯ РОТАЦИИ ---

    // Нужно ли менять пароль по графику ротации?
    fun shouldRotateBySchedule(): Boolean {
        if (!rotationEnabled || lastRotationDate == null) return false
        val monthsSinceRotation = (System.currentTimeMillis() - lastRotationDate) / 
            (30L * 24 * 60 * 60 * 1000)
        return monthsSinceRotation >= rotationPeriodMonths
    }

    // Добавление хеша текущего пароля в историю
    fun addToHistory(currentPasswordHash: String): Entry {
        val currentList = if (passwordHistoryHashes.isBlank()) {
            emptyList()
        } else {
            passwordHistoryHashes.split(",").filter { it.isNotBlank() }
        }
        
        // Храним последние 10 хешей
        val newList = (currentList + currentPasswordHash).takeLast(10)
        
        return copy(
            passwordHistoryHashes = newList.joinToString(","),
            lastRotationDate = System.currentTimeMillis(),
            lastChanged = System.currentTimeMillis()
        )
    }

    // ... существующие поля ...

    // Дата следующего обновления пароля (для ротации)
    val nextRotationDate: Long? = null,

    // Дней до напоминания (по умолчанию 7)
    val reminderDaysBefore: Int = 7,

    // ... в методе create() добавь:
    nextRotationDate = if (rotationEnabled) {
        System.currentTimeMillis() + (rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
    } else null,

    // ... добавь метод для проверки истечения:
    fun isRotationDue(): Boolean {
        return nextRotationDate != null && System.currentTimeMillis() >= nextRotationDate!!
    }

    fun getDaysUntilRotation(): Int? {
        return nextRotationDate?.let {
            val diff = it - System.currentTimeMillis()
            (diff / (1000 * 60 * 60 * 24)).toInt()
        }
    }

    // Проверка: был ли такой пароль ранее
    fun isPasswordInHistory(newPassword: String): Boolean {
        if (passwordHistoryHashes.isBlank()) return false
        
        val newHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(newPassword.toByteArray())
            .joinToString("") { "%02x".format(it) }
            
        return passwordHistoryHashes.split(",").contains(newHash)
    }
    
    enum class ExpiryStatus {
        OK, WARNING, CRITICAL, EXPIRED
    }
}

enum class Profile {
    PERSONAL,
    WORK
}
