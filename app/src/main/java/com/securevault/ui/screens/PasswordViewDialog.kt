@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.security.ProfilePasswordHasher
import com.securevault.utils.AccessMode
import com.securevault.utils.PasswordAccessPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordViewDialog(
    entry: Entry,
    profile: Profile,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onQr: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var showPassword by remember { mutableStateOf(false) }
    var decryptedPassword by remember { mutableStateOf<String?>(null) }
    
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    val accessMode = remember(entry, profile) {
        PasswordAccessPolicy.resolve(entry, profile)
    }

    fun requestAccess() {
        when (accessMode) {
            AccessMode.NO_CONFIRMATION -> {
                showPassword = true
                decryptedPassword = entry.password
            }
            AccessMode.PIN_REQUIRED, AccessMode.PIN_ALWAYS -> {
                showPinDialog = true
            }
            AccessMode.BIOMETRIC_OR_PIN -> {
                val biometricManager = BiometricManager.from(context)
                val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                
                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS && activity != null) {
                    showBiometricPrompt(activity, context) { success ->
                        if (success) {
                            showPassword = true
                            decryptedPassword = entry.password
                        } else {
                            showPinDialog = true
                        }
                    }
                } else {
                    showPinDialog = true
                }
            }
            else -> {
                showPinDialog = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(entry.service, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("Логин", entry.username)
                if (!entry.url.isNullOrBlank()) InfoRow("URL", entry.url)
                if (!entry.notes.isNullOrBlank()) InfoRow("Заметки", entry.notes)
                if (!entry.textHint.isNullOrBlank()) InfoRow("Подсказка", entry.textHint)
                if (!entry.mnemonicPhraseHint.isNullOrBlank()) InfoRow("Мнемоника", entry.mnemonicPhraseHint)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Пароль", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            //  visualTransformation удалён, так как это недопустимый параметр для Text
                            Text(
                                text = if (showPassword && decryptedPassword != null) decryptedPassword!! else "••••••••••••",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (!showPassword) {
                                IconButton(onClick = { requestAccess() }) {
                                    Icon(Icons.Default.Visibility, "Показать пароль", tint = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                IconButton(onClick = {
                                    context.getSystemService(android.content.ClipboardManager::class.java)
                                        .setPrimaryClip(android.content.ClipData.newPlainText("password", decryptedPassword))
                                    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, "Копировать пароль", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onQr) {
                        Icon(Icons.Default.QrCode, contentDescription = "Показать QR-код")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Изменить запись")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить запись", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Введите PIN профиля") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it; pinError = null },
                        label = { Text("PIN") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = pinError != null
                    )
                    if (pinError != null) Text(pinError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (ProfilePasswordHasher.verify(pinInput, profile.passwordHash, profile.passwordSalt)) {
                        showPassword = true
                        decryptedPassword = entry.password
                        showPinDialog = false
                        pinInput = ""
                    } else {
                        pinError = "Неверный PIN профиля"
                    }
                }) { Text("Подтвердить") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false; pinInput = "" }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun showBiometricPrompt(activity: FragmentActivity, context: Context, onResult: (Boolean) -> Unit) {
    val executor = ContextCompat.getMainExecutor(context)
    val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
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
    })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Подтвердите личность")
        .setSubtitle("Для просмотра пароля")
        .setNegativeButtonText("Использовать PIN")
        .build()

    biometricPrompt.authenticate(promptInfo)
}
