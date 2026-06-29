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
        val daysLeft = getDaysUntilRotation
