@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

// === FOUNDATION — БЕЗ weight() ===
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

// === MATERIAL3 — ЯВНЫЕ ИМПОРТЫ ===
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar

// === RUNTIME ===
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

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
fun ReminderScreen(    onBack: () -> Unit,
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
                title = { Text(text = "Напоминания") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (upcoming.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Нет предстоящих смен паролей")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(upcoming, key = { it.id }) { e ->
                    Card {
                        // ✅ ИСПРАВЛЕНО: Используем Arrangement.SpaceBetween вместо weight()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Левая часть: сервис + дата
                            Column(
                                modifier = Modifier.fillMaxWidth(0.7f)
                            ) {
                                Text(text = e.service, fontWeight = FontWeight.Bold)                                Text(
                                    text = "Осталось: ${e.getDaysUntilRotation()?.toString() ?: "—"} д.",
                                    fontSize = 12.sp
                                )
                            }
                            // Правая часть: кнопка
                            TextButton(onClick = {
                                viewModel.updatePassword(
                                    e.id,
                                    PasswordGenerator.generate(12, true, true, true).password
                                )
                            }) {
                                Text(text = "Обновить")
                            }
                        }
                    }
                }
            }
        }
    }
}