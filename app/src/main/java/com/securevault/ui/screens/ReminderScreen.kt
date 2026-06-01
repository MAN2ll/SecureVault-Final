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
import com.securevault.viewmodel.VaultViewModel
import com.securevault.utils.ReminderManager

@Composable
fun ReminderScreen(onBack: () -> Unit, viewModel: VaultViewModel = hiltViewModel(), reminderManager: ReminderManager = ReminderManager()) {
    val all by viewModel.entries.collectAsState()
    val upcoming = remember(all) { reminderManager.getUpcoming(all) }

    Scaffold(topBar = { TopAppBar(title = { Text("Напоминания") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        if (upcoming.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("Нет предстоящих смен паролей") }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(upcoming, key = { it.id }) { e ->
                    Card { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(e.service, FontWeight.Bold); Text("Осталось: ${e.getDaysUntilRotation()} д.") }
                        TextButton({ viewModel.updatePassword(e.id, com.securevault.utils.PasswordGenerator.generate(12, true, true, true).password) }) { Text("Обновить") }
                    }}
                }
            }
        }
    }
}
