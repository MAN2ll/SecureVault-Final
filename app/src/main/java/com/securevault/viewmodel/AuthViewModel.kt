package com.securevault.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.security.BruteForceGuard
import com.securevault.security.MasterPasswordHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor() : ViewModel() {

    sealed class AuthState {
        object Locked : AuthState()
        object SetupRequired : AuthState()
        object Unlocked : AuthState()
        data class BruteForceLocked(val remainingMillis: Long) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Locked)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private lateinit var prefs: SharedPreferences
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        BruteForceGuard.init(context)
        isInitialized = true

        val hasMasterHash = prefs.getString("master_hash", null) != null
        _authState.value = if (hasMasterHash) AuthState.Locked else AuthState.SetupRequired
    }

    fun setupMasterPassword(password: String) {
        val hashResult = MasterPasswordHasher.hash(password)
        prefs.edit()
            .putString("master_hash", hashResult.hash)
            .putString("master_salt", hashResult.salt)
            .putInt("master_iterations", hashResult.iterations)
            .apply()
        BruteForceGuard.resetAttempts()
        _authState.value = AuthState.Unlocked
    }

    fun attemptUnlock(password: String): Boolean {
        if (BruteForceGuard.isLockedOut()) {
            val remaining = BruteForceGuard.getRemainingLockoutMillis()
            _authState.value = AuthState.BruteForceLocked(remaining)
            return false
        }

        val storedHash = prefs.getString("master_hash", null) ?: return false
        val storedSalt = prefs.getString("master_salt", null) ?: return false
        val iterations = prefs.getInt("master_iterations", 100_000)

        val isValid = MasterPasswordHasher.verify(password, storedHash, storedSalt, iterations)

        if (isValid) {
            BruteForceGuard.resetAttempts()
            _authState.value = AuthState.Unlocked
            return true
        } else {
            BruteForceGuard.recordFailedAttempt()
            
            if (BruteForceGuard.shouldWipeData()) {
                // Сброс данных после 10 неудачных попыток
                prefs.edit().clear().apply()
                _authState.value = AuthState.SetupRequired
            } else {
                val remaining = BruteForceGuard.getRemainingLockoutMillis()
                if (remaining > 0) {
                    _authState.value = AuthState.BruteForceLocked(remaining)
                    viewModelScope.launch {
                        while (BruteForceGuard.getRemainingLockoutMillis() > 0) {
                            delay(1000)
                            _authState.value = AuthState.BruteForceLocked(BruteForceGuard.getRemainingLockoutMillis())
                        }
                        _authState.value = AuthState.Locked
                    }
                } else {
                    _authState.value = AuthState.Locked
                }
            }
            return false
        }
    }

    fun changeMasterPassword(oldPassword: String, newPassword: String): Boolean {
        val storedHash = prefs.getString("master_hash", null) ?: return false
        val storedSalt = prefs.getString("master_salt", null) ?: return false
        val iterations = prefs.getInt("master_iterations", 100_000)

        if (!MasterPasswordHasher.verify(oldPassword, storedHash, storedSalt, iterations)) {
            return false
        }

        setupMasterPassword(newPassword)
        return true
    }

    fun lock() {
        _authState.value = AuthState.Locked
    }
}
