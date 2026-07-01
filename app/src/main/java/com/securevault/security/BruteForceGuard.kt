package com.securevault.security

import android.content.Context
import android.content.SharedPreferences

object BruteForceGuard {

    private const val PREFS_NAME = "brute_force_guard"
    private const val KEY_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    private const val KEY_TOTAL_FAILURES = "total_failures"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getFailedAttempts(): Int = prefs.getInt(KEY_ATTEMPTS, 0)
    fun getTotalFailures(): Int = prefs.getInt(KEY_TOTAL_FAILURES, 0)
    fun getLockoutUntil(): Long = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)

    fun isLockedOut(): Boolean {
        val lockoutUntil = getLockoutUntil()
        return lockoutUntil > System.currentTimeMillis()
    }

    fun getRemainingLockoutMillis(): Long {
        val lockoutUntil = getLockoutUntil()
        return (lockoutUntil - System.currentTimeMillis()).coerceAtLeast(0)
    }

    fun recordFailedAttempt() {
        val attempts = getFailedAttempts() + 1
        prefs.edit().putInt(KEY_ATTEMPTS, attempts).apply()
        prefs.edit().putInt(KEY_TOTAL_FAILURES, getTotalFailures() + 1).apply()

        // Прогрессивная блокировка
        val lockoutMillis = when {
            attempts >= 10 -> 0L // Сброс данных после 10 попыток
            attempts >= 7 -> 60_000L // 1 минута
            attempts >= 5 -> 30_000L // 30 секунд
            else -> 0L
        }

        if (lockoutMillis > 0) {
            prefs.edit().putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + lockoutMillis).apply()
        }
    }

    fun resetAttempts() {
        prefs.edit()
            .putInt(KEY_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0)
            .apply()
    }

    fun shouldWipeData(): Boolean = getFailedAttempts() >= 10

    fun getAttemptsWarning(): String? {
        val attempts = getFailedAttempts()
        return when {
            attempts >= 10 -> "Слишком много попыток. Данные будут удалены."
            attempts >= 7 -> "Блокировка на 1 минуту."
            attempts >= 5 -> "Блокировка на 30 секунд."
            else -> null
        }
    }
}
