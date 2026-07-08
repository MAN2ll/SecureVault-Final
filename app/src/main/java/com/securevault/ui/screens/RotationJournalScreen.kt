@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.data.PasswordHistoryItem
import com.securevault.security.MasterPasswordHasher
import com.securevault.utils.CryptoUtils
import com.securevault.viewmodel.VaultViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class JournalEntry(
    val serviceName: String,
    val historyItem: PasswordHistoryItem,
    val entryId: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationJournalScreen(
    profileId: Int?,
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    LaunchedEffect(profileId) {
        if (profileId != null) {
            viewModel.setCurrentProfile(profileId)
        }
    }

    val entries by viewModel.entries.collectAsState()
    val context = LocalContext.current

    // Собираем все изменения из истории
    val journalEntries = remember(entries) {
        val journal = mutableListOf<JournalEntry>()
        for (entry in entries) {
            val history = entry.getPasswordHistory()
            for (item in history) {
                journal.add(
                    JournalEntry(
                        serviceName = entry.service,
                        historyItem = item,
                        entryId = entry.id
                    )
                )
            }
        }
        // Сортируем по дате (новые сверху)
        journal.sortedByDescending { it.historyItem.date }
    }

    var selectedJournalEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var decryptedPassword by remember { mutableStateOf<String?>(null) }
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Статистика
    val now = System.currentTimeMillis()
    val monthAgo = now - (30L * 24 * 60 * 60 * 1000)
    val changesLastMonth = journalEntries.count { it.historyItem.date >= monthAgo }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал ротации", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Статистика
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            journalEntries.size.toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Всего изменений", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            changesLastMonth.toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("За последний месяц", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            if (journalEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Журнал пуст", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Изменения паролей появятся здесь", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(journalEntries, key = { "${it.entryId}_${it.historyItem.date}" }) { journalEntry ->
                        JournalCard(
                            journalEntry = journalEntry,
                            onViewPassword = {
                                selectedJournalEntry = journalEntry
                                showMasterPasswordDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Диалог мастер-пароля для расшифровки
    if (showMasterPasswordDialog && selectedJournalEntry != null) {
        AlertDialog(
            onDismissRequest = {
                showMasterPasswordDialog = false
                selectedJournalEntry = null
            },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Показать старый пароль") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Введите мастер-пароль для расшифровки", fontSize = 13.sp)
                    MasterPasswordInput(
                        context = context,
                        onConfirmed = {
                            showMasterPasswordDialog = false
                            try {
                                val encrypted = selectedJournalEntry!!.historyItem.encryptedOldPassword
                                if (encrypted != null) {
                                    decryptedPassword = CryptoUtils.decrypt(encrypted)
                                } else {
                                    decryptedPassword = "Пароль недоступен"
                                }
                            } catch (e: Exception) {
                                errorMessage = "Не удалось расшифровать пароль"
                            }
                        },
                        onError = { error ->
                            showMasterPasswordDialog = false
                            errorMessage = error
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showMasterPasswordDialog = false
                    selectedJournalEntry = null
                }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Показ расшифрованного пароля
    if (decryptedPassword != null && selectedJournalEntry != null) {
        AlertDialog(
            onDismissRequest = {
                decryptedPassword = null
                selectedJournalEntry = null
            },
            icon = { Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Старый пароль: ${selectedJournalEntry!!.serviceName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Дата изменения: ${formatDate(selectedJournalEntry!!.historyItem.date)}", fontSize = 12.sp)
                    Text(
                        decryptedPassword!!,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    decryptedPassword = null
                    selectedJournalEntry = null
                }) {
                    Text("Закрыть")
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
private fun JournalCard(
    journalEntry: JournalEntry,
    onViewPassword: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Folder, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        journalEntry.serviceName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    formatDate(journalEntry.historyItem.date),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tag, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(
                    "Тип: ${formatGenerationType(journalEntry.historyItem.type)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!journalEntry.historyItem.hint.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lightbulb, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Подсказка: ${journalEntry.historyItem.hint}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (!journalEntry.historyItem.relatedService.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Связан с: ${journalEntry.historyItem.relatedService}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = onViewPassword,
                modifier = Modifier.fillMaxWidth(),
                enabled = journalEntry.historyItem.encryptedOldPassword != null
            ) {
                Icon(Icons.Default.Visibility, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Показать старый пароль")
            }
        }
    }
}

@Composable
private fun MasterPasswordInput(
    context: Context,
    onConfirmed: () -> Unit,
    onError: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Мастер-пароль") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = error != null
        )
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Button(
            onClick = {
                val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val storedHash = prefs.getString("master_hash", null)
                val storedSalt = prefs.getString("master_salt", null)
                val iterations = prefs.getInt("master_iterations", 100_000)

                if (storedHash != null && storedSalt != null &&
                    MasterPasswordHasher.verify(password, storedHash, storedSalt, iterations)) {
                    onConfirmed()
                } else {
                    error = "Неверный мастер-пароль"
                }
                password = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Подтвердить")
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatGenerationType(type: String): String = when (type) {
    "mnemonic" -> "Мнемонический (AMPG v2)"
    "shuffled" -> "Перемешанный"
    "manual" -> "Ручной ввод"
    else -> "Случайный"
}
