package com.securevault

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.securevault.ui.SecureVaultNavHost
import com.securevault.ui.theme.SecureVaultTheme
import com.securevault.utils.AutoLockManager
import com.securevault.utils.NotificationHelper
import com.securevault.utils.ReminderScheduler
import com.securevault.utils.ThemeManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            ReminderScheduler.scheduleReminder(this)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✅ Инициализация AutoLockManager
        AutoLockManager.init(applicationContext)
        
        // ✅ Создание канала уведомлений
        NotificationHelper.createNotificationChannel(this)
        
        // ✅ Запрос разрешения на уведомления (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                ReminderScheduler.scheduleReminder(this)
            }
        } else {
            ReminderScheduler.scheduleReminder(this)
        }
        
        setContent {
            // ✅ Получаем текущую тему
            val currentTheme = ThemeManager.getTheme(this)
            
            SecureVaultTheme(
                darkTheme = when (currentTheme) {
                    ThemeManager.AppTheme.LIGHT -> false
                    ThemeManager.AppTheme.DARK -> true
                    ThemeManager.AppTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SecureVaultNavHost()
                }
            }
        }
    }
}
