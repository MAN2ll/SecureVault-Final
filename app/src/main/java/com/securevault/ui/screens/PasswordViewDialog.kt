@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.securevault.data.Entry
import com.securevault.data.PasswordHistoryItem
import com.securevault.security.MasterPasswordHasher
import com.securevault.utils.CryptoUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordViewDialog(
    entry: Entry,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    //  РАЗДЕЛЬНЫЕ СОСТОЯНИЯ
    var isCurrentPasswordRevealed by remember { mutableStateOf(false) }
    var isHistoryVisible by remember { mutableStateOf(false) }
    var areOldPasswordsRevealed by remember { mutableStateOf(false) }
    
    var showCurrentPasswordConfirm by remember { mutableStateOf(false) }
    var showHistoryConfirm by remember { mutableStateOf(false) }
    var showOldPasswordsConfirm by remember { mutableStateOf(false) }

    val history = entry.getPasswordHistory()
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = {
            //  При закрытии всё скрывается
            isCurrentPasswordRevealed = false
            isHistoryVisible = false
            areOldPasswordsRevealed = false
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
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Информация
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
                                when (entry.generationType) {
                                    "mnemonic" -> "Мнемонический (AMPG)"
                                    "shuffled" -> "Перемешанный"
                                    "swapped" -> "Обменянный"
                                    else -> "Случайный"
                                },
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

                //  КНОПКА QR-КОДА
                var showQrDialog by remember { mutableStateOf(false) }
                
                OutlinedButton(
                    onClick = { showQrDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCode, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Показать QR-код")
                }
                
                if (showQrDialog) {
                    QrCodeDialog(
                        entry = entry,
                        onDismiss = { showQrDialog = false }
                    )
                }

                //  ТЕКУЩИЙ ПАРОЛЬ (отдельное подтверждение)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentPasswordRevealed) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (isCurrentPasswordRevealed) {
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
                            Text(entry.password, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
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
                                Button(onClick = { showCurrentPasswordConfirm = true }, modifier = Modifier.padding(start = 8.dp)) {
                                    Text("Показать", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                //  ИСТОРИЯ (отдельное подтверждение, БЕЗ старых паролей)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (isHistoryVisible) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("История изменений:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                TextButton(onClick = { 
                                    isHistoryVisible = false
                                    areOldPasswordsRevealed = false
                                }) {
                                    Text("Скрыть", fontSize = 11.sp)
                                }
                            }
                            
                            //  Кнопка "Показать старые пароли" (отдельное подтверждение)
                            if (history.isNotEmpty() && !areOldPasswordsRevealed) {
                                TextButton(onClick = { showOldPasswordsConfirm = true }) {
                                    Text("Показать старые пароли", fontSize = 11.sp)
                                }
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            if (history.isEmpty()) {
                                Text("История ротации отсутствует", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            } else {
                                history.take(10).forEach { item ->
                                    HistoryItemCard(
                                        item = item,
                                        dateFormat = dateFormat,
                                        showOldPassword = areOldPasswordsRevealed,
                                        clipboardManager = clipboardManager,
                                        context = context
                                    )
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
                                Button(onClick = { showHistoryConfirm = true }, modifier = Modifier.padding(start = 8.dp)) {
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
                isCurrentPasswordRevealed = false
                isHistoryVisible = false
                areOldPasswordsRevealed = false
                onDismiss()
            }) {
                Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Закрыть")
            }
        },
        modifier = Modifier.fillMaxWidth(0.95f)
    )

    //  ПОДТВЕРЖДЕНИЕ ДЛЯ ТЕКУЩЕГО ПАРОЛЯ
    if (showCurrentPasswordConfirm) {
        ConfirmMasterPasswordDialog(
            context = context,
            title = "Просмотр текущего пароля",
            onConfirmed = {
                showCurrentPasswordConfirm = false
                isCurrentPasswordRevealed = true
            },
            onDismiss = { showCurrentPasswordConfirm = false }
        )
    }

    //  ПОДТВЕРЖДЕНИЕ ДЛЯ ИСТОРИИ (без старых паролей)
    if (showHistoryConfirm) {
        ConfirmMasterPasswordDialog(
            context = context,
            title = "Просмотр истории",
            onConfirmed = {
                showHistoryConfirm = false
                isHistoryVisible = true
                //  Старые пароли НЕ раскрываются
            },
            onDismiss = { showHistoryConfirm = false }
        )
    }

    //  ПОДТВЕРЖДЕНИЕ ДЛЯ СТАРЫХ ПАРОЛЕЙ (отдельное)
    if (showOldPasswordsConfirm) {
        ConfirmMasterPasswordDialog(
            context = context,
            title = "Просмотр старых паролей",
            onConfirmed = {
                showOldPasswordsConfirm = false
                areOldPasswordsRevealed = true
            },
            onDismiss = { showOldPasswordsConfirm = false }
        )
    }
}

@Composable
private fun HistoryItemCard(
    item: PasswordHistoryItem,
    dateFormat: SimpleDateFormat,
    showOldPassword: Boolean,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: Context
) {
    val decryptedPassword = remember(item.encryptedOldPassword, showOldPassword) {
        if (showOldPassword && item.encryptedOldPassword != null) {
            try {
                CryptoUtils.decrypt(item.encryptedOldPassword)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(dateFormat.format(Date(item.date)), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            
            val typeLabel = when (item.type) {
                "mnemonic" -> "Мнемоническая ротация"
                "random" -> "Случайная ротация"
                "manual" -> "Ручная замена"
                "shuffled" -> "Перемешивание"
                "swapped" -> "Обмен"
                "legacy" -> "Старая запись"
                else -> "Изменение"
            }
            Text(typeLabel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            if (item.relatedService != null) {
                Text("Связано с: ${item.relatedService}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
            
            if (decryptedPassword != null) {
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Старый: $decryptedPassword",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(decryptedPassword))
                        android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                    }
                }
            } else {
                Text("Старый пароль скрыт", fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmMasterPasswordDialog(
    context: Context,
    title: String,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Введите мастер-пароль для подтверждения:", fontSize = 13.sp)
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
                val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val storedHash = prefs.getString("master_hash", null)
                val storedSalt = prefs.getString("master_salt", null)
                val iterations = prefs.getInt("master_iterations", 100_000)
                
                if (storedHash != null && storedSalt != null && 
                    MasterPasswordHasher.verify(password, storedHash, storedSalt, iterations)) {
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
