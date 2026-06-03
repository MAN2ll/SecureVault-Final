@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

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
import com.securevault.data.Profile
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    onNavigate: (String) -> Unit,
    onLock: () -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val profileFilter by viewModel.profileFilter.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    
    var viewingEntry by remember { mutableStateOf<Entry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SecureVault", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { onNavigate("settings") }) { 
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Настройки") 
                    }
                    IconButton(onClick = { onNavigate("rotation") }) { 
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Ротация") 
                    }
                    IconButton(onClick = { onLock() }) { 
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Заблокировать") 
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = !favoritesOnly,
                    onClick = { viewModel.toggleFavoritesOnly() },
                    icon = { Icon(imageVector = Icons.Default.Key, contentDescription = null) },
                    label = { Text("Все", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = favoritesOnly,
                    onClick = { viewModel.toggleFavoritesOnly() },
                    icon = { Icon(imageVector = Icons.Default.Star, contentDescription = null) },
                    label = { Text("Избранное", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate("settings") },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Настройки", fontSize = 10.sp) }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate("editor/new") }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Добавить")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(profileFilter == null, { viewModel.setProfileFilter(null) }, label = { Text("Все") }, modifier = Modifier.weight(1f))
                FilterChip(profileFilter == Profile.PERSONAL, { viewModel.setProfileFilter(Profile.PERSONAL) }, label = { Text("Личные") }, modifier = Modifier.weight(1f))
                FilterChip(profileFilter == Profile.WORK, { viewModel.setProfileFilter(Profile.WORK) }, label = { Text("Работа") }, modifier = Modifier.weight(1f))
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry, 
                        onClick = { viewingEntry = entry }, 
                        onEdit = { onNavigate("editor/${entry.id}") },
                        onDelete = { viewModel.delete(entry) }, 
                        onToggleFavorite = { viewModel.toggleFavorite(entry) }
                    )
                }
                
                if (entries.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Нет записей", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    viewingEntry?.let { entry ->
        PasswordViewDialog(
            entry = entry,
            onDismiss = { viewingEntry = null }
        )
    }
}

@Composable
private fun EntryCard(entry: Entry, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onToggleFavorite: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.service, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (entry.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(entry.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!entry.quickTags.isNullOrBlank()) {
                    Text(entry.quickTags, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Row {
                IconButton(onClick = onEdit) { 
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) 
                }
                IconButton(onClick = onToggleFavorite) { 
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = null,
                        tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                }
                IconButton(onClick = onDelete) { 
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) 
                }
            }
        }
    }
}
