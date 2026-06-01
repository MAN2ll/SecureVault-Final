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
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.viewmodel.VaultViewModel

@Composable
fun VaultListScreen(
    onAdd: () -> Unit,
    onThemeChange: () -> Unit,
    onReminders: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val filter by viewModel.currentFilter.collectAsState()
    var viewEntry by remember { mutableStateOf<Entry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SecureVault", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onChangeTheme) { Icon(Icons.Default.BrightnessMedium, "Тема") }
                    IconButton(onReminders) { Icon(Icons.Default.Notifications, "Напоминания") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onAdd) { Icon(Icons.Default.Add, "Добавить") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == null, { viewModel.setFilter(null) }, label = { Text("Все") }, modifier = Modifier.weight(1f))
                Profile.entries.forEach { p ->
                    FilterChip(filter == p, { viewModel.setFilter(p) }, label = { Text(p.label) }, modifier = Modifier.weight(1f))
                }
            }
            LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Card(onClick = { viewEntry = entry }) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.service, fontWeight = FontWeight.SemiBold)
                                Text(entry.username)
                                Text("Профиль: ${entry.profile.label}", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton({ viewModel.delete(entry) }) { Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    viewEntry?.let { e ->
        PasswordViewDialog(entry = e, onDismiss = { viewEntry = null })
    }
}
