@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Categories
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.viewmodel.VaultViewModel

@Composable
fun VaultListScreen(
    onNavigate: (String) -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val profileFilter by viewModel.profileFilter.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SecureVault", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { onNavigate("settings") }) { 
                        Icon(Icons.Default.Settings, "Настройки") 
                    }
                    IconButton(onClick = { onNavigate("rotation") }) { 
                        Icon(Icons.Default.Refresh, "Ротация") 
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = !favoritesOnly, 
                    onClick = { viewModel.toggleFavoritesOnly() }, 
                    icon = { Icon(Icons.Default.Key, null) }, 
                    label = { Text("Пароли", fontSize = 10.sp) }
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
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(profileFilter == null, { viewModel.setProfileFilter(null) }, label = { Text("Все") }, modifier = Modifier.weight(1f))
                FilterChip(profileFilter == Profile.PERSONAL, { viewModel.setProfileFilter(Profile.PERSONAL) }, label = { Text("Личные") }, modifier = Modifier.weight(1f))
                FilterChip(profileFilter == Profile.WORK, { viewModel.setProfileFilter(Profile.WORK) }, label = { Text("Работа") }, modifier = Modifier.weight(1f))
            }
            
            if (profileFilter != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(categoryFilter == null, { viewModel.setCategoryFilter(null) }, label = { Text("Все") }, modifier = Modifier.weight(1f))
                    Categories.getFor(profileFilter!!).take(3).forEach { cat ->
                        FilterChip(categoryFilter == cat, { viewModel.setCategoryFilter(cat) }, label = { Text(cat) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), 
                verticalArrangement = Arrangement.spacedBy(8.dp), 
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry, 
                        onClick = { onNavigate("editor/${entry.id}") }, 
                        onDelete = { viewModel.delete(entry) }, 
                        onToggleFavorite = { viewModel.toggleFavorite(entry) }
                    )
                }
                
                if (entries.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text("Нет записей", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(entry: Entry, onClick: () -> Unit, onDelete: () -> Unit, onToggleFavorite: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.service, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (entry.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(entry.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    if (entry.rotationEnabled) {
                        val days = entry.getDaysUntilRotation()
                        val color = when {
                            days == null || days!! > 7 -> MaterialTheme.colorScheme.onSurfaceVariant
                            days!! > 3 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                        Text("🔄 $days дн.", fontSize = 11.sp, color = color)
                    }
                }
            }
            Row {
                IconButton(onClick = onToggleFavorite) { 
                    Icon(
                        // ✅ ЯВНОЕ использование импортированных классов
                        imageVector = if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "Избранное",
                        tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                }
                IconButton(onClick = onDelete) { 
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) 
                }
            }
        }
    }
}
