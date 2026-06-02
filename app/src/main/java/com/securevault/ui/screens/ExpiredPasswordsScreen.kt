@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiredPasswordsScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val allEntries by viewModel.entries.collectAsState()
    val expiredEntries = allEntries.filter { it.isPasswordExpired() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Просроченные пароли", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (expiredEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Все пароли актуальны!", modifier = Modifier.padding(16.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(expiredEntries, key = { it.id }) { entry ->
                    ExpiredEntryCard(entry = entry, onClick = { onEdit(entry.id) })
                }
            }
        }
    }
}

@Composable
fun ExpiredEntryCard(entry: Entry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.service,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                // ✅ ИСПРАВЛЕНО: безопасная обработка null
                val days = entry.getDaysUntilRotation() ?: 0
                Text(
                    text = "Просрочен на ${-days} д.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = onClick) {
                Text("Сменить", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
