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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.data.Entry
import com.securevault.security.MasterPasswordHasher
import com.securevault.utils.SecureQrManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordViewDialog(
    entry: Entry,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onQr: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showPassword by remember { mutableStateOf(false) }
    var decryptedPassword by remember { mutableStateOf<String?>(null) }
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var masterPasswordInput by remember { mutableStateOf("") }
    var masterPasswordError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(entry.service, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Логин
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Логин", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(entry.username, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(entry.username))
                                Toast.makeText(context, "Логин скопирован", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, "Копировать логин", Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Пароль
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Пароль", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showPassword && decryptedPassword != null) {
                                    decryptedPassword!!
                                } else {
                                    "••••••••••••"
                                },
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            
                            IconButton(onClick = {
                                if (showPassword) {
                                    showPassword = false
                                } else {
                                    showMasterPasswordDialog = true
                                }
                            }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Скрыть пароль" else "Показать пароль",
                                    Modifier.size(18.dp)
                                )
                            }
                            
                            IconButton(onClick = {
                                if (decryptedPassword != null) {
                                    clipboardManager.setText(AnnotatedString(decryptedPassword!!))
                                    Toast.makeText(context, "Пароль скопирован", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Сначала покажите пароль", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.ContentCopy, "Копировать пароль", Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // URL
                if (!entry.url.isNullOrBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("URL", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(entry.url!!, fontSize = 13.sp)
                        }
                    }
                }

                // Заметки
                if (!entry.notes.isNullOrBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Заметки", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(entry.notes!!, fontSize = 13.sp)
                        }
                    }
                }

                // Информация о ротации
                if (entry.rotationEnabled) {
                    val daysLeft = entry.getDaysUntilRotation()
                    val isExpired = entry.isPasswordExpired()
                    val statusColor = when {
                        isExpired -> MaterialTheme.colorScheme.error
                        daysLeft != null && daysLeft <= 7 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val statusText = when {
                        isExpired -> "Просрочено"
                        daysLeft != null -> "Осталось $daysLeft дн."
                        else -> "Ротация включена"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, null, tint = statusColor, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(statusText, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Тип генерации
                if (entry.generationType.isNotBlank()) {
                    val typeName = when (entry.generationType) {
                        "mnemonic" -> "AMPG v2"
                        "shuffled" -> "Перемешанный"
                        "manual" -> "Ручной"
                        else -> "Случайный"
                    }
                    Text(
                        "Тип: $typeName",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            //  Только одна кнопка "Изменить"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Удалить")
                }

                OutlinedButton(
                    onClick = onQr,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.QrCode, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("QR")
                }

                //  Только "Изменить", без "Редактировать"
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Изменить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )

    // Диалог мастер-пароля для показа пароля
    if (showMasterPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showMasterPasswordDialog = false
                masterPasswordInput = ""
                masterPasswordError = null
            },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Подтверждение") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Для просмотра пароля введите мастер-пароль:", fontSize = 13.sp)
                    androidx.compose.foundation.text.KeyboardOptions
                    OutlinedTextField(
                        value = masterPasswordInput,
                        onValueChange = { masterPasswordInput = it; masterPasswordError = null },
                        label = { Text("Мастер-пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                        ),
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
                    val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    val storedHash = prefs.getString("master_hash", null)
                    val storedSalt = prefs.getString("master_salt", null)
                    val iterations = prefs.getInt("master_iterations", 100_000)

                    if (storedHash != null && storedSalt != null &&
                        MasterPasswordHasher.verify(masterPasswordInput, storedHash, storedSalt, iterations)) {
                        try {
                            decryptedPassword = entry.password
                            showPassword = true
                            showMasterPasswordDialog = false
                            masterPasswordInput = ""
                            masterPasswordError = null
                        } catch (e: Exception) {
                            masterPasswordError = "Не удалось расшифровать пароль"
                        }
                    } else {
                        masterPasswordError = "Неверный мастер-пароль"
                    }
                }) {
                    Text("Подтвердить")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMasterPasswordDialog = false
                    masterPasswordInput = ""
                    masterPasswordError = null
                }) {
                    Text("Отмена")
                }
            }
        )
    }
}
