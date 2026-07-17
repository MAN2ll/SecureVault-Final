@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.components

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.securevault.data.Profile
import com.securevault.security.ProfilePasswordHasher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileAccessDialog(
    profile: Profile,
    title: String = "Подтверждение доступа",
    subtitle: String = "Введите PIN профиля или используйте отпечаток",
    isBiometricEnabled: Boolean,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    
    var showPinInput by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (isBiometricEnabled && activity != null) {
            val biometricManager = BiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            
            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onConfirmed()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        showPinInput = true
                    }
                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        showPinInput = true
                    }
                })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle("Используйте отпечаток пальца или лицо")
                    .setNegativeButtonText("Ввести PIN профиля")
                    .build()

                biometricPrompt.authenticate(promptInfo)
                return@LaunchedEffect
            }
        }
        showPinInput = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (showPinInput) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(subtitle, fontSize = 13.sp)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it; error = null },
                        label = { Text("PIN профиля") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = error != null
                    )
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            } else {
                Text("Ожидание биометрии...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            if (showPinInput) {
                Button(onClick = {
                    if (pin.isBlank()) {
                        error = "Введите PIN профиля"
                        return@Button
                    }
                    if (ProfilePasswordHasher.verify(pin, profile.passwordHash, profile.passwordSalt)) {
                        onConfirmed()
                    } else {
                        error = "Неверный PIN профиля"
                    }
                }) { Text("Подтвердить") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
