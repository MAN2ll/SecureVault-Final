@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    
    //  Используем var для реактивного обновления
    var isBiometricEnabled by remember { mutableStateOf(viewModel.isBiometricLoginEnabled()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            //  Переключатель без запроса мастер-пароля
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Вход по отпечатку пальца", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(
                            "Используйте биометрию для быстрого входа",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                // Включаем: проверяем биометрию и показываем системный промпт
                                triggerBiometricSetup(activity, context, true, viewModel) { success ->
                                    if (success) {
                                        viewModel.setBiometricLoginEnabled(true)
                                        isBiometricEnabled = true
                                    }
                                }
                            } else {
                                // Выключаем: просто меняем флаг, без мастер-пароля
                                viewModel.setBiometricLoginEnabled(false)
                                isBiometricEnabled = false
                            }
                        }
                    )
                }
            }

            HorizontalDivider()

            Text("Безопасность и данные", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            SettingsActionCard(
                icon = Icons.Default.Lock,
                title = "Сменить мастер-пароль",
                subtitle = "Обновите пароль для входа в приложение",
                onClick = onNavigateToChangePassword
            )
            
            SettingsActionCard(
                icon = Icons.Default.Upload,
                title = "Экспорт / Импорт",
                subtitle = "Резервное копирование и перенос данных",
                onClick = onNavigateToExport
            )
        }
    }
}

@Composable
private fun SettingsActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

//  Логика без мастер-пароля, только системная биометрия
private fun triggerBiometricSetup(
    activity: FragmentActivity?,
    context: Context,
    enable: Boolean,
    viewModel: AuthViewModel,
    onChanged: (Boolean) -> Unit
) {
    if (!enable) {
        onChanged(false)
        return
    }

    if (activity == null) {
        onChanged(false)
        return
    }

    val biometricManager = BiometricManager.from(context)
    val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)

    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
        android.widget.Toast.makeText(context, "Биометрия недоступна на этом устройстве", android.widget.Toast.LENGTH_LONG).show()
        onChanged(false)
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onChanged(true)
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            onChanged(false)
        }
        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            android.widget.Toast.makeText(context, "Ошибка биометрии", android.widget.Toast.LENGTH_SHORT).show()
            onChanged(false)
        }
    })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Подтвердите включение биометрии")
        .setSubtitle("Используйте отпечаток пальца или лицо")
        .setNegativeButtonText("Отмена")
        .build()

    biometricPrompt.authenticate(promptInfo)
}
