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
import androidx.compose.ui.text.font.FontFamily
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
    viewModel: VaultViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val profileFilter by viewModel.profileFilter.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    
    // ✅ Добавили состояние для диалога просмотра пароля
    var viewingEntry by remember { mutableStateOf<Entry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SecureVault", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { onNavigate("settings") }) { Icon(Icons.Default.Settings, "Настройки") }
                    IconButton(onClick = { onNavigate("rotation") }) { Icon(Icons.Default.Refresh, "Ротация") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = !favoritesOnly,
                    onClick = { viewModel.toggleFavoritesOnly() },
                    icon = { Icon(Icons.Default.Key, null) },
                    label = { Text("Все", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = favoritesOnly,
                    onClick = { viewModel.toggleFavoritesOnly() },
                    icon = { Icon(Icons.Default.Star, null) },
                    label = { Text("Избранное", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate("settings") },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Настройки", fontSize = 10.sp) }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate("editor/new") }) {
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Фильтры профилей
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(profileFilter == null, { viewModel.setProfileFilter(null) }, label = { Text("Все") }, modifier = Modifier.weight(1f))
                FilterChip(profileFilter == Profile.PERSONAL, { viewModel.setProfileFilter(Profile.PERSONAL) }, label = { Text("Личные") }, modifier = Modifier.weight(1f))
                FilterChip(profileFilter == Profile.WORK, { viewModel.setProfileFilter(Profile.WORK) }, label = { Text("Работа") }, modifier = Modifier.weight(1f))
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    // ✅ Клик по карточке теперь открывает диалог просмотра, а не редактор
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

    // ✅ Диалог просмотра пароля
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
                    if (entry.isFavorite) Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                }
                Text(entry.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // ✅ Отображение тегов/смайликов, если они есть
                if (!entry.quickTags.isNullOrBlank()) {
                    Text(entry.quickTags, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onToggleFavorite) { 
                    Icon(if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.Star, null, tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) 
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
