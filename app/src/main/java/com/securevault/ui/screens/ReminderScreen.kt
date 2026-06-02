@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Левая колонка (70% ширины)
                            Column(
                                modifier = Modifier.fillMaxWidth(0.7f)
                            ) {
                                Text(
                                    text = e.service, 
                                    fontWeight = FontWeight.Bold
                                )
                                
                                // ✅ ИСПРАВЛЕНО: простая конкатенация вместо сложной интерполяции
                                val daysText = e.getDaysUntilRotation()?.toString() ?: "—"
                                Text(
                                    text = "Осталось: $daysText д.",
                                    fontSize = 12.sp
                                )
                            }
                            
                            // Правая колонка (кнопка)
                            TextButton(
                                onClick = {
                                    val newPwd = PasswordGenerator.generate(12, true, true, true).password
                                    viewModel.updatePassword(e.id, newPwd)
                                }
                            ) {
                                Text(text = "Обновить")
                            }
                        }
                    }
                }
            }
        }
    }
}
