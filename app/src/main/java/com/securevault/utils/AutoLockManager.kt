package com.securevault.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AutoLockManager {
    private const val PREFS_NAME = "auto_lock_prefs"
    private const val KEY_ENABLED = "auto_lock_enabled"
    private const val KEY_TIMEOUT_MINUTES = "auto_lock_timeout_minutes"
    
    private lateinit var prefs: SharedPreferences
    
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()
    
    private var lastActivityTime: Long = System.currentTimeMillis()
    private var timeoutMillis: Long = 5 * 60 * 1000L // По умолчанию 5 минут
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        timeoutMillis = getTimeoutMinutes() * 60 * 1000L
    }
    
    fun isEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, true)
    }
    
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
    
    fun getTimeoutMinutes(): Int {
        return prefs.getInt(KEY_TIMEOUT_MINUTES, 5)
    }
    
    fun setTimeoutMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_TIMEOUT_MINUTES, minutes).apply()
        timeoutMillis = minutes * 60 * 1000L
    }
    
    fun recordUserActivity() {
        lastActivityTime = System.currentTimeMillis()
        if (_isLocked.value) {
            _isLocked.value = false
        }
    }
    
    fun checkTimeout(): Boolean {
        if (!isEnabled()) return false
        
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastActivityTime
        
        if (elapsed >= timeoutMillis) {
            _isLocked.value = true
            return true
        }
        return false
    }
    
    fun lock() {
        _isLocked.value = true
    }
    
    fun unlock() {
        _isLocked.value = false
        recordUserActivity()
    }
    
    fun getTimeRemainingMillis(): Long {
        val elapsed = System.currentTimeMillis() - lastActivityTime
        return (timeoutMillis - elapsed).coerceAtLeast(0)
    }
}
