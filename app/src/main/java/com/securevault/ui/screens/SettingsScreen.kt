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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var pendingBiometricAction by remember { mutableStateOf<Boolean?>(null) } // true = включить, false = выключить
    var masterPassword by remember { mutableStateOf("") }
    var masterPasswordError by remember { mutableStateOf<String?>(null) }

    val isBiometricEnabled by remember { mutableStateOf(viewModel.isBiometricLoginEnabled()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                            pendingBiometricAction = newValue
                            showMasterPasswordDialog = true
                        }
                    )
                }
            }
        }
    }

    // Диалог мастер-пароля перед изменением настройки биометрии
    if (showMasterPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showMasterPasswordDialog = false },
            title = { Text("Подтверждение") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Введите мастер-пароль для изменения настрое безопасности", fontSize = 13.sp)
                    OutlinedTextField(
                        value = masterPassword,
                        onValueChange = { masterPassword = it; masterPasswordError = null },
                        label = { Text("Мастер-пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = masterPasswordError != null
                    )
                    if (masterPasswordError != null) {
                        Text(masterPasswordError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (viewModel.attemptUnlock(masterPassword)) {
                        showMasterPasswordDialog = false
                        masterPassword = ""
                        // Запускаем биометрическую проверку
                        if (activity != null) {
                            triggerBiometricSetup(activity, context, pendingBiometricAction == true, viewModel)
                        }
                    } else {
                        masterPasswordError = "Неверный мастер-пароль"
                    }
                }) { Text("Подтвердить") }
            },
            dismissButton = {
                TextButton(onClick = { showMasterPasswordDialog = false; masterPassword = "" }) { Text("Отмена") }
            }
        )
    }
}

//  Логика включения/выключения биометрии
private fun triggerBiometricSetup(
    activity: FragmentActivity,
    context: Context,
    enable: Boolean,
    viewModel: AuthViewModel
) {
    if (!enable) {
        // Выключение не требует биометрии, только мастер-пароль (который уже введен)
        viewModel.setBiometricLoginEnabled(false)
        return
    }

    val biometricManager = BiometricManager.from(context)
    val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)

    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
        // Показать Toast или Snackbar: "Биометрия недоступна на этом устройстве"
        android.widget.Toast.makeText(context, "Биометрия недоступна на этом устройстве", android.widget.Toast.LENGTH_LONG).show()
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            viewModel.setBiometricLoginEnabled(true)
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            // Отмена пользователем
        }
        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            android.widget.Toast.makeText(context, "Ошибка биометрии", android.widget.Toast.LENGTH_SHORT).show()
        }
    })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Подтвердите включение биометрии")
        .setSubtitle("Используйте отпечаток пальца или лицо")
        .setNegativeButtonText("Отмена")
        .build()

    biometricPrompt.authenticate(promptInfo)
}
