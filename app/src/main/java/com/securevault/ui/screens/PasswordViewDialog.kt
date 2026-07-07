@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.data.Entry
import com.securevault.security.MasterPasswordHasher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    var passwordRevealed by remember { mutableStateOf(false) }
    var decryptedPassword by remember { mutableStateOf<String?>(null) }
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(entry.service, fontWeight = FontWeight.Bold)
                if (entry.isFavorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Star, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Основная информация
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow(Icons.Default.Person, "Логин", entry.username)
                        if (!entry.url.isNullOrBlank()) {
                            InfoRow(Icons.Default.Link, "URL", entry.url)
                        }
                        InfoRow(Icons.Default.Tag, "Тип генерации", formatGenerationType(entry.generationType))
                        InfoRow(Icons.Default.Schedule, "Последнее изменение", formatDate(entry.lastChanged))
                        if (entry.rotationEnabled) {
                            val daysLeft = entry.getDaysUntilRotation()
                            val status = when {
                                entry.isPasswordExpired() -> "Просрочено"
                                daysLeft != null -> "Осталось $daysLeft дн."
                                else -> "Активно"
                            }
                            InfoRow(Icons.Default.Timer, "Статус ротации", status)
                        }
                    }
                }

                // Подсказка
                if (!entry.textHint.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Подсказка", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(entry.textHint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }

                // Заметки
                if (!entry.notes.isNullOrBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Notes, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Заметки", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(entry.notes, fontSize = 12.sp)
                        }
                    }
                }

                // Пароль
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Пароль", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            IconButton(onClick = {
                                if (passwordRevealed) {
                                    passwordRevealed = false
                                    decryptedPassword = null
                                } else {
                                    showMasterPasswordDialog = true
                                }
                            }) {
                                Icon(
                                    if (passwordRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    if (passwordRevealed) "Скрыть пароль" else "Показать пароль"
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (passwordRevealed && decryptedPassword != null) decryptedPassword!! else "••••••••••••",
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (decryptedPassword != null) {
                                    clipboardManager.setText(AnnotatedString(decryptedPassword!!))
                                    android.widget.Toast.makeText(context, "Пароль скопирован", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = passwordRevealed && decryptedPassword != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Копировать пароль")
                        }
                    }
                }

                // История паролей
                val history = entry.getPasswordHistory()
                if (history.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showHistoryDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.History, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("История паролей (${history.size})")
                    }
                }

                // Действия
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onQr,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCode, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("QR")
                    }
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Изменить")
                    }
                }

                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Удалить запись")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )

    // Диалог мастер-пароля для показа пароля
    if (showMasterPasswordDialog) {
        MasterPasswordConfirmDialog(
            title = "Показать пароль",
            onConfirmed = {
                try {
                    decryptedPassword = entry.password
                    passwordRevealed = true
                    showMasterPasswordDialog = false
                } catch (e: Exception) {
                    errorMessage = "Не удалось расшифровать пароль"
                    showMasterPasswordDialog = false
                }
            },
            onDismiss = { showMasterPasswordDialog = false }
        )
    }

    // Диалог истории паролей
    if (showHistoryDialog) {
        PasswordHistoryDialog(
            entry = entry,
            onDismiss = { showHistoryDialog = false }
        )
    }

    // Подтверждение удаления
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить запись?") },
            text = { Text("Удалить запись \"${entry.service}\"? Это действие необратимо.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Ошибки
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ошибка") },
            text = { Text(errorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("Понятно")
                }
            }
        )
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text("$label: ", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp)
    }
}

private fun formatGenerationType(type: String): String = when (type) {
    "mnemonic" -> "Мнемонический (AMPG v2)"
    "shuffled" -> "Перемешанный"
    "manual" -> "Ручной ввод"
    else -> "Случайный"
}

private fun formatDate(timestamp: Long?): String {
    if (timestamp == null) return "—"
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// Диалог истории паролей
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordHistoryDialog(
    entry: Entry,
    onDismiss: () -> Unit
) {
    val history = entry.getPasswordHistory()
    var revealedIndexes by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showMasterPassword by remember { mutableStateOf(false) }
    var pendingIndex by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("История паролей", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Предыдущие пароли записи. Для просмотра введите мастер-пароль.", fontSize = 12.sp)
                history.forEachIndexed { index, item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Изменён: ${formatDate(item.changedAt)}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text("Тип: ${formatGenerationType(item.generationType)}", fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (index in revealedIndexes) "••••••••" else "••••••••",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = {
                                    pendingIndex = index
                                    showMasterPassword = true
                                }) {
                                    Text("Показать")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )

    if (showMasterPassword) {
        MasterPasswordConfirmDialog(
            title = "Показать старый пароль",
            onConfirmed = {
                pendingIndex?.let { idx ->
                    revealedIndexes = revealedIndexes + idx
                }
                showMasterPassword = false
                pendingIndex = null
            },
            onDismiss = {
                showMasterPassword = false
                pendingIndex = null
            }
        )
    }
}
