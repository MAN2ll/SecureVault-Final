package com.securevault.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.securevault.security.MasterPasswordHasher

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    //  Вложенный enum, как ожидает оригинальный код
    enum class AuthState {
        SetupRequired,
        Locked,
        Unlocked,
        BruteForceLocked
    }

    private val _authState = MutableStateFlow(checkInitialState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _remainingMillis = MutableStateFlow(0L)
    val remainingMillis: StateFlow<Long> = _remainingMillis.asStateFlow()

    init {
        updateBruteForceState()
    }

    //  Метод init() для совместимости с вызовами из SetupScreen/LockScreen
    fun init() {
        updateBruteForceState()
    }

    private fun checkInitialState(): AuthState {
        val hasMasterPassword = prefs.contains("master_hash")
        val isUnlocked = prefs.getBoolean("is_unlocked", false)
        val bruteForceUntil = prefs.getLong("brute_force_until", 0L)
        
        if (bruteForceUntil > System.currentTimeMillis()) {
            return AuthState.BruteForceLocked
        }
        
        return when {
            !hasMasterPassword -> AuthState.SetupRequired
            isUnlocked -> AuthState.Unlocked
            else -> AuthState.Locked
        }
    }

    private fun updateBruteForceState() {
        val bruteForceUntil = prefs.getLong("brute_force_until", 0L)
        val now = System.currentTimeMillis()
        if (bruteForceUntil > now) {
            _authState.value = AuthState.BruteForceLocked
            _remainingMillis.value = bruteForceUntil - now
        } else if (bruteForceUntil > 0) {
            prefs.edit().remove("brute_force_until").apply()
            _authState.value = AuthState.Locked
            _remainingMillis.value = 0L
        }
    }

    fun attemptUnlock(password: String): Boolean {
        updateBruteForceState()
        if (_authState.value == AuthState.BruteForceLocked) return false

        val storedHash = prefs.getString("master_hash", null)
        val storedSalt = prefs.getString("master_salt", null)
        val iterations = prefs.getInt("master_iterations", 100_000)

        return if (storedHash != null && storedSalt != null &&
            MasterPasswordHasher.verify(password, storedHash, storedSalt, iterations)) {
            prefs.edit().putInt("failed_attempts", 0).apply()
            _authState.value = AuthState.Unlocked
            prefs.edit().putBoolean("is_unlocked", true).apply()
            prefs.edit().putLong("last_master_password_confirmed_at", System.currentTimeMillis()).apply()
            true
        } else {
            incrementFailedAttempts()
            false
        }
    }

    private fun incrementFailedAttempts() {
        val attempts = prefs.getInt("failed_attempts", 0) + 1
        val editor = prefs.edit().putInt("failed_attempts", attempts)
        
        if (attempts >= 5) {
            val lockDuration = when (attempts) {
                5 -> 30_000L
                6 -> 60_000L
                7 -> 300_000L
                else -> 3600_000L
            }
            val until = System.currentTimeMillis() + lockDuration
            editor.putLong("brute_force_until", until)
            _remainingMillis.value = lockDuration
            _authState.value = AuthState.BruteForceLocked
        }
        editor.apply()
    }

    fun setupMasterPassword(password: String) {
        //  hash() принимает только password и возвращает HashResult
        val hashResult = MasterPasswordHasher.hash(password)
        prefs.edit()
            .putString("master_hash", hashResult.hash)
            .putString("master_salt", hashResult.salt)
            .putInt("master_iterations", 100_000)
            .putBoolean("is_unlocked", true)
            .putInt("failed_attempts", 0)
            .putLong("last_master_password_confirmed_at", System.currentTimeMillis())
            .apply()

        _authState.value = AuthState.Unlocked
    }

    fun changeMasterPassword(oldPassword: String, newPassword: String): Boolean {
        val storedHash = prefs.getString("master_hash", null)
        val storedSalt = prefs.getString("master_salt", null)
        val iterations = prefs.getInt("master_iterations", 100_000)

        if (storedHash == null || storedSalt == null ||
            !MasterPasswordHasher.verify(oldPassword, storedHash, storedSalt, iterations)) {
            return false
        }

        //  hash() принимает только newPassword
        val hashResult = MasterPasswordHasher.hash(newPassword)
        prefs.edit()
            .putString("master_hash", hashResult.hash)
            .putString("master_salt", hashResult.salt)
            .putLong("last_master_password_confirmed_at", System.currentTimeMillis())
            .apply()

        return true
    }

    fun lock() {
        _authState.value = AuthState.Locked
        prefs.edit().putBoolean("is_unlocked", false).apply()
    }

    fun isBiometricLoginEnabled(): Boolean {
        return prefs.getBoolean("biometric_login_enabled", false)
    }

    fun setBiometricLoginEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_login_enabled", enabled).apply()
    }

    fun isMasterPasswordRequired(): Boolean {
        val lastConfirmed = prefs.getLong("last_master_password_confirmed_at", 0L)
        val sevenDaysInMillis = 7L * 24 * 60 * 60 * 1000
        return (System.currentTimeMillis() - lastConfirmed) >= sevenDaysInMillis
    }
}
