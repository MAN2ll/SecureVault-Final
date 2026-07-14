package com.securevault

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity // Было ComponentActivity
import com.securevault.ui.theme.SecureVaultTheme // Адаптируй под свой пакет темы
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() { // 
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecureVaultTheme {
                // Твой корневой Composable, например SecureVaultNavHost()
                SecureVaultApp() 
            }
        }
    }
}
