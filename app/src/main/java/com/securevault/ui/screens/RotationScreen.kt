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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val rotationEntries by viewModel.rotationEntries.collectAsState()
    var selectedEntry by remember { mutableStateOf<Entry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ротация паролей", fontWeight = FontWeight.Bold) },
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
        ) {
            // Информация
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Записи с включённой ротацией. Нажмите «Заменить» для выбора нового пароля.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (rotationEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Schedule,
                            null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Нет записей с ротацией",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Включите ротацию в настройках записи",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(rotationEntries, key = { it.id }) { entry ->
                        RotationEntryCard(
                            entry = entry,
                            onReplace = { selectedEntry = entry }
                        )
                    }
                }
            }
        }
    }

    // Диалог замены пароля
    selectedEntry?.let { entry ->
        PasswordRotationDialog(
            serviceName = entry.service,
            currentHint = entry.textHint,
            generationType = entry.generationType,
            rotationMonth = null,
            rotationYear = null,
            onDismiss = { selectedEntry = null },
            onPasswordReplaced = { newPassword, newHint, newGenerationType ->
                viewModel.replacePassword(entry.id, newPassword, newHint, newGenerationType)
                selectedEntry = null
            }
        )
    }
}

@Composable
private fun RotationEntryCard(
    entry: Entry,
    onReplace: () -> Unit
) {
    val daysLeft = entry.getDaysUntilRotation()
    val isExpired = entry.isPasswordExpired()
    
    val statusColor = when {
        isExpired -> MaterialTheme.colorScheme.error
        daysLeft != null && daysLeft <= 3 -> MaterialTheme.colorScheme.error
        daysLeft != null && daysLeft <= 7 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val statusText = when {
        isExpired -> "Просрочено"
        daysLeft != null -> "Осталось $daysLeft дн."
        else -> "Неизвестно"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpired) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Folder,
                        null,
                        Modifier.size(20.dp),
                        tint = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        entry.service,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.username,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        null,
                        Modifier.size(12.dp),
                        tint = statusColor
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }
                if (entry.generationType == "mnemonic") {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lightbulb,
                            null,
                            Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Мнемонический",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            
            Button(
                onClick = onReplace,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Заменить")
            }
        }
    }
}
