@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    
    val weakPasswords = entries.filter { it.password.length < 10 }
    val expiredPasswords = entries.filter { it.isPasswordExpired() }
    val totalPasswords = entries.size
    val score = if (totalPasswords == 0) 100 else {
        val penalty = (weakPasswords.size * 20) + (expiredPasswords.size * 15)
        (100 - penalty).coerceAtLeast(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аудит безопасности", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        score >= 80 -> MaterialTheme.colorScheme.primaryContainer
                        score >= 50 -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Общий рейтинг безопасности", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "$score%",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            score >= 80 -> MaterialTheme.colorScheme.primary
                            score >= 50 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Всего паролей: $totalPasswords", fontSize = 14.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Слабые", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${weakPasswords.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
                Card(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Просрочено", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${expiredPasswords.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Рекомендации", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (weakPasswords.isNotEmpty()) {
                        Text("• Усилить ${weakPasswords.size} слабых паролей (минимум 10 символов)", fontSize = 13.sp)
                    }
                    if (expiredPasswords.isNotEmpty()) {
                        Text("• Обновить ${expiredPasswords.size} просроченных паролей", fontSize = 13.sp)
                    }
                    if (weakPasswords.isEmpty() && expiredPasswords.isEmpty()) {
                        Text("• Отличный уровень безопасности!", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
