@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.ui.components.ProfileAccessDialog
import com.securevault.utils.AccessResult
import com.securevault.utils.PasswordAccessPolicy
import com.securevault.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordViewDialog(
    entry: Entry,
    profile: Profile,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onQr: () -> Unit,
    onDelete: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    var showPassword by remember { mutableStateOf(false) }
    var decryptedPassword by remember { mutableStateOf<String?>(null) }
    
    var showProfileAccessDialog by remember { mutableStateOf(false) }
    var showPinNotSetDialog by remember { mutableStateOf(false) }

    fun requestAccess() {
        when (val result = PasswordAccessPolicy.resolve(entry, profile)) {
            is AccessResult.Granted -> {
                showPassword = true
                decryptedPassword = entry.password
            }
            is AccessResult.BiometricOrPin -> {
                showProfileAccessDialog = true
            }
            is AccessResult.PinNotSet -> {
                showPinNotSetDialog = true
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

    if (showProfileAccessDialog) {
        ProfileAccessDialog(
            profile = profile,
            title = "Подтверждение доступа",
            subtitle = "Введите PIN профиля или используйте отпечаток",
            isBiometricEnabled = authViewModel.isBiometricLoginEnabled(),
            onConfirmed = {
                showPassword = true
                decryptedPassword = entry.password
                showProfileAccessDialog = false
            },
            onDismiss = { showProfileAccessDialog = false }
        )
    }

    if (showPinNotSetDialog) {
        AlertDialog(
            onDismissRequest = { showPinNotSetDialog = false },
            title = { Text("PIN профиля не задан") },
            text = { Text("Для этого действия нужно сначала задать PIN профиля в настройках.") },
            confirmButton = {
                TextButton(onClick = { showPinNotSetDialog = false }) { Text("Понятно") }
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
