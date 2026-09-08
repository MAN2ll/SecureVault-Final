package com.securevault.ui.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.securevault.data.Profile
import com.securevault.security.ProfilePasswordHasher
import java.util.concurrent.Executor

@Composable
fun ProfileAccessDialog(
    profile: Profile,
    requireBiometric: Boolean,
    onDismiss: () -> Unit,
    onGranted: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    fun verifyPin() {
        if (pinInput.isBlank()) {
            errorMessage = "Введите PIN"
            return
        }
        isVerifying = true
        val isValid = ProfilePasswordHasher.verify(pinInput, profile.passwordHash ?: "", profile.passwordSalt ?: "")
        isVerifying = false
        
        if (isValid) {
            onGranted()
        } else {
            errorMessage = "Неверный PIN"
            pinInput = ""
        }
    }

    fun launchBiometric() {
        if (activity == null) {
            verifyPin() // Fallback
            return
        }
        
        val executor: Executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onGranted()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // При ошибке или отмене биометрии предлагаем ввести PIN
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    errorMessage = "Биометрия не распознана. Введите PIN."
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Доступ к паролю")
            .setSubtitle("Подтвердите личность для просмотра")
            .setNegativeButtonText("Использовать PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        if (requireBiometric) {
            val biometricManager = BiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                launchBiometric()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Требуется подтверждение") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Введите PIN профиля для доступа к паролю:")
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { 
                        pinInput = it.filter { char -> char.isDigit() }.take(8)
                        errorMessage = null 
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null
                )
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = { verifyPin() }, enabled = pinInput.isNotEmpty() && !isVerifying) {
                if (isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Подтвердить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
