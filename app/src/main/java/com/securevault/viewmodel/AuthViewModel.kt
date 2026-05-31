package com.securevault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.security.BruteForceGuard
import com.securevault.security.DataWiper
import com.securevault.security.MasterPasswordHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val hasher: MasterPasswordHasher,
    private val bruteForceGuard: BruteForceGuard,
    private val dataWiper: DataWiper
) : ViewModel() {

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _lockedUntil = MutableStateFlow(0L)
    val lockedUntil: StateFlow<Long> = _lockedUntil.asStateFlow()

    private val _wipeTriggered = MutableStateFlow(false)
    val wipeTriggered: StateFlow<Boolean> = _wipeTriggered.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        loadSecurityState()
    }

    private fun loadSecurityState() {
        _failedAttempts.value = bruteForceGuard.getFailedAttempts()
        _lockedUntil.value = bruteForceGuard.getLockedUntil()
        _wipeTriggered.value = bruteForceGuard.isWipeTriggered()
    }

    fun verifyPassword(password: CharArray, storedHash: String): Boolean {
        if (!bruteForceGuard.shouldAllowAttempt()) {
            _authState.value = AuthState.Blocked(
                bruteForceGuard.getRemainingLockoutTime(),
                bruteForceGuard.isWipeTriggered()
            )
            return false
        }

        val isValid = hasher.verify(password, storedHash)
        
        if (isValid) {
            bruteForceGuard.resetAttempts()
            _authState.value = AuthState.Success
            loadSecurityState()
        } else {
            bruteForceGuard.incrementAttempts()
            _failedAttempts.value = bruteForceGuard.getFailedAttempts()
            _lockedUntil.value = bruteForceGuard.getLockedUntil()
            _wipeTriggered.value = bruteForceGuard.isWipeTriggered()
            
            _authState.value = if (bruteForceGuard.isWipeTriggered()) {
                AuthState.WipeTriggered
            } else if (bruteForceGuard.isLocked()) {
                AuthState.Blocked(
                    bruteForceGuard.getRemainingLockoutTime(),
                    false
                )
            } else {
                AuthState.Failed(bruteForceGuard.getFailedAttempts())
            }
        }
        
        hasher.clearCharArray(password)
        return isValid
    }

    fun saveMasterPassword(password: CharArray): String {
        return hasher.hash(password).also {
            hasher.clearCharArray(password)
        }
    }

    fun resetSecurity() {
        bruteForceGuard.resetAttempts()
        loadSecurityState()
    }

    suspend fun triggerWipe(): DataWiper.WipeResult {
        val result = dataWiper.wipeAllData()
        if (result is DataWiper.WipeResult.Success) {
            dataWiper.markWipeConfirmed()
            bruteForceGuard.resetAttempts()
            loadSecurityState()
        }
        return result
    }

    fun getRemainingLockoutTime(): Long {
        return bruteForceGuard.getRemainingLockoutTime()
    }

    fun formatLockoutTime(milliseconds: Long): String {
        return bruteForceGuard.formatLockoutTime(milliseconds)
    }

    sealed class AuthState {
        object Idle : AuthState()
        object Success : AuthState()
        data class Failed(val attempts: Int) : AuthState()
        data class Blocked(val remainingMs: Long, val isWipe: Boolean) : AuthState()
        object WipeTriggered : AuthState()
    }
}
