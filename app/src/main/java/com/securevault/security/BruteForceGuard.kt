package com.securevault.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BruteForceGuard @Inject constructor(
    @ApplicationContext private val context: Context  //  Добавлена аннотация
) {
    companion object {
        private const val PREFS_NAME = "brute_force_prefs"
        private const val KEY_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKED_UNTIL = "locked_until"
        private const val KEY_WIPE_TRIGGERED = "wipe_triggered"
        
        private const val MAX_ATTEMPTS = 10
        private const val LOCKOUT_1_MIN = 60_000L
        private const val LOCKOUT_5_MIN = 300_000L
        private const val LOCKOUT_30_MIN = 1_800_000L
        private const val LOCKOUT_PERMANENT = Long.MAX_VALUE
    }

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getFailedAttempts(): Int {
        return prefs.getInt(KEY_ATTEMPTS, 0)
    }

    fun getLockedUntil(): Long {
        return prefs.getLong(KEY_LOCKED_UNTIL, 0L)
    }

    fun isLocked(): Boolean {
        val lockedUntil = getLockedUntil()
        return lockedUntil > 0L && System.currentTimeMillis() < lockedUntil
    }

    fun isWipeTriggered(): Boolean {
        return prefs.getBoolean(KEY_WIPE_TRIGGERED, false)
    }

    fun incrementAttempts() {
        val current = getFailedAttempts()
        val newCount = current + 1
        
        prefs.edit()
            .putInt(KEY_ATTEMPTS, newCount)
            .apply()
        
        when {
            newCount >= MAX_ATTEMPTS -> {
                triggerWipe()
            }
            newCount >= 7 -> {
                setLockout(LOCKOUT_30_MIN)
            }
            newCount >= 5 -> {
                setLockout(LOCKOUT_5_MIN)
            }
            newCount >= 3 -> {
                setLockout(LOCKOUT_1_MIN)
            }
        }
    }

    private fun setLockout(durationMs: Long) {
        val unlockTime = System.currentTimeMillis() + durationMs
        prefs.edit()
            .putLong(KEY_LOCKED_UNTIL, unlockTime)
            .apply()
    }

    private fun triggerWipe() {
        prefs.edit()
            .putLong(KEY_LOCKED_UNTIL, LOCKOUT_PERMANENT)
            .putBoolean(KEY_WIPE_TRIGGERED, true)
            .apply()
    }

    fun resetAttempts() {
        prefs.edit()
            .putInt(KEY_ATTEMPTS, 0)
            .putLong(KEY_LOCKED_UNTIL, 0L)
            .apply()
    }

    fun getRemainingLockoutTime(): Long {
        val lockedUntil = getLockedUntil()
        val now = System.currentTimeMillis()
        return if (lockedUntil > now) {
            lockedUntil - now
        } else {
            0L
        }
    }

    fun formatLockoutTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        return when {
            seconds < 60 -> "$seconds сек"
            seconds < 3600 -> "${seconds / 60} мин"
            else -> "${seconds / 3600} час"
        }
    }

    fun shouldAllowAttempt(): Boolean {
        return !isLocked() && !isWipeTriggered()
    }
}
