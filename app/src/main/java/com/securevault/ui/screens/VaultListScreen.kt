@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.security.MasterPasswordHasher
import com.securevault.ui.components.LockActionButton
import com.securevault.ui.components.PasswordViewDialog //  Импорт диалога
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.VaultViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    profileId: Int?,
    onNavigateToEntry: (String) -> Unit = {},
    onNavigateToNewEntry: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    onNavigateToRotation: () -> Unit = {},
    onNavigateToRotationJournal: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToMnemonicGenerator: () -> Unit = {},
    onNavigateToQrScanner: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    onBack: () -> Unit = {},
    onLock: () -> Unit = {},
    onScanQr: () -> Unit = {},
    onMnemonicGenerator: () -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    
    var entryToDelete by remember { mutableStateOf<Entry?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedEntryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var pendingDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    //  Восстановлено: Диалог просмотра записи
    var entryToView by remember { mutableStateOf<Entry?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    if (profileId == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    LaunchedEffect(profileId) {
        viewModel.setCurrentProfile(profileId)
    }

    val allTags = remember(entries) {
        entries.flatMap { it.tags }.distinctBy { it.lowercase() }.sorted()
    }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    
    val filteredEntries = remember(entries, searchQuery, selectedTag) {
        entries.filter { entry ->
            val matchesSearch = searchQuery.isBlank() || 
                entry.service.contains(searchQuery, ignoreCase = true) ||
                entry.username.contains(searchQuery, ignoreCase = true) ||
                entry.tags.any { it.contains(searchQuery, ignoreCase = true) }
            val matchesTag = selectedTag == null || selectedTag in entry.tags
            matchesSearch && matchesTag
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Пароли", fontWeight = FontWeight.Bold)
                        Text("Записей: ${filteredEntries.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.setCurrentProfile(null)
                        onNavigateToProfiles()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Назад к профилям")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        selectionMode = !selectionMode
                        if (!selectionMode) selectedEntryIds = emptySet()
                    }) {
                        Icon(if (selectionMode) Icons.Default.Close else Icons.Default.CheckBox, if (selectionMode) "Отменить выбор" else "Выбрать записи")
                    }
                    
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Меню")
                    }
                    
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Новая запись") }, onClick = { showMenu = false; onNavigateToNewEntry() }, leadingIcon = { Icon(Icons.Default.Add, null) })
                        DropdownMenuItem(text = { Text("Сканировать QR") }, onClick = { showMenu = false; onNavigateToQrScanner() }, leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) })
                        DropdownMenuItem(text = { Text("Мнемонический генератор") }, onClick = { showMenu = false; onNavigateToMnemonicGenerator() }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) })
                        Divider()
                        DropdownMenuItem(text = { Text("Проверка безопасности (Аудит)") }, onClick = { showMenu = false; onNavigateToAudit() }, leadingIcon = { Icon(Icons.Default.Security, null) })
                        DropdownMenuItem(text = { Text("Ротация паролей") }, onClick = { showMenu = false; onNavigateToRotation() }, leadingIcon = { Icon(Icons.Default.Sync, null) })
                        DropdownMenuItem(text = { Text("Журнал ротации") }, onClick = { showMenu = false; onNavigateToRotationJournal() }, leadingIcon = { Icon(Icons.Default.History, null) })
                        DropdownMenuItem(text = { Text("Экспорт / Импорт") }, onClick = { showMenu = false; onNavigateToExport() }, leadingIcon = { Icon(Icons.Default.SwapVert, null) })
                        DropdownMenuItem(text = { Text("Настройки профиля") }, onClick = { showMenu = false; onNavigateToSettings() }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                        Divider()
                        DropdownMenuItem(text = { Text("Удалить все пароли профиля") }, onClick = { showMenu = false; showDeleteAllDialog = true }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null) })
                    }
                    LockActionButton(onLock = onLock)
                }
            )
        },
        floatingActionButton = {
            // Восстановлена кнопка добавления записи
            FloatingActionButton(onClick = onNavigateToNewEntry) {
                Icon(Icons.Default.Add, "Добавить запись")
            }
        },
        snackbarHost = {
            operationMessage?.let { msg ->
                Snackbar(modifier = Modifier.padding(16.dp), action = { TextButton(onClick = { operationMessage = null }) { Text("OK") } }) { Text(msg) }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск по сервису, логину или тегам...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, "Очистить") } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = favoritesOnly, onCheckedChange = { viewModel.toggleFavoritesOnly() })
                Text("Только избранное", fontSize = 14.sp)
            }
            
            if (allTags.isNotEmpty()) {
                LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = selectedTag == null, onClick = { selectedTag = null }, label = { Text("Все") }) }
                    items(allTags) { tag -> FilterChip(selected = selectedTag == tag, onClick = { selectedTag = if (selectedTag == tag) null else tag }, label = { Text(tag) }) }
                }
                Spacer(Modifier.height(8.dp))
            }
            
            if (selectionMode && selectedEntryIds.isNotEmpty()) {
                Button(
                    onClick = {
                        pendingDeleteAction = {
                            viewModel.deleteEntries(selectedEntryIds.toList(), profileId) { result ->
                                when (result) {
                                    is PasswordOperationResult.Success -> { operationMessage = result.message; selectedEntryIds = emptySet(); selectionMode = false }
                                    is PasswordOperationResult.Error -> operationMessage = result.message
                                }
                            }
                        }
                        showMasterPasswordDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Удалить выбранные (${selectedEntryIds.size})")
                }
            }
            
            if (filteredEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("Нет записей", fontWeight = FontWeight.Medium, fontSize = 18.sp)
                        Text("Добавьте первую запись или измените фильтры", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredEntries) { entry ->
                        EntryCard(
                            entry = entry,
                            selectionMode = selectionMode,
                            isSelected = entry.id in selectedEntryIds,
                            onToggleSelection = {
                                if (entry.id in selectedEntryIds) selectedEntryIds -= entry.id else selectedEntryIds += entry.id
                            },
                            onToggleFavorite = {
                                viewModel.toggleFavorite(entry) { result -> if (result is PasswordOperationResult.Error) operationMessage = result.message }
                            },
                            onDelete = { entryToDelete = entry },
                            onOpen = { entryToView = entry }, //  Открываем диалог просмотра, а не редактор
                            dateFormat = dateFormat
                        )
                    }
                }
            }
        }
    }

    //  Диалог просмотра записи (безопасный, без расшифровки в редакторе)
    if (entryToView != null) {
        PasswordViewDialog(
            entry = entryToView!!,
            onDismiss = { entryToView = null },
            onEdit = { 
                entryToView = null
                onNavigateToEntry(entryToView!!.id) 
            },
            onDelete = {
                entryToView = null
                entryToDelete = entryToView // Передаем в диалог удаления
            }
        )
    }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Удалить запись?") },
            text = { Text("Запись ${entryToDelete!!.service} будет удалена безвозвратно.") },
            confirmButton = {
                Button(
                    onClick = {
                        val entry = entryToDelete!!
                        entryToDelete = null
                        pendingDeleteAction = {
                            viewModel.deleteEntry(entry.id, profileId) { result ->
                                when (result) {
                                    is PasswordOperationResult.Success -> operationMessage = "Запись удалена"
                                    is PasswordOperationResult.Error -> operationMessage = result.message
                                }
                            }
                        }
                        showMasterPasswordDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { entryToDelete = null }) { Text("Отмена") } }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Удалить все пароли?") },
            text = { Text("Все пароли текущего профиля будут удалены безвозвратно.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        pendingDeleteAction = {
                            viewModel.deleteAllEntriesInProfile(profileId) { result ->
                                when (result) {
                                    is PasswordOperationResult.Success -> operationMessage = result.message
                                    is PasswordOperationResult.Error -> operationMessage = result.message
                                }
                            }
                        }
                        showMasterPasswordDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить все") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllDialog = false }) { Text("Отмена") } }
        )
    }

    if (showMasterPasswordDialog) {
        MasterPasswordDialog(
            context = context,
            onConfirm = { password ->
                showMasterPasswordDialog = false
                val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                //  ИСПРАВЛЕНО: Правильные ключи мастер-пароля
                val storedHash = prefs.getString("master_hash", "") ?: ""
                val storedSalt = prefs.getString("master_salt", "") ?: ""
                val iterations = prefs.getInt("master_iterations", 100000)
                
                if (MasterPasswordHasher.verify(password, storedHash, storedSalt, iterations)) {
                    pendingDeleteAction?.invoke()
                    pendingDeleteAction = null
                } else {
                    operationMessage = "Неверный мастер-пароль"
                }
            },
            onDismiss = { showMasterPasswordDialog = false; pendingDeleteAction = null }
        )
    }
}

@Composable
private fun EntryCard(
    entry: Entry,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !selectionMode) { onOpen() },
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.service, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(entry.username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.tags.isNotEmpty()) {
                    Text(entry.tags.joinToString(", "), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                if (entry.rotationEnabled && entry.nextRotationDate != null) {
                    Text("Ротация: ${dateFormat.format(Date(entry.nextRotationDate))}", fontSize = 10.sp, color = if (entry.nextRotationDate <= System.currentTimeMillis()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!selectionMode) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun MasterPasswordDialog(context: Context, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтверждение") },
        text = {
            Column {
                Text("Введите мастер-пароль для подтверждения операции:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Мастер-пароль") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } }
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) { Text("Подтвердить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
