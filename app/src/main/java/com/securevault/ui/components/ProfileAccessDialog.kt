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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.securevault.data.Profile
import com.securevault.security.ProfilePasswordHasher

@Composable
fun ProfileAccessDialog(profile: Profile, allowBiometric: Boolean,
    onDismiss: () -> Unit, onGranted: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val grant by rememberUpdatedState(onGranted)
    var active by remember { mutableStateOf(true) }
    var pin by remember(profile.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val prompt = remember(activity, profile.id) {
        activity?.let {
            BiometricPrompt(it, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (active) { active = false; grant() }
                }
                override fun onAuthenticationFailed() { if (active) error = "Биометрия не распознана. Введите PIN." }
            })
        }
    }
    DisposableEffect(prompt) { onDispose { active = false; pin = ""; prompt?.cancelAuthentication() } }
    fun authenticate() {
        if (allowBiometric && BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            prompt?.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Подтверждение доступа")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Использовать PIN").build())
        }
    }
    LaunchedEffect(profile.id, allowBiometric) { authenticate() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("PIN профиля «${profile.name}»") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8); error = null },
                label = { Text("PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (allowBiometric) TextButton(onClick = { authenticate() }) { Text("Использовать биометрию") }
        }
    }, confirmButton = { Button(onClick = {
        if (ProfilePasswordHasher.verify(pin, profile.passwordHash, profile.passwordSalt)) {
            pin = ""; if (active) { active = false; grant() }
        } else { pin = ""; error = "Неверный PIN" }
    }, enabled = pin.isNotEmpty()) { Text("Подтвердить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
