package com.securevault

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.ui.SecureVaultNavHost
import com.securevault.ui.theme.SecureVaultTheme
import com.securevault.utils.NotificationHelper
import com.securevault.utils.ReminderScheduler
import com.securevault.utils.ThemeManager
import com.securevault.viewmodel.VaultViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            //  Вызываем обе системы напоминаний
            ReminderScheduler.scheduleReminder(this)
            scheduleNewRotationCheck()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Создание канала уведомлений (для новой системы)
        NotificationHelper.createNotificationChannel(this)
        
        // Запрос разрешения на уведомления (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                ReminderScheduler.scheduleReminder(this)
                scheduleNewRotationCheck()
            }
        } else {
            ReminderScheduler.scheduleReminder(this)
            scheduleNewRotationCheck()
        }
        
        setContent {
            val currentTheme by ThemeManager.getThemeFlow(this).collectAsState(initial = ThemeManager.AppTheme.SYSTEM)
            
            SecureVaultTheme(
                darkTheme = when (currentTheme) {
                    ThemeManager.AppTheme.LIGHT -> false
                    ThemeManager.AppTheme.DARK -> true
                    ThemeManager.AppTheme.SYSTEM -> isSystemInDarkTheme()
                    else -> isSystemInDarkTheme()
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
    
    //  Планирование новой системы проверки ротации
    private fun scheduleNewRotationCheck() {
        val vaultViewModel: VaultViewModel by lazy {
            androidx.lifecycle.ViewModelProvider(this)[VaultViewModel::class.java]
        }
        vaultViewModel.scheduleRotationCheck(this)
    }
}
