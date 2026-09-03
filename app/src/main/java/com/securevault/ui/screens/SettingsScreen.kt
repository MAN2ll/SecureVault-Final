@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.activity.ComponentActivity
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.viewmodel.VaultViewModel
import java.util.concurrent.Executor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToExport: () -> Unit = {}, //  Добавлено
    onNavigateToChangePassword: () -> Unit = {}, //  Добавлено
    onLock: () -> Unit = {}, //  Добавлено
    viewModel: VaultViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val prefs = remember { context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE) }
    
    var biometricEnabled by remember { mutableStateOf(prefs.getBoolean("biometric_enabled", false)) }
    var actualBiometricState by remember { mutableStateOf(biometricEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Вход по биометрии", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Использовать отпечаток или Face ID", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = actualBiometricState,
                        onCheckedChange = { isEnabled ->
                            if (isEnabled && activity != null) {
                                val biometricManager = BiometricManager.from(context)
                                val canAuthenticate = biometricManager.canAuthenticate(
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                )

                                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                                    launchBiometricPrompt(activity) { success ->
                                        if (success) {
                                            actualBiometricState = true
                                            prefs.edit().putBoolean("biometric_enabled", true).apply()
                                        } else {
                                            actualBiometricState = false
                                        }
                                    }
                                } else {
                                    actualBiometricState = false
                                }
                            } else {
                                actualBiometricState = false
                                biometricEnabled = false
                                prefs.edit().putBoolean("biometric_enabled", false).apply()
                            }
                        }
                    )
                }
            }
            
            // Здесь могут быть твои кнопки Экспорта/Импорта, вызывающие onNavigateToExport()
        }
    }
}

private fun launchBiometricPrompt(activity: ComponentActivity, onResult: (Boolean) -> Unit) {
    val executor: Executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(
        activity, //  Теперь передаем Activity, а не Context
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onResult(true)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onResult(false)
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onResult(false)
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Подтвердите личность")
        .setSubtitle("Для включения входа по биометрии подтвердите свои данные")
        .setNegativeButtonText("Отмена")
        .build()

    biometricPrompt.authenticate(promptInfo)
}
