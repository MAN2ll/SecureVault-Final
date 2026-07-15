@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    profileId: Int?,
    onNavigateToEntry: (String) -> Unit,
    onNavigateToNewEntry: () -> Unit,
    onNavigateToAudit: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToRotation: () -> Unit,
    onNavigateToRotationJournal: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMnemonicGenerator: () -> Unit,
    onNavigateToQrScanner: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onLock: () -> Unit, //  Добавлен параметр блокировки
    authViewModel: AuthViewModel = hiltViewModel(),
    viewModel: VaultViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(profileId) {
        if (profileId != null) {
            viewModel.setCurrentProfile(profileId)
        }
    }

    val entries by viewModel.entries.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val profiles by profileViewModel.profiles.collectAsState()

    var selectionMode by remember { mutableStateOf(false) }
    var selectedEntries by remember { mutableStateOf<Set<String>>(emptySet()) }

    var searchQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf<Entry?>(null) }
    var showViewDialog by remember { mutableStateOf<Entry?>(null) }

    var entryToDelete by remember { mutableStateOf<Entry?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showMasterPasswordDialog by remember { mutableStateOf<MasterPasswordAction?>(null) }
    var operationError by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(entries) {
        selectedEntries = selectedEntries.filter { id -> entries.any { it.id == id } }.toSet()
        if (selectedEntries.isEmpty()) selectionMode = false
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("Выбрано: ${selectedEntries.size}", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectionMode = false
                            selectedEntries = emptySet()
                        }) {
                            Icon(Icons.Default.Close, "Отмена выбора")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedEntries.isNotEmpty()) {
                                    showMasterPasswordDialog = MasterPasswordAction.DELETE_SELECTED
                                }
                            },
                            enabled = selectedEntries.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, "Удалить выбранные",
                                tint = if (selectedEntries.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("SecureVault", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = {
                            showSearchField = !showSearchField
                            if (!showSearchField) searchQuery = ""
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
                                    text = { Text("Выбрать записи") },
                                    onClick = { showMenu = false; selectionMode = true },
                                    leadingIcon = { Icon(Icons.Default.Checklist, null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Сканировать QR") },
                                    onClick = { showMenu = false; onNavigateToQrScanner() },
                                    leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ротация паролей") },
                                    onClick = { showMenu = false; onNavigateToRotation() },
                                    leadingIcon = { Icon(Icons.Default.Schedule, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Журнал ротации") },
                                    onClick = { showMenu = false; onNavigateToRotationJournal() },
                                    leadingIcon = { Icon(Icons.Default.History, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Аудит безопасности") },
                                    onClick = { showMenu = false; onNavigateToAudit() },
                                    leadingIcon = { Icon(Icons.Default.Security, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Мнемонический генератор") },
                                    onClick = { showMenu = false; onNavigateToMnemonicGenerator() },
                                    leadingIcon = { Icon(Icons.Default.Lightbulb, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Экспорт / импорт") },
                                    onClick = { showMenu = false; onNavigateToExport() },
                                    leadingIcon = { Icon(Icons.Default.Upload, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Настройки профиля") },
                                    onClick = { showMenu = false; onNavigateToSettings() },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Удалить все пароли профиля", color = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; showMasterPasswordDialog = MasterPasswordAction.DELETE_ALL_PROFILE },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Выйти к профилям") },
                                    onClick = { showMenu = false; viewModel.setCurrentProfile(null); onNavigateToProfiles() },
                                    leadingIcon = { Icon(Icons.Default.ExitToApp, null) }
                                )
                                HorizontalDivider()
                                //  Реальная блокировка с переходом на экран Lock
                                DropdownMenuItem(
                                    text = { Text("Заблокировать приложение", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.setCurrentProfile(null)
                                        authViewModel.lock()
                                        onLock()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = onNavigateToNewEntry, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "Добавить запись")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (showSearchField) {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Поиск по сервисам") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Очистить") } },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (filteredEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text(if (searchQuery.isNotBlank()) "Ничего не найдено" else if (favoritesOnly) "Нет избранных записей" else "Нет сохранённых паролей", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text("Записей: ${filteredEntries.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry, selectionMode = selectionMode, isSelected = entry.id in selectedEntries,
                            onClick = { if (selectionMode) selectedEntries = if (entry.id in selectedEntries) selectedEntries - entry.id else selectedEntries + entry.id else showViewDialog = entry },
                            onEditClick = { onNavigateToEntry(entry.id) },
                            onFavoriteClick = { viewModel.toggleFavorite(entry) },
                            onQrClick = { showQrDialog = entry },
                            onDeleteClick = { entryToDelete = entry }
                        )
                    }
                }
            }
        }
    }

    if (showViewDialog != null) {
        val currentProfileId = viewModel.currentProfileId.value
        val currentProfile = profiles.find { it.id == currentProfileId }
        if (currentProfile != null) {
            PasswordViewDialog(
                entry = showViewDialog!!, profile = currentProfile,
                onDismiss = { showViewDialog = null },
                onEdit = { val entry = showViewDialog!!; showViewDialog = null; onNavigateToEntry(entry.id) },
                onQr = { val entry = showViewDialog!!; showViewDialog = null; showQrDialog = entry },
                onDelete = { val entry = showViewDialog!!; showViewDialog = null; entryToDelete = entry }
            )
        } else {
            showViewDialog = null
        }
    }

    if (showQrDialog != null) {
        QrCodeDialog(entry = showQrDialog!!, onDismiss = { showQrDialog = null })
    }

    if (entryToDelete != null) {
        MasterPasswordConfirmDialog(
            title = "Подтверждение удаления",
            onConfirmed = {
                val entry = entryToDelete!!
                val currentProfile = viewModel.currentProfileId.value
                if (currentProfile == null) { operationError = "Профиль не выбран"; entryToDelete = null; return@MasterPasswordConfirmDialog }
                viewModel.deleteEntry(entry.id, currentProfile) { result ->
                    when (result) {
                        is PasswordOperationResult.Success -> entryToDelete = null
                        is PasswordOperationResult.Error -> { operationError = result.message; entryToDelete = null }
                    }
                }
            },
            onDismiss = { entryToDelete = null }
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить выбранные записи?") },
            text = { Text("Будет удалено записей: ${selectedEntries.size}. Это действие необратимо.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteSelectedDialog = false
                    val currentProfile = viewModel.currentProfileId.value
                    if (currentProfile != null) {
                        viewModel.deleteEntries(selectedEntries.toList(), currentProfile) { result ->
                            when (result) {
                                is PasswordOperationResult.Success -> { selectedEntries = emptySet(); selectionMode = false }
                                is PasswordOperationResult.Error -> operationError = result.message
                            }
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showDeleteSelectedDialog = false }) { Text("Отмена") } }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить все пароли профиля?") },
            text = { Text("Все пароли текущего профиля будут удалены. Это действие необратимо.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteAllDialog = false
                    val currentProfile = viewModel.currentProfileId.value
                    if (currentProfile != null) {
                        viewModel.deleteAllEntriesInProfile(currentProfile) { result ->
                            if (result is PasswordOperationResult.Error) operationError = result.message
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить всё") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllDialog = false }) { Text("Отмена") } }
        )
    }

    if (showMasterPasswordDialog != null) {
        val action = showMasterPasswordDialog!!
        MasterPasswordConfirmDialog(
            title = when (action) {
                MasterPasswordAction.DELETE_SELECTED -> "Подтверждение массового удаления"
                MasterPasswordAction.DELETE_ALL_PROFILE -> "Подтверждение удаления всех паролей"
            },
            onConfirmed = {
                when (action) {
                    MasterPasswordAction.DELETE_SELECTED -> showDeleteSelectedDialog = true
                    MasterPasswordAction.DELETE_ALL_PROFILE -> showDeleteAllDialog = true
                }
                showMasterPasswordDialog = null
            },
            onDismiss = { showMasterPasswordDialog = null }
        )
    }

    if (operationError != null) {
        AlertDialog(
            onDismissRequest = { operationError = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ошибка") },
            text = { Text(operationError ?: "") },
            confirmButton = { TextButton(onClick = { operationError = null }) { Text("Понятно") } }
        )
    }
}

enum class MasterPasswordAction { DELETE_SELECTED, DELETE_ALL_PROFILE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterPasswordConfirmDialog(title: String, onConfirmed: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Введите мастер-пароль для подтверждения действия", fontSize = 13.sp)
                OutlinedTextField(
                    value = password, onValueChange = { password = it; error = null }, label = { Text("Мастер-пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = error != null
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val storedHash = prefs.getString("master_hash", null)
                val storedSalt = prefs.getString("master_salt", null)
                val iterations = prefs.getInt("master_iterations", 100_000)
                if (storedHash != null && storedSalt != null && MasterPasswordHasher.verify(password, storedHash, storedSalt, iterations)) {
                    onConfirmed()
                } else {
                    error = "Неверный мастер-пароль"
                }
                password = ""
            }) { Text("Подтвердить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun EntryCard(
    entry: Entry, selectionMode: Boolean, isSelected: Boolean,
    onClick: () -> Unit, onEditClick: () -> Unit, onFavoriteClick: () -> Unit, onQrClick: () -> Unit, onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = when { selectionMode && isSelected -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant })
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 8.dp))
            }
            Icon(Icons.Default.Folder, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.service, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(entry.username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val daysLeft = entry.getDaysUntilRotation()
                    val isExpired = entry.isPasswordExpired()
                    if (isExpired) {
                        Spacer(Modifier.width(4.dp))
                        Text("Просрочено", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    } else if (daysLeft != null && daysLeft <= 7) {
                        Spacer(Modifier.width(4.dp))
                        Text("Осталось $daysLeft дн.", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Medium)
                    }
                    when (entry.generationType) {
                        "mnemonic" -> { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Lightbulb, null, Modifier.size(10.dp), tint = MaterialTheme.colorScheme.tertiary); Text(" AMPG v2", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary) }
                        "shuffled" -> { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Shuffle, null, Modifier.size(10.dp), tint = MaterialTheme.colorScheme.secondary); Text(" Перемешанный", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary) }
                        "manual" -> { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Edit, null, Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(" Ручной", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            if (!selectionMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Меню записи") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Просмотр") }, onClick = { showMenu = false; onClick() }, leadingIcon = { Icon(Icons.Default.Visibility, null) })
                        DropdownMenuItem(text = { Text("Изменить") }, onClick = { showMenu = false; onEditClick() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem(text = { Text("QR-код") }, onClick = { showMenu = false; onQrClick() }, leadingIcon = { Icon(Icons.Default.QrCode, null) })
                        DropdownMenuItem(text = { Text(if (entry.isFavorite) "Убрать из избранного" else "В избранное") }, onClick = { showMenu = false; onFavoriteClick() }, leadingIcon = { Icon(if (entry.isFavorite) Icons.Default.Star else Icons.Outlined.Star, null, tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDeleteClick() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            }
        }
    }
}
