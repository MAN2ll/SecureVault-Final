package com.securevault.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.securevault.security.MasterPasswordHasher

@HiltViewModel
class AuthViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    sealed class AuthState {
        object SetupRequired : AuthState()
        object Locked : AuthState()
        object Unlocked : AuthState()
        data class BruteForceLocked(val remainingMillis: Long) : AuthState()
    }

    private val _remainingMillis = MutableStateFlow(0L)
    val remainingMillis: StateFlow<Long> = _remainingMillis.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(checkInitialState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    //  Событие для очистки чувствительных данных (паролей, диалогов)
    private val _clearSensitiveEvent = MutableSharedFlow<Unit>()
    val clearSensitiveEvent: SharedFlow<Unit> = _clearSensitiveEvent.asSharedFlow()

    init { updateBruteForceState() }

    private fun checkInitialState(): AuthState {
        val hasMasterPassword = prefs.contains("master_hash")
        val bruteForceUntil = prefs.getLong("brute_force_until", 0L)
        if (bruteForceUntil > System.currentTimeMillis()) {
            val remaining = bruteForceUntil - System.currentTimeMillis()
            _remainingMillis.value = remaining
            return AuthState.BruteForceLocked(remaining)
        }
        return if (!hasMasterPassword) AuthState.SetupRequired else AuthState.Locked
    }

    private fun updateBruteForceState() {
        val bruteForceUntil = prefs.getLong("brute_force_until", 0L)
        val now = System.currentTimeMillis()
        if (bruteForceUntil > now) {
            _remainingMillis.value = bruteForceUntil - now
            _authState.value = AuthState.BruteForceLocked(_remainingMillis.value)
        } else if (bruteForceUntil > 0) {
            prefs.edit().remove("brute_force_until").apply()
            _remainingMillis.value = 0L
            _authState.value = AuthState.Locked
        }
    }

    fun attemptUnlock(password: String): Boolean {
        updateBruteForceState()
        if (_authState.value is AuthState.BruteForceLocked) return false
        val storedHash = prefs.getString("master_hash", null)
        val storedSalt = prefs.getString("master_salt", null)
        val iterations = prefs.getInt("master_iterations", 100_000)
        return if (storedHash != null && storedSalt != null && MasterPasswordHasher.verify(password, storedHash, storedSalt, iterations)) {
            prefs.edit().putInt("failed_attempts", 0).apply()
            _authState.value = AuthState.Unlocked
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
            val lockDuration = when (attempts) { 5 -> 30_000L; 6 -> 60_000L; 7 -> 300_000L; else -> 3600_000L }
            editor.putLong("brute_force_until", System.currentTimeMillis() + lockDuration)
        }
        editor.apply()
        updateBruteForceState()
    }

    fun setupMasterPassword(password: String) {
        val hashResult = MasterPasswordHasher.hash(password)
        prefs.edit()
            .putString("master_hash", hashResult.hash).putString("master_salt", hashResult.salt)
            .putInt("master_iterations", 100_000).putInt("failed_attempts", 0)
            .putLong("last_master_password_confirmed_at", System.currentTimeMillis()).apply()
        _authState.value = AuthState.Unlocked
    }

    fun lock() {
        _authState.value = AuthState.Locked
        prefs.edit().putBoolean("is_unlocked", false).apply()
        viewModelScope.launch { _clearSensitiveEvent.emit(Unit) } //  Оповещаем UI
    }

    fun unlockWithBiometric(): Boolean {
        if (isMasterPasswordRequired()) return false
        _authState.value = AuthState.Unlocked
        return true
    }

    fun isBiometricLoginEnabled(): Boolean = prefs.getBoolean("biometric_login_enabled", false)
    fun setBiometricLoginEnabled(enabled: Boolean) = prefs.edit().putBoolean("biometric_login_enabled", enabled).apply()

    fun isMasterPasswordRequired(): Boolean {
        val lastConfirmed = prefs.getLong("last_master_password_confirmed_at", 0L)
        return (System.currentTimeMillis() - lastConfirmed) >= (7L * 24 * 60 * 60 * 1000)
    }
}
