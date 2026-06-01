@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

// === FOUNDATION ===
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

// === MATERIAL3 ===
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*

// === RUNTIME ===
import androidx.compose.runtime.*

// === UI ===
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// === HILT ===
import androidx.hilt.navigation.compose.hiltViewModel

// === PROJECT ===
import com.securevault.data.Entry
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel

@Composable
fun ReminderScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val all by viewModel.entries.collectAsState()
    val upcoming = remember(all) {
        all.filter { e ->
            e.rotationEnabled && e.nextRotationDate != null &&
            (e.getDaysUntilRotation() ?: Int.MAX_VALUE) in 0..7
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Напоминания") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        if (upcoming.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center
            ) {
                Text("Нет предстоящих смен паролей")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(upcoming, key = { it.id }) { e ->
                    Card {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.service, FontWeight.Bold)
                                // ✅ ИСПРАВЛЕНО: только именованные параметры
                                Text(
                                    text = "Осталось: ${e.getDaysUntilRotation()} д.",
                                    fontSize = 12.sp
                                )
                            }
                            TextButton({
                                viewModel.updatePassword(
                                    e.id,
                                    PasswordGenerator.generate(12, true, true, true).password
                                )
                            }) {
                                Text("Обновить")
                            }
                        }
                    }
                }
            }
        }
    }
}
