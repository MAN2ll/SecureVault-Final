@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.viewmodel.AuthViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordViewDialog(
    entry: Entry,
    onDismiss: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    // ✅ СОСТОЯНИЕ: пароль скрыт по умолчанию
    var isPasswordRevealed by remember { mutableStateOf(false) }
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var isHistoryRevealed by remember { mutableStateOf(false) }

    val history = entry.getPasswordHistory()
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    // Основной диалог с информацией
    AlertDialog(
        onDismissRequest = {
            // При закрытии сбрасываем состояние
            isPasswordRevealed = false
            isHistoryRevealed = false
            onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(entry.service, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Информация о записи (всегда видна)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Информация:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Логин: ", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(entry.username, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Тип генерации: ", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (entry.generationType == "mnemonic") "Мнемонический (AMPG)" 
                                else "Случайный",
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Последнее изменение: ", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(dateFormat.format(Date(entry.lastChanged)), fontSize = 12.sp)
                        }
                        
                        if (entry.rotationEnabled && entry.nextRotationDate != null) {
                            Spacer(Modifier.height(4.dp))
                            val daysLeft = entry.getDaysUntilRotation()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Ротация: ", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    when {
                                        daysLeft == null -> "Неизвестно"
                                        daysLeft < 0 -> "Просрочено"
                                        else -> "через $daysLeft дн."
                                    },
                                    fontSize = 12.sp,
                                    color = when {
                                        daysLeft == null || daysLeft < 0 -> MaterialTheme.colorScheme.error
                                        daysLeft <= 7 -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                        
                        if (entry.textHint != null) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lightbulb, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text("Подсказка: ${entry.textHint}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Пароль (скрыт по умолчанию)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPasswordRevealed) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (isPasswordRevealed) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Текущий пароль:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(entry.password))
                                    android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                entry.password,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Текущий пароль:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("••••••••••••", fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                                }
                                Button(
                                    onClick = { showMasterPasswordDialog = true },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(Icons.Default.Visibility, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Показать", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // История ротации (скрыта по умолчанию)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (isHistoryRevealed) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("История изменений:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                TextButton(onClick = { isHistoryRevealed = false }) {
                                    Text("Скрыть", fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            
                            if (history.isEmpty()) {
                                Text(
                                    "История ротации отсутствует",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            } else {
                                history.take(5).forEach { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                dateFormat.format(Date(item.date)),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                when (item.type) {
                                                    "mnemonic" -> "Мнемоническая ротация"
                                                    "random" -> "Случайная ротация"
                                                    "manual" -> "Ручная замена"
                                                    "legacy" -> "Старая запись"
                                                    else -> "Изменение"
                                                },
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "Старый пароль скрыт",
                                                fontSize = 10.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("История ротации", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { showMasterPasswordDialog = true },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text("Показать", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                isPasswordRevealed = false
                isHistoryRevealed = false
                onDismiss()
            }) {
                Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Закрыть")
            }
        },
        modifier = Modifier.fillMaxWidth(0.95f)
    )

    // Диалог подтверждения мастер-пароля
    if (showMasterPasswordDialog) {
        ConfirmMasterPasswordDialog(
            authViewModel = authViewModel,
            onConfirmed = {
                showMasterPasswordDialog = false
                isPasswordRevealed = true
                isHistoryRevealed = true
            },
            onDismiss = { showMasterPasswordDialog = false }
        )
    }
}

// ✅ НОВЫЙ ДИАЛОГ: подтверждение мастер-пароля
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmMasterPasswordDialog(
    authViewModel: AuthViewModel,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Подтверждение") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Для просмотра введите мастер-пароль:", fontSize = 13.sp)
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
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (authViewModel.verifyMasterPassword(password)) {
                    onConfirmed()
                } else {
                    error = "Неверный пароль"
                }
                password = ""
            }) {
                Text("Подтвердить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
