@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.securevault.data.Profile
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onLock: () -> Unit,
    onExport: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SecureVault",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Резервная копия"
                        )
                    }
                    IconButton(onClick = onLock) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Заблокировать"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 🔘 ФИЛЬТРЫ: Все / Личные / Рабочие
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ProfileFilterChip(
                        selected = currentFilter == null,
                        onClick = { viewModel.setFilter(null) },
                        label = { Text("Все", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    ProfileFilterChip(
                        selected = currentFilter == Profile.PERSONAL,
                        onClick = { viewModel.setFilter(Profile.PERSONAL) },
                        label = { Text("Личные", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    ProfileFilterChip(
                        selected = currentFilter == Profile.WORK,
                        onClick = { viewModel.setFilter(Profile.WORK) },
                        label = { Text("Рабочие", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 📋 СПИСОК ЗАПИСЕЙ
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Нет записей — добавьте первую!",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        EntryCard(entry = entry, onClick = { onEdit(entry.id) })
                    }
                }
            }
        }
    }
}

// ✅ ИСПРАВЛЕНО: Уникальное имя функции + вызов правильного Material3 компонента
@Composable
private fun ProfileFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // ✅ Явно указываем пакет: androidx.compose.material3.FilterChip
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier
    )
}

@Composable
fun EntryCard(entry: Entry, onClick: () -> Unit) {
    val borderColor = when (entry.getExpiryStatus()) {
        Entry.ExpiryStatus.EXPIRED -> MaterialTheme.colorScheme.error
        Entry.ExpiryStatus.CRITICAL -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        Entry.ExpiryStatus.WARNING -> MaterialTheme.colorScheme.tertiary
        Entry.ExpiryStatus.OK -> MaterialTheme.colorScheme.outline
    }

    val daysUntilExpiry = entry.getDaysUntilExpiry()

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.service,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = entry.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Копировать"
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (entry.profile == Profile.WORK) "Работа" else "Личное",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                if (entry.getExpiryStatus() != Entry.ExpiryStatus.OK) {
                    val statusText = when (entry.getExpiryStatus()) {
                        Entry.ExpiryStatus.EXPIRED -> "Просрочен"
                        Entry.ExpiryStatus.CRITICAL -> "$daysUntilExpiry д."
                        Entry.ExpiryStatus.WARNING -> "$daysUntilExpiry д."
                        Entry.ExpiryStatus.OK -> ""
                    }
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = borderColor
                    )
                }
            }
        }
    }
}
