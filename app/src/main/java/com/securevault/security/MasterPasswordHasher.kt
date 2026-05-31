package com.securevault.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.securevault.security.BruteForceGuard
import com.securevault.security.DataWiper
import com.securevault.security.MasterPasswordHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bruteForceGuard: BruteForceGuard,
    private val dataWiper: DataWiper,
    private val hasher: MasterPasswordHasher
) : ViewModel() {

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_MASTER_HASH = "master_hash"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
    }

    // Инициализация зашифрованных настроек
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

    // Состояния UI
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

    /**
     * Загружает текущее состояние защиты (попытки, блокировка)
     */
    private fun loadSecurityState() {
        try {
            _failedAttempts.value = bruteForceGuard.getFailedAttempts()
            _lockedUntil.value = bruteForceGuard.getLockedUntil()
            _wipeTriggered.value = bruteForceGuard.isWipeTriggered()
        } catch (e: Exception) {
            // Игнорируем ошибки при загрузке, чтобы не крашиться
        }
    }

    /**
     * Проверка: завершена ли первоначальная настройка (задан ли мастер-пароль)
     */
    fun isSetupComplete(): Boolean {
        return try {
            prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Сохранение хеша мастер-пароля при первом запуске
     * @param password Пароль пользователя (очищается внутри метода)
     * @return true если успешно сохранено
     */
    fun setupMasterPassword(password: String): Boolean {
        return try {
            if (password.length < 4) return false
            
            // Хешируем и сохраняем
            val passwordArray = password.toCharArray()
            val hash = hasher.hash(passwordArray)
            hasher.clearCharArray(passwordArray) // 🔒 Очистка пароля из памяти
            
            prefs.edit()
                .putString(KEY_MASTER_HASH, hash)
                .putBoolean(KEY_SETUP_COMPLETE, true)
                .apply()
            
            // Сбрасываем защиту после успешной настройки
            bruteForceGuard.resetAttempts()
            loadSecurityState()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Проверка введённого пароля
     * @param password Введённый пользователем пароль
     * @return true если пароль верный
     */
    fun verifyPassword(password: String): Boolean {
        return try {
            // 1. Проверка блокировки
            if (!bruteForceGuard.shouldAllowAttempt()) {
                _authState.value = AuthState.Blocked(
                    bruteForceGuard.getRemainingLockoutTime(),
                    bruteForceGuard.isWipeTriggered()
                )
                return false
            }

            // 2. Получаем сохранённый хеш
            val storedHash = prefs.getString(KEY_MASTER_HASH, null)
            if (storedHash.isNullOrEmpty()) {
                _authState.value = AuthState.Error("Мастер-пароль не настроен")
                return false
            }

            // 3. Проверяем пароль
            val passwordArray = password.toCharArray()
            val isValid = hasher.verify(passwordArray, storedHash)
            
            // 🔒 Обязательно очищаем пароль из памяти сразу после проверки
            hasher.clearCharArray(passwordArray)
            
            if (isValid) {
                // ✅ Успех: сбрасываем счётчики
                bruteForceGuard.resetAttempts()
                _authState.value = AuthState.Success
                loadSecurityState()
            } else {
                // ❌ Ошибка: инкремент попыток
                bruteForceGuard.incrementAttempts()
                _failedAttempts.value = bruteForceGuard.getFailedAttempts()
                _lockedUntil.value = bruteForceGuard.getLockedUntil()
                _wipeTriggered.value = bruteForceGuard.isWipeTriggered()
                
                _authState.value = when {
                    bruteForceGuard.isWipeTriggered() -> AuthState.WipeTriggered
                    bruteForceGuard.isLocked() -> AuthState.Blocked(
                        bruteForceGuard.getRemainingLockoutTime(),
                        false
                    )
                    else -> AuthState.Failed(bruteForceGuard.getFailedAttempts())
                }
            }
            
            isValid
        } catch (e: Exception) {
            // 🛡️ Ловим любые исключения, чтобы приложение не упало
            _authState.value = AuthState.Error(e.message ?: "Ошибка проверки")
            false
        }
    }

    /**
     * Сброс счётчика неудачных попыток (после успешного входа или вручную)
     */
    fun resetSecurity() {
        try {
            bruteForceGuard.resetAttempts()
            loadSecurityState()
        } catch (e: Exception) {
            // Игнорируем
        }
    }

    /**
     * 🔥 Полная очистка данных (самоуничтожение)
     */
    suspend fun triggerWipe(): DataWiper.WipeResult {
        return try {
            val result = dataWiper.wipeAllData()
            if (result is DataWiper.WipeResult.Success) {
                // Очищаем настройки авторизации
                dataWiper.markWipeConfirmed()
                prefs.edit()
                    .remove(KEY_MASTER_HASH)
                    .remove(KEY_SETUP_COMPLETE)
                    .apply()
                bruteForceGuard.resetAttempts()
                loadSecurityState()
            }
            result
        } catch (e: Exception) {
            DataWiper.WipeResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Возвращает оставшееся время блокировки в миллисекундах
     */
    fun getRemainingLockoutTime(): Long {
        return try {
            bruteForceGuard.getRemainingLockoutTime()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Форматирует время блокировки в человекочитаемый вид
     */
    fun formatLockoutTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        return when {
            seconds < 60 -> "$seconds сек"
            seconds < 3600 -> "${seconds / 60} мин"
            else -> "${seconds / 3600} час"
        }
    }

    /**
     * Состояния экрана авторизации для UI
     */
    sealed class AuthState {
        object Idle : AuthState()              // Ожидание ввода
        object Success : AuthState()           // Успешный вход
        data class Failed(val attempts: Int) : AuthState()  // Неверный пароль
        data class Blocked(val remainingMs: Long, val isWipe: Boolean) : AuthState() // Временная блокировка
        object WipeTriggered : AuthState()     // Превышено макс. попыток
        data class Error(val message: String) : AuthState() // Системная ошибка
    }
}
