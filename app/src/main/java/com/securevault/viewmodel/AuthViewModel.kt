package com.securevault.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.security.MasterPasswordHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthState {
    UNINITIALIZED,  // Нет мастер-пароля, нужна SetupScreen
    LOCKED,         // Мастер-пароль есть, нужен ввод
    UNLOCKED        // Приложение открыто
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow(checkInitialState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Проверяем, нужно ли принудительно запросить мастер-пароль (раз в неделю)
        if (isBiometricLoginEnabled() && !isMasterPasswordRequired()) {
            // Можно использовать биометрию
        }
    }

    private fun checkInitialState(): AuthState {
        val hasMasterPassword = prefs.contains("master_hash")
        val isUnlocked = prefs.getBoolean("is_unlocked", false)
        
        return when {
            !hasMasterPassword -> AuthState.UNINITIALIZED
            isUnlocked -> AuthState.UNLOCKED
            else -> AuthState.LOCKED
        }
    }

    fun attemptUnlock(password: String): Boolean {
        val storedHash = prefs.getString("master_hash", null)
        val storedSalt = prefs.getString("master_salt", null)
        val iterations = prefs.getInt("master_iterations", 100_000)

        return if (storedHash != null && storedSalt != null &&
            MasterPasswordHasher.verify(password, storedHash, storedSalt, iterations)) {
            _authState.value = AuthState.UNLOCKED
            prefs.edit().putBoolean("is_unlocked", true).apply()
            // Обновляем время последнего подтверждения мастер-паролем
            prefs.edit().putLong("last_master_password_confirmed_at", System.currentTimeMillis()).apply()
            true
        } else {
            false
        }
    }

    fun setupMasterPassword(password: String) {
        val salt = MasterPasswordHasher.generateSalt()
        val hash = MasterPasswordHasher.hash(password, salt, 100_000)

        prefs.edit()
            .putString("master_hash", hash)
            .putString("master_salt", String(salt))
            .putInt("master_iterations", 100_000)
            .putBoolean("is_unlocked", true)
            .putLong("last_master_password_confirmed_at", System.currentTimeMillis())
            .apply()

        _authState.value = AuthState.UNLOCKED
    }

    fun changeMasterPassword(oldPassword: String, newPassword: String): Boolean {
        val storedHash = prefs.getString("master_hash", null)
        val storedSalt = prefs.getString("master_salt", null)
        val iterations = prefs.getInt("master_iterations", 100_000)

        // Проверяем старый пароль
        if (storedHash == null || storedSalt == null ||
            !MasterPasswordHasher.verify(oldPassword, storedHash, storedSalt, iterations)) {
            return false
        }

        // Устанавливаем новый пароль
        val newSalt = MasterPasswordHasher.generateSalt()
        val newHash = MasterPasswordHasher.hash(newPassword, newSalt, 100_000)

        prefs.edit()
            .putString("master_hash", newHash)
            .putString("master_salt", String(newSalt))
            .putLong("last_master_password_confirmed_at", System.currentTimeMillis())
            .apply()

        return true
    }

    fun lock() {
        _authState.value = AuthState.LOCKED
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
