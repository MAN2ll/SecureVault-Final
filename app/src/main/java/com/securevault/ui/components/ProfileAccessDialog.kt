package com.securevault.ui.components

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.securevault.data.Profile
import com.securevault.security.ProfilePasswordHasher
import java.util.concurrent.Executor

@Composable
fun ProfileAccessDialog(
    profile: Profile,
    allowBiometric: Boolean,
    onDismiss: () -> Unit,
    onGranted: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    fun verifyPin() {
        if (pinInput.isBlank()) {
            errorMessage = "Введите PIN"
            return
        }
        isVerifying = true
        //  СТРОГАЯ ПРОВЕРКА PIN
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
            verifyPin()
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
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    errorMessage = "Биометрия не распознана. Введите PIN."
                }
            }
        )

        //  ТОЛЬКО BIOMETRIC_STRONG, БЕЗ DEVICE_CREDENTIAL
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Подтверждение доступа")
            .setSubtitle("Используйте отпечаток пальца")
            .setNegativeButtonText("Использовать PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        if (allowBiometric && activity != null) {
            val biometricManager = BiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
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
                Text("Введите PIN профиля для доступа:")
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { 
                        pinInput = it.filter { char -> char.isDigit() }.take(8)
                        errorMessage = null 
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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
