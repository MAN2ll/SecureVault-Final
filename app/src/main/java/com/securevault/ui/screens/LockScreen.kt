@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val authState by viewModel.authState.collectAsState()

    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showBiometricPrompt by remember { mutableStateOf(false) }

    //  Автоматический запуск биометрии при входе, если включена и не нужна недельная проверка
    LaunchedEffect(authState) {
        if (authState is AuthViewModel.AuthState.Locked) {
            if (viewModel.isBiometricLoginEnabled() && !viewModel.isMasterPasswordRequired()) {
                showBiometricPrompt = true
            }
        }
    }

    // Обработчик биометрии
    if (showBiometricPrompt && activity != null) {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                showBiometricPrompt = false
                onUnlock() // Биометрия успешна, разблокируем
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                showBiometricPrompt = false // При ошибке/отмене просто показываем ввод пароля
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                showBiometricPrompt = false
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Разблокировать SecureVault")
            .setSubtitle("Используйте отпечаток пальца или лицо")
            .setNegativeButtonText("Использовать мастер-пароль")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        
        Text("SecureVault", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        
        // Текст для недельной проверки
        if (viewModel.isMasterPasswordRequired()) {
            Text(
                "Для безопасности введите мастер-пароль",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(16.dp))
        } else {
            Text("Введите мастер-пароль", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Мастер-пароль") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = error != null
        )
        
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (viewModel.attemptUnlock(password)) {
                    onUnlock()
                } else {
                    error = "Неверный мастер-пароль"
                    password = ""
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Разблокировать", fontSize = 16.sp)
        }
        
        //  Кнопка ручного вызова биометрии, если она включена
        if (viewModel.isBiometricLoginEnabled() && !viewModel.isMasterPasswordRequired()) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { showBiometricPrompt = true }) {
                Icon(Icons.Default.Fingerprint, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Войти по отпечатку")
            }
        }
    }
}
