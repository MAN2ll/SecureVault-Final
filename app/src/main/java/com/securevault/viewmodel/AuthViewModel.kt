package com.securevault.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.securevault.security.MasterPasswordHasher

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private var _isAuthenticated = false
    private var _isLocked = true

    val isAuthenticated: Boolean get() = _isAuthenticated
    val isLocked: Boolean get() = _isLocked

    fun authenticate(password: String): Boolean {
        val storedHash = prefs.getString("master_hash", null)
        val storedSalt = prefs.getString("master_salt", null)
        val iterations = prefs.getInt("master_iterations", 100_000)

        return if (storedHash != null && storedSalt != null &&
            MasterPasswordHasher.verify(password, storedHash, storedSalt, iterations)) {
            _isAuthenticated = true
            _isLocked = false
            prefs.edit().putLong("last_master_password_confirmed_at", System.currentTimeMillis()).apply()
            true
        } else {
            false
        }
    }

    fun lock() {
        _isLocked = true
        _isAuthenticated = false
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
