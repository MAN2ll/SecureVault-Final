@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    
    // Фильтруем записи, у которых включена ротация
    val rotatableEntries = entries.filter { it.rotationEnabled && it.nextRotationDate != null }
    val personalEntries = rotatableEntries.filter { it.profile == Profile.PERSONAL }
    val workEntries = rotatableEntries.filter { it.profile == Profile.WORK }

    // Диалог подтверждения
    var showConfirmDialog by remember { mutableStateOf<RotationTarget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ротация паролей", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // === КНОПКИ МАССОВОЙ РОТАЦИИ ===
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Массовое обновление", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    
                    Button(
                        onClick = { showConfirmDialog = RotationTarget.ALL_PERSONAL },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = personalEntries.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Обновить все личные (${personalEntries.size})")
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Button(
                        onClick = { showConfirmDialog = RotationTarget.ALL_WORK },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = workEntries.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Work, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Обновить все рабочие (${workEntries.size})")
                    }
                }
            }

            // === СПИСОК ЗАПИСЕЙ ===
            Text("Отдельные записи", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            
            if (rotatableEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("Нет записей с активной ротацией", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rotatableEntries, key = { it.id }) { entry ->
                        RotationEntryCard(
                            entry = entry,
                            onUpdate = { viewModel.rotatePassword(entry.id) }
                        )
                    }
                }
            }
        }
    }

    // === ДИАЛОГ ПОДТВЕРЖДЕНИЯ ===
    showConfirmDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = { Text("Подтверждение") },
            text = {
                Text(when (target) {
                    RotationTarget.ALL_PERSONAL -> "Обновить все личные пароли? (Генерация новых случайных паролей)"
                    RotationTarget.ALL_WORK -> "Обновить все рабочие пароли? (Генерация новых случайных паролей)"
                    else -> ""
                })
            },
            confirmButton = {
                Button(onClick = {
                    when (target) {
                        RotationTarget.ALL_PERSONAL -> viewModel.bulkRotatePasswords(personalEntries.map { it.id })
                        RotationTarget.ALL_WORK -> viewModel.bulkRotatePasswords(workEntries.map { it.id })
                        else -> {}
                    }
                    showConfirmDialog = null
                }) {
                    Text("Обновить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = null }) { Text("Отмена") }
            }
        )
    }
}

enum class RotationTarget { ALL_PERSONAL, ALL_WORK }

@Composable
private fun RotationEntryCard(entry: Entry, onUpdate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.service, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    text = "След. смена: ${entry.nextRotationDate?.let { formatDate(it) } ?: "—"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onUpdate, colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )) {
                Text("Обновить")
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))
}
