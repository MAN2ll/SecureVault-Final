package com.securevault

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
    //  Получаем ViewModel для отслеживания состояния
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        //  Блокировка приложения при уходе в фон (сворачивание или закрытие)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                // Вызываем блокировку. Если пользователь сворачивает приложение, 
                // при следующем открытии потребуется вход.
                authViewModel.lock()
            }
        })

        setContent {
            SecureVaultTheme {
                SecureVaultNavHost()
            }
        }
    }
}
