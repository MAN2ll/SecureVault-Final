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
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.VaultViewModel

enum class RotationFilter(val label: String) {
    EXPIRED("Просроченные"),
    SOON("Скоро истекают"),
    ALL("Все с ротацией")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val allRotationEntries by viewModel.rotationEntries.collectAsState()
    var selectedEntry by remember { mutableStateOf<Entry?>(null) }
    var showBulkRotation by remember { mutableStateOf(false) }
    var showShuffleDialog by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(RotationFilter.EXPIRED) }
    var daysThreshold by remember { mutableIntStateOf(7) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filteredEntries = remember(allRotationEntries, currentFilter, daysThreshold) {
        val now = System.currentTimeMillis()
        when (currentFilter) {
            RotationFilter.EXPIRED -> {
                allRotationEntries.filter { it.nextRotationDate != null && it.nextRotationDate <= now }
            }
            RotationFilter.SOON -> {
                val threshold = now + (daysThreshold * 24L * 60 * 60 * 1000)
                allRotationEntries.filter { 
                    it.nextRotationDate != null && 
                    it.nextRotationDate > now && 
                    it.nextRotationDate <= threshold 
                }
            }
            RotationFilter.ALL -> {
                allRotationEntries
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ротация паролей", fontWeight = FontWeight.Bold) },
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
            TabRow(selectedTabIndex = currentFilter.ordinal) {
                RotationFilter.entries.forEach { filter ->
                    Tab(
                        selected = currentFilter == filter,
                        onClick = { currentFilter = filter },
                        text = { Text(text = filter.label, fontSize = 11.sp) }
                    )
                }
            }

            if (currentFilter == RotationFilter.SOON) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(7, 14, 30).forEach { days ->
                        FilterChip(
                            selected = daysThreshold == days,
                            onClick = { daysThreshold = days },
                            label = { Text(text = "$days дн.", fontSize = 11.sp) }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Найдено: ${filteredEntries.size} записей", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            if (filteredEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text(text = "Нет записей по фильтру", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        RotationEntryCard(
                            entry = entry,
                            onReplace = { selectedEntry = entry }
                        )
                    }
                    
                    item {
                        Spacer(Modifier.height(16.dp))
                        
                        Button(
                            onClick = { showBulkRotation = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = filteredEntries.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Group, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Массовая ротация (${filteredEntries.size})")
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        OutlinedButton(
                            onClick = { showShuffleDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = filteredEntries.size >= 2
                        ) {
                            Icon(Icons.Default.Shuffle, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (filteredEntries.size >= 2) 
                                    "Перемешать между сервисами (${filteredEntries.size})"
                                else 
                                    "Перемешать (выберите 2+ записи)"
                            )
                        }
                        
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        PasswordRotationDialog(
            serviceName = entry.service,
            currentHint = entry.mnemonicPhraseHint ?: Entry.extractShortPhrase(entry.textHint),
            generationType = entry.generationType,
            rotationMonth = null,
            rotationYear = null,
            onDismiss = { selectedEntry = null },
            onPasswordReplaced = { newPassword: String, newHint: String?, newGenerationType: String, mnemonicPhrase: String?, mnemonicOptions: String? ->
                viewModel.replacePassword(
                    entryId = entry.id,
                    newPassword = newPassword,
                    newHint = newHint,
                    newGenerationType = newGenerationType,
                    newMnemonicPhraseHint = mnemonicPhrase,
                    newMnemonicOptionsJson = mnemonicOptions,
                    onResult = { result ->
                        when (result) {
                            is PasswordOperationResult.Success -> {
                                selectedEntry = null
                            }
                            is PasswordOperationResult.Error -> {
                                errorMessage = result.message
                            }
                        }
                    }
                )
            }
        )
    }

    if (showBulkRotation) {
        BulkRotationDialog(
            entries = filteredEntries,
            onDismiss = { showBulkRotation = false },
            onBulkReplace = { replacements ->
                viewModel.bulkReplacePasswords(replacements) { result ->
                    when (result) {
                        is PasswordOperationResult.Success -> {
                            showBulkRotation = false
                        }
                        is PasswordOperationResult.Error -> {
                            errorMessage = result.message
                        }
                    }
                }
            }
        )
    }

    if (showShuffleDialog) {
        PasswordShuffleDialog(
            entries = filteredEntries,
            onDismiss = { showShuffleDialog = false },
            onShuffleApplied = {
                showShuffleDialog = false
            }
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ошибка валидации") },
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
private fun RotationEntryCard(
    entry: Entry,
    onReplace: () -> Unit
) {
    val daysLeft = entry.getDaysUntilRotation()
    val isExpired = entry.isPasswordExpired()
    
    val statusColor = when {
        isExpired -> MaterialTheme.colorScheme.error
        daysLeft != null && daysLeft <= 3 -> MaterialTheme.colorScheme.error
        daysLeft != null && daysLeft <= 7 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val statusText = when {
        isExpired -> "Просрочено"
        daysLeft != null -> "Осталось $daysLeft дн."
        else -> "Неизвестно"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, Modifier.size(20.dp), tint = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(text = entry.service, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(text = entry.username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(12.dp), tint = statusColor)
                    Spacer(Modifier.width(4.dp))
                    Text(text = statusText, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = statusColor)
                }
                if (entry.generationType == "mnemonic") {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(4.dp))
                        Text(text = "Мнемонический", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary)
                    }
                } else if (entry.generationType == "shuffled") {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shuffle, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(4.dp))
                        Text(text = "Перемешанный", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            
            Button(onClick = onReplace, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Заменить")
            }
        }
    }
}
