package com.securevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.securevault.ui.SecureVaultNavHost
import com.securevault.ui.theme.SecureVaultTheme
import com.securevault.utils.AutoLockManager
import com.securevault.utils.ThemeManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✅ Инициализация AutoLockManager
        AutoLockManager.init(applicationContext)
        
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
