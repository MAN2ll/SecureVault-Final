@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.security.MasterPasswordHasher
import com.securevault.viewmodel.AuthViewModel
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    onNavigateToEntry: (String) -> Unit,
    onNavigateToNewEntry: () -> Unit,
    onNavigateToAudit: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToRotation: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMnemonicGenerator: () -> Unit,
    onNavigateToQrScanner: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    viewModel: VaultViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    var showQrDialog by remember { mutableStateOf<Entry?>(null) }
    
    //  Состояние для удаления записи
    var entryToDelete by remember { mutableStateOf<Entry?>(null) }

    val filteredEntries = remember(entries, searchQuery, favoritesOnly) {
        var result = entries
        if (searchQuery.isNotBlank()) {
            result = result.filter { 
                it.service.contains(searchQuery, ignoreCase = true) ||
                it.username.contains(searchQuery, ignoreCase = true)
            }
        }
        if (favoritesOnly) {
            result = result.filter { it.isFavorite }
        }
        result.sortedBy { it.service.lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SecureVault", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { 
                        showSearchField = !showSearchField
                        if (showSearchField.not()) searchQuery = ""
                    }) {
                        Icon(
                            if (showSearchField) Icons.Default.Close else Icons.Default.Search,
                            if (showSearchField) "Закрыть поиск" else "Поиск"
                        )
                    }
                    
                    IconButton(onClick = { viewModel.toggleFavoritesOnly() }) {
                        Icon(
                            if (favoritesOnly) Icons.Default.Star else Icons.Outlined.Star,
                            "Избранное",
                            tint = if (favoritesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Меню")
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Аудит безопасности") },
                                onClick = { 
                                    showMenu = false
                                    onNavigateToAudit()
                                },
                                leadingIcon = { Icon(Icons.Default.Security, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Экспорт / импорт") },
                                onClick = { 
                                    showMenu = false
                                    onNavigateToExport()
                                },
                                leadingIcon = { Icon(Icons.Default.Upload, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Ротация паролей") },
                                onClick = { 
                                    showMenu = false
                                    onNavigateToRotation()
                                },
                                leadingIcon = { Icon(Icons.Default.Schedule, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Мнемонический генератор") },
                                onClick = { 
                                    showMenu = false
                                    onNavigateToMnemonicGenerator()
                                },
                                leadingIcon = { Icon(Icons.Default.Lightbulb, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Сканировать QR") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToQrScanner()
                                },
                                leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Настройки") },
                                onClick = { 
                                    showMenu = false
                                    onNavigateToSettings()
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Удалить все записи", color = MaterialTheme.colorScheme.error) },
                                onClick = { 
                                    showMenu = false
                                    showDeleteAllDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) }
                            )
                            DropdownMenuItem(
                                text = { Text("Заблокировать", color = MaterialTheme.colorScheme.error) },
                                onClick = { 
                                    showMenu = false
                                    authViewModel.lock()
                                },
                                leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToNewEntry,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Добавить запись")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showSearchField) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Поиск по сервисам") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, "Очистить")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.Lock,
                            null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isNotBlank()) 
                                "Ничего не найдено" 
                            else if (favoritesOnly) 
                                "Нет избранных записей"
                            else 
                                "Нет сохранённых паролей",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        if (searchQuery.isBlank() && !favoritesOnly) {
                            Text(
                                "Нажмите + чтобы добавить первую запись",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Записей: ${filteredEntries.size}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            onClick = { onNavigateToEntry(entry.id) },
                            onFavoriteClick = { viewModel.toggleFavorite(entry) },
                            onQrClick = { showQrDialog = entry },
                            onDeleteClick = { entryToDelete = entry } // ✅ НОВОЕ
                        )
                    }
                }
            }
        }
    }

    // ✅ НОВОЕ: Диалог удаления записи с подтверждением мастер-паролем
    if (entryToDelete != null) {
        ConfirmDeleteEntryDialog(
            entry = entryToDelete!!,
            context = context,
            onDismiss = { entryToDelete = null },
            onConfirmed = {
                viewModel.delete(entryToDelete!!)
                entryToDelete = null
            }
        )
    }

    if (showDeleteAllDialog) {
        ConfirmDeleteAllDialog(
            context = context,
            onDismiss = { showDeleteAllDialog = false },
            onConfirmed = {
                viewModel.deleteAll()
                showDeleteAllDialog = false
            }
        )
    }

    if (showQrDialog != null) {
        QrCodeDialog(
            entry = showQrDialog!!,
            onDismiss = { showQrDialog = null }
        )
    }
}

@Composable
private fun EntryCard(
    entry: Entry,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onQrClick: () -> Unit,
    onDeleteClick: () -> Unit // ✅ НОВЫЙ ПАРАМЕТР
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                null,
                Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.service,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    entry.username,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val daysLeft = entry.getDaysUntilRotation()
                    val isExpired = entry.isPasswordExpired()
                    
                    if (isExpired) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Просрочено",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (daysLeft != null && daysLeft <= 7) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Осталось $daysLeft дн.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    if (entry.generationType == "mnemonic") {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Lightbulb, null, Modifier.size(10.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(" AMPG", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary)
                    }
                    
                    if (entry.generationType == "shuffled") {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Shuffle, null, Modifier.size(10.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(" Перемешанный", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Меню записи")
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("QR-код") },
                        onClick = {
                            showMenu = false
                            onQrClick()
                        },
                        leadingIcon = { Icon(Icons.Default.QrCode, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Избранное") },
                        onClick = {
                            showMenu = false
                            onFavoriteClick()
                        },
                        leadingIcon = {
                            Icon(
                                if (entry.isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                                null,
                                tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    // ✅ НОВОЕ: Пункт меню "Удалить"
                    DropdownMenuItem(
                        text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

//  Подтверждение удаления записи с мастер-паролем
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmDeleteEntryDialog(
    entry: Entry,
    context: Context,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    var masterPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Удалить запись?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Вы уверены, что хотите удалить запись \"${entry.service}\"?")
                Text("Это действие необратимо.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("Для подтверждения введите мастер-пароль:", fontSize = 13.sp)
                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it; error = null },
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
            Button(
                onClick = {
                    val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    val storedHash = prefs.getString("master_hash", null)
                    val storedSalt = prefs.getString("master_salt", null)
                    val iterations = prefs.getInt("master_iterations", 100_000)

                    if (storedHash != null && storedSalt != null &&
                        MasterPasswordHasher.verify(masterPassword, storedHash, storedSalt, iterations)) {
                        onConfirmed()
                    } else {
                        error = "Неверный мастер-пароль"
                    }
                    masterPassword = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Удалить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

//  Подтверждение удаления всех записей с мастер-паролем
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmDeleteAllDialog(
    context: Context,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    var masterPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Удалить все записи?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Вы уверены, что хотите удалить ВСЕ записи в текущем профиле?")
                Text("Это действие необратимо.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("Для подтверждения введите мастер-пароль:", fontSize = 13.sp)
                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it; error = null },
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
            Button(
                onClick = {
                    val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    val storedHash = prefs.getString("master_hash", null)
                    val storedSalt = prefs.getString("master_salt", null)
                    val iterations = prefs.getInt("master_iterations", 100_000)

                    if (storedHash != null && storedSalt != null &&
                        MasterPasswordHasher.verify(masterPassword, storedHash, storedSalt, iterations)) {
                        onConfirmed()
                    } else {
                        error = "Неверный мастер-пароль"
                    }
                    masterPassword = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Удалить всё")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
