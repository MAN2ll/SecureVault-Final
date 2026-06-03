package com.securevault.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.securevault.data.VaultRepository
import com.securevault.security.DataWiper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VaultRepository,
    private val dataWiper: DataWiper
) : ViewModel() {

    sealed class AuthState {
        object Idle : AuthState()
        object SetupRequired : AuthState()  // ✅ ДОБАВЛЕНО
        object Success : AuthState()
        data class Failed(val attempts: Int) : AuthState()
        data class Blocked(val remainingMs: Long, val isWipe: Boolean = false) : AuthState()
        object WipeTriggered : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _wipeTriggered = MutableStateFlow(false)
    val wipeTriggered: StateFlow<Boolean> = _wipeTriggered.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var lockoutUntil: Long = 0

    init {
        initPrefs()
        checkSetupState()  // ✅ ПРОВЕРКА ПРИ ИНИЦИАЛИЗАЦИИ
    }

    private fun initPrefs() {
        if (prefs == null) {
            try {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                prefs = EncryptedSharedPreferences.create(
                    "secure_prefs",
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ✅ ПРОВЕРКА: завершена ли настройка
    private fun checkSetupState() {
        initPrefs()
        val isSetup = prefs?.getString("master_hash", null) != null
        _authState.value = if (isSetup) AuthState.Idle else AuthState.SetupRequired
    }

    fun isSetupComplete(): Boolean {
        initPrefs()
        return prefs?.getString("master_hash", null) != null
    }

    fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun setupMasterPassword(password: String) {
        prefs?.edit()?.putString("master_hash", hashPassword(password))?.apply()
        _authState.value = AuthState.Success
    }

    fun verifyPassword(password: String): Boolean {
        if (System.currentTimeMillis() < lockoutUntil) {
            val remaining = lockoutUntil - System.currentTimeMillis()
            _authState.value = AuthState.Blocked(remaining)
            return false
        }

        val savedHash = prefs?.getString("master_hash", null)
        val currentHash = hashPassword(password)

        if (savedHash == currentHash) {
            _failedAttempts.value = 0
            _authState.value = AuthState.Success
            return true
        } else {
            _failedAttempts.value++
            val attempts = _failedAttempts.value

            if (attempts >= 10) {
                _authState.value = AuthState.Blocked(0, isWipe = true)
            } else if (attempts >= 7) {
                lockoutUntil = System.currentTimeMillis() + 60000
                _authState.value = AuthState.Blocked(60000)
            } else if (attempts >= 5) {
                lockoutUntil = System.currentTimeMillis() + 30000
                _authState.value = AuthState.Blocked(30000)
            } else {
                _authState.value = AuthState.Failed(attempts)
            }
            return false
        }
    }

    fun tryUnlock(password: String): Boolean {
        return verifyPassword(password)
    }

    suspend fun triggerWipe(): DataWiper.WipeResult {
        _wipeTriggered.value = true
        _authState.value = AuthState.WipeTriggered
        return dataWiper.wipeAllData()
    }

    fun resetSecurity() {
        _failedAttempts.value = 0
        lockoutUntil = 0
        _authState.value = AuthState.Idle
    }

    fun formatLockoutTime(ms: Long): String {
        val seconds = ms / 1000
        return "${seconds}с"
    }

    fun lock() {
        _authState.value = AuthState.Idle
    }
        // ✅ НОВЫЙ МЕТОД: смена мастер-пароля
    fun changeMasterPassword(oldPassword: String, newPassword: String): Boolean {
        // Проверяем старый пароль
        if (!verifyPassword(oldPassword)) {
            return false
        }
        // Сохраняем новый
        prefs?.edit()?.putString("master_hash", hashPassword(newPassword))?.apply()
        return true
    }
}
