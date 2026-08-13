@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.ui.components.LockActionButton
import com.securevault.viewmodel.AuthViewModel
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
    onLock: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val profiles by profileViewModel.profiles.collectAsState()
    val currentProfile = remember(profileId, profiles) { profiles.find { it.id == profileId } }
    
    var searchQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    var showViewDialog by remember { mutableStateOf<Entry?>(null) }
    var showQrDialog by remember { mutableStateOf<Entry?>(null) }

    LaunchedEffect(Unit) {
        authViewModel.clearSensitiveEvent.collect {
            showViewDialog = null
            showQrDialog = null
        }
    }

    val filteredEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) entries else entries.filter {
            it.service.contains(searchQuery, ignoreCase = true) || it.username.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SecureVault", fontWeight = FontWeight.Bold) },
                actions = {
                    LockActionButton(onLock = onLock)
                    
                    IconButton(onClick = {
                        showSearchField = !showSearchField
                        if (!showSearchField) searchQuery = ""
                    }) {
                        Icon(if (showSearchField) Icons.Default.Close else Icons.Default.Search, null)
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Новая запись") }, onClick = { showMenu = false; onNavigateToNewEntry() }, leadingIcon = { Icon(Icons.Default.Add, null) })
                        DropdownMenuItem(text = { Text("Ротация паролей") }, onClick = { showMenu = false; onNavigateToRotation() }, leadingIcon = { Icon(Icons.Default.Schedule, null) })
                        DropdownMenuItem(text = { Text("Журнал ротации") }, onClick = { showMenu = false; onNavigateToRotationJournal() }, leadingIcon = { Icon(Icons.Default.History, null) })
                        DropdownMenuItem(text = { Text("Аудит безопасности") }, onClick = { showMenu = false; onNavigateToAudit() }, leadingIcon = { Icon(Icons.Default.Security, null) })
                        DropdownMenuItem(text = { Text("Экспорт / импорт") }, onClick = { showMenu = false; onNavigateToExport() }, leadingIcon = { Icon(Icons.Default.Upload, null) })
                        DropdownMenuItem(text = { Text("Настройки") }, onClick = { showMenu = false; onNavigateToSettings() }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Выйти к профилям", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onNavigateToProfiles() }, leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToNewEntry) {
                Icon(Icons.Default.Add, "Добавить запись")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (showSearchField) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Поиск") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    singleLine = true
                )
            }
            
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredEntries, key = { it.id }) { entry ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { showViewDialog = entry }) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (entry.isFavorite) Icons.Default.Star else Icons.Outlined.Star, null, tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.service, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(entry.username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (entry.rotationEnabled && entry.nextRotationDate != null && entry.nextRotationDate <= System.currentTimeMillis()) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showViewDialog != null && currentProfile != null) {
        PasswordViewDialog(
            entry = showViewDialog!!,
            profile = currentProfile,
            onDismiss = { showViewDialog = null },
            onEdit = { onNavigateToEntry(showViewDialog!!.id); showViewDialog = null },
            onQr = { showQrDialog = showViewDialog; showViewDialog = null },
            onDelete = { showViewDialog = null }
        )
    }

    if (showQrDialog != null && currentProfile != null) {
        QrCodeDialog(
            entry = showQrDialog!!,
            profile = currentProfile,
            onDismiss = { showQrDialog = null }
        )
    }
}
