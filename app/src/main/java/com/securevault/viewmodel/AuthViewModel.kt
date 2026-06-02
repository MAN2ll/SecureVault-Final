package com.securevault.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor() : ViewModel() {

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _isSetupRequired = MutableStateFlow(true)
    val isSetupRequired: StateFlow<Boolean> = _isSetupRequired.asStateFlow()

    // В реальном приложении контекст лучше получать через @ApplicationContext, 
    // но для простоты передадим его через метод инициализации или используем безопасный fallback
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            prefs = EncryptedSharedPreferences.create(
                "secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            _isSetupRequired.value = prefs!!.getString("master_hash", null) == null
        }
    }

    fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun setupMasterPassword(password: String) {
        prefs?.edit()?.putString("master_hash", hashPassword(password))?.apply()
        _isSetupRequired.value = false
        _isUnlocked.value = true
    }

    fun tryUnlock(password: String): Boolean {
        val savedHash = prefs?.getString("master_hash", null)
        val currentHash = hashPassword(password)
        
        if (savedHash == currentHash) {
            _isUnlocked.value = true
            return true
        }
        return false
    }

    fun lock() {
        _isUnlocked.value = false
    }
}
