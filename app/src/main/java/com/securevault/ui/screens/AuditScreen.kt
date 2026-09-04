@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.clickable
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
import com.securevault.ui.components.LockActionButton
import com.securevault.utils.CryptoUtils
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(
    profileId: Int?,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onEditEntry: (String) -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    
    val weakPasswords = remember(entries) {
        entries.filter { entry ->
            try {
                val pwd = CryptoUtils.decrypt(entry.encryptedPassword)
                pwd.length < 8 || pwd.count { it.isUpperCase() } < 1 || 
                pwd.count { it.isLowerCase() } < 1 || pwd.count { it.isDigit() } < 1
            } catch (e: Exception) { false }
        }
    }
    
    val duplicatePasswords = remember(entries) {
        val fingerprintMap = entries.groupBy { it.passwordFingerprint ?: "" }
        fingerprintMap.filter { it.key.isNotEmpty() && it.value.size > 1 }.values.flatten()
    }
    
    val expiredPasswords = remember(entries) {
        entries.filter { entry ->
            entry.rotationEnabled && entry.nextRotationDate != null && 
            entry.nextRotationDate < System.currentTimeMillis()
        }
    }
    
    val totalIssues = weakPasswords.size + duplicatePasswords.size + expiredPasswords.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Аудит безопасности", fontWeight = FontWeight.Bold)
                        Text("Найдено проблем: $totalIssues", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") }
                },
                actions = { LockActionButton(onLock = onLock) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AuditSection(
                    title = "Слабые пароли",
                    count = weakPasswords.size,
                    description = "Менее 8 символов или недостаточно разнообразия",
                    icon = Icons.Default.Warning,
                    color = MaterialTheme.colorScheme.error,
                    entries = weakPasswords,
                    onEdit = onEditEntry
                )
            }
            
            item {
                AuditSection(
                    title = "Повторяющиеся пароли",
                    count = duplicatePasswords.size,
                    description = "Одинаковый пароль используется в нескольких записях",
                    icon = Icons.Default.ContentCopy,
                    color = MaterialTheme.colorScheme.tertiary,
                    entries = duplicatePasswords,
                    onEdit = onEditEntry
                )
            }
            
            item {
                AuditSection(
                    title = "Устаревшие пароли",
                    count = expiredPasswords.size,
                    description = "Превышен срок ротации",
                    icon = Icons.Default.Schedule,
                    color = MaterialTheme.colorScheme.primary,
                    entries = expiredPasswords,
                    onEdit = onEditEntry
                )
            }
            
            if (totalIssues == 0) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Отлично! Проблем не найдено", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Все пароли соответствуют требованиям безопасности", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditSection(
    title: String,
    count: Int,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    entries: List<Entry>,
    onEdit: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Badge(containerColor = color) { Text("$count") }
            }
            
            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                
                entries.take(5).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onEdit(entry.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.service, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(entry.username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                if (entries.size > 5) {
                    Text("И ещё ${entries.size - 5} записей...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
