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
    @ColumnInfo(name = "profile") val profile: Profile,
    @ColumnInfo(name = "emoji_hint") val emojiHint: String? = null,
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

    fun isExpired(): Boolean = nextRotationDate?.let { System.currentTimeMillis() > it } ?: false
}
