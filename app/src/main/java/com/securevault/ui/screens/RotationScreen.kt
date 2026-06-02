@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.viewmodel.VaultViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val allEntries by viewModel.entries.collectAsState()
    val rotatableEntries = remember(allEntries) {
        allEntries.filter { it.rotationEnabled && it.nextRotationDate != null }
            .sortedBy { it.nextRotationDate }
    }

    var showReplaceAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ротация паролей", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (rotatableEntries.isNotEmpty()) {
                Button(
                    onClick = { showReplaceAllDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Заменить ВСЕ пароли с ротацией")
                }
            }

            if (rotatableEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет паролей с активной ротацией")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rotatableEntries, key = { it.id }) { entry ->
                        RotationItemCard(entry = entry, onUpdate = { viewModel.rotatePassword(entry.id) })
                    }
                }
            }
        }
    }

    if (showReplaceAllDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceAllDialog = false },
            title = { Text("Подтверждение") },
            text = { Text("Вы уверены? Это сгенерирует новые сложные пароли для всех ${rotatableEntries.size} записей и сбросит таймер ротации.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.bulkRotatePasswords(rotatableEntries.map { it.id })
                    showReplaceAllDialog = false
                }) { Text("Да, заменить все") }
            },
            dismissButton = { TextButton({ showReplaceAllDialog = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun RotationItemCard(entry: Entry, onUpdate: () -> Unit) {
    val daysLeft = entry.getDaysUntilRotation() ?: 0
    val isUrgent = daysLeft <= 7
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val dateStr = entry.nextRotationDate?.let { dateFormat.format(it) } ?: "Н/Д"

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.7f)) {
                Text(entry.service, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "След. замена: $dateStr",
                    fontSize = 13.sp,
                    color = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Осталось: $daysLeft дн.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            TextButton(onClick = onUpdate) { Text("Заменить") }
        }
    }
}
