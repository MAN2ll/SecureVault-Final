package com.securevault

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.securevault.ui.SecureVaultNavHost
import com.securevault.ui.theme.SecureVaultTheme
import com.securevault.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        //  Отслеживание ухода в фон и возврата для автоблокировки
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                // Сохраняем время ухода в фон
                val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("last_background_at", System.currentTimeMillis()).apply()
            }

            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                
                // Получаем настройку таймаута (по умолчанию 5 минут)
                val timeoutMinutes = prefs.getInt("auto_lock_timeout_minutes", 5)
                val lastBackgroundAt = prefs.getLong("last_background_at", 0L)
                val now = System.currentTimeMillis()

                // Проверяем, было ли приложение в фоне
                if (lastBackgroundAt > 0) {
                    val elapsed = now - lastBackgroundAt
                    val timeoutMs = timeoutMinutes * 60L * 1000L

                    // Если таймаут 0 (сразу) или прошло больше времени, чем разрешено
                    if (timeoutMinutes == 0 || elapsed >= timeoutMs) {
                        authViewModel.lock()
                    }
                }
            }
        })

        setContent {
            SecureVaultTheme {
                SecureVaultNavHost()
            }
        }
    }
}
