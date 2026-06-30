@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import com.securevault.viewmodel.VaultViewModel

enum class SortOption(val label: String) {
    NAME_ASC("По имени (А-Я)"),
    NAME_DESC("По имени (Я-А)"),
    DATE_CREATED_DESC("Сначала новые"),
    DATE_CREATED_ASC("Сначала старые"),
    DATE_CHANGED_DESC("Недавно изменённые")
}

@Composable
fun VaultListScreen(
    profileId: Int?,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onLock: () -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    var viewingEntry by remember { mutableStateOf<Entry?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    
    var sortOption by remember { mutableStateOf(SortOption.DATE_CREATED_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(profileId) {
        viewModel.setCurrentProfile(profileId)
    }

    val filteredAndSortedEntries = remember(entries, searchQuery, favoritesOnly, sortOption) {
        val filtered = entries.filter { entry ->
            val matchesSearch = searchQuery.isBlank() || 
                entry.service.contains(searchQuery, ignoreCase = true) ||
                entry.username.contains(searchQuery, ignoreCase = true) ||
                (entry.notes?.contains(searchQuery, ignoreCase = true) == true)
            val matchesFavorite = !favoritesOnly || entry.isFavorite
            matchesSearch && matchesFavorite
        }
        
        when (sortOption) {
            SortOption.NAME_ASC -> filtered.sortedBy { it.service.lowercase() }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.service.lowercase() }
            SortOption.DATE_CREATED_DESC -> filtered.sortedByDescending { it.createdAt }
            SortOption.DATE_CREATED_ASC -> filtered.sortedBy { it.createdAt }
            SortOption.DATE_CHANGED_DESC -> filtered.sortedByDescending { it.lastChanged }
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { isSearchActive = false },
                    active = true,
                    onActiveChange = { isSearchActive = it },
                    placeholder = { Text("Поиск по сервису или логину") },
                    leadingIcon = { 
                        IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                            Icon(Icons.Default.ArrowBack, "Закрыть поиск")
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, "Очистить")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (searchQuery.isNotBlank() && filteredAndSortedEntries.isNotEmpty()) {
                        LazyColumn {
                            items(filteredAndSortedEntries) { entry ->
                                ListItem(
                                    headlineContent = { Text(entry.service) },
                                    supportingContent = { Text(entry.username) },
                                    leadingContent = {
                                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    modifier = Modifier.clickable {
                                        viewingEntry = entry
                                        isSearchActive = false
                                        searchQuery = ""
                                    }
                                )
                            }
                        }
                    } else if (searchQuery.isNotBlank()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = { Text("Пароли", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Назад")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, "Поиск")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, "Сортировка")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            sortOption = option
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (sortOption == option) {
                                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onNavigate("audit") }) {
                            Icon(Icons.Default.Security, "Аудит")
                        }
                        IconButton(onClick = { onNavigate("export") }) {
                            Icon(Icons.Default.SwapHoriz, "Экспорт/Импорт")
                        }
                        IconButton(onClick = { onNavigate("settings") }) {
                            Icon(Icons.Default.Settings, "Настройки")
                        }
                        IconButton(onClick = { onNavigate("rotation") }) {
                            Icon(Icons.Default.Refresh, "Ротация")
                        }
                        IconButton(onClick = onLock) {
                            Icon(Icons.Default.Lock, "Заблокировать")
                        }
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    !favoritesOnly, 
                    { viewModel.toggleFavoritesOnly() }, 
                    { Icon(Icons.Default.Key, null) }, 
                    label = { Text("Все", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    favoritesOnly, 
                    { viewModel.toggleFavoritesOnly() }, 
                    { Icon(Icons.Default.Star, null) }, 
                    label = { Text("Избранное", fontSize = 10.sp) }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate("editor/new") }) {
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchQuery.isNotBlank()) {
                Text(
                    "Найдено: ${filteredAndSortedEntries.size}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredAndSortedEntries, key = { it.id }) { entry ->
                    EntryCard(
                        entry,
                        { viewingEntry = entry },
                        { onNavigate("editor/${entry.id}") },
                        { viewModel.delete(entry) },
                        { viewModel.toggleFavorite(entry) }
                    )
                }
                if (filteredAndSortedEntries.isEmpty() && !isSearchActive) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Security, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(16.dp))
                                Text("Нет записей", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Нажмите + чтобы добавить", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }

    viewingEntry?.let { PasswordViewDialog(it, { viewingEntry = null }) }
}

@Composable
private fun EntryCard(entry: Entry, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onToggleFavorite: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(entry.service, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (entry.isFavorite) {
                        Icon(Icons.Default.Star, null, Modifier.size(16.dp).padding(start = 4.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(entry.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.rotationEnabled) {
                    val days = entry.getDaysUntilRotation()
                    val color = when {
                        days == null || days > 7 -> MaterialTheme.colorScheme.onSurfaceVariant
                        days > 3 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(12.dp), tint = color)
                        Spacer(Modifier.width(4.dp))
                        Text("${days ?: 0} дн.", fontSize = 11.sp, color = color)
                    }
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
