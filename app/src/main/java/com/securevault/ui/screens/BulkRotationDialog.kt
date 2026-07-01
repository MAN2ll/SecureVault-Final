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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.data.Entry
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.PasswordReplacement

/**
 * Диалог массовой ротации паролей.
 * Шаг 1: Выбор записей
 * Шаг 2: Предпросмотр новых паролей (только случайный режим)
 * Шаг 3: Подтверждение
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkRotationDialog(
    entries: List<Entry>,
    onDismiss: () -> Unit,
    onBulkReplace: (List<PasswordReplacement>) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var selectedEntryIds by remember { mutableStateOf<Set<String>>(entries.map { it.id }.toSet()) }
    var replacements by remember { mutableStateOf<List<PasswordReplacement>>(emptyList()) }
    
    // Генерация случайных паролей для предпросмотра
    LaunchedEffect(selectedEntryIds) {
        if (currentStep == 2) {
            val newReplacements = entries
                .filter { it.id in selectedEntryIds }
                .map { entry ->
                    val result = PasswordGenerator.generate(16, true, true, true)
                    PasswordReplacement(
                        entryId = entry.id,
                        newPassword = result.password,
                        newHint = "Случайный пароль, создан при массовой ротации",
                        generationType = "random"
                    )
                }
            replacements = newReplacements
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Массовая ротация", fontWeight = FontWeight.Bold)
                    Text("Шаг $currentStep из 3", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            when (currentStep) {
                1 -> {
                    // Шаг 1: Выбор записей
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        Text(
                            "Выберите записи для замены (${selectedEntryIds.size} из ${entries.size}):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(entries, key = { it.id }) { entry ->
                                val isSelected = selectedEntryIds.contains(entry.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedEntryIds = if (checked) {
                                                selectedEntryIds + entry.id
                                            } else {
                                                selectedEntryIds - entry.id
                                            }
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            entry.service,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            entry.username,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { 
                                selectedEntryIds = if (selectedEntryIds.size == entries.size) {
                                    emptySet()
                                } else {
                                    entries.map { it.id }.toSet()
                                }
                            }) {
                                Text(if (selectedEntryIds.size == entries.size) "Снять все" else "Выбрать все")
                            }
                        }
                    }
                }
                
                2 -> {
                    // Шаг 2: Предпросмотр новых паролей
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        Text(
                            "Предпросмотр новых паролей:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(replacements, key = { it.entryId }) { replacement ->
                                val entry = entries.find { it.id == replacement.entryId }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            entry?.service ?: "Unknown",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            replacement.newPassword,
                                            fontSize = 14.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Сложность: ${calculateStrength(replacement.newPassword).name}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            IconButton(
                                                onClick = {
                                                    // Перегенерировать для этой записи
                                                    val result = PasswordGenerator.generate(16, true, true, true)
                                                    replacements = replacements.map {
                                                        if (it.entryId == replacement.entryId) {
                                                            it.copy(newPassword = result.password)
                                                        } else {
                                                            it
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                3 -> {
                    // Шаг 3: Подтверждение
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "После подтверждения старые пароли будут заменены. " +
                                    "Убедитесь, что вы сможете обновить их на соответствующих сервисах.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        
                        Text(
                            "Будет заменено ${replacements.size} паролей:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(replacements, key = { it.entryId }) { replacement ->
                                val entry = entries.find { it.id == replacement.entryId }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        entry?.service ?: "Unknown",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (currentStep) {
                1 -> {
                    Button(
                        onClick = { currentStep = 2 },
                        enabled = selectedEntryIds.isNotEmpty()
                    ) {
                        Text("Далее")
                    }
                }
                2 -> {
                    Button(onClick = { currentStep = 3 }) {
                        Text("Далее")
                    }
                }
                3 -> {
                    Button(
                        onClick = { onBulkReplace(replacements) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Применить замену")
                    }
                }
            }
        },
        dismissButton = {
            if (currentStep > 1) {
                TextButton(onClick = { currentStep-- }) {
                    Text("Назад")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(0.95f)
    )
}

private fun calculateStrength(password: String): PasswordGenerator.Strength {
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.length >= 16) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    
    return when {
        score >= 6 -> PasswordGenerator.Strength.VERY_STRONG
        score >= 4 -> PasswordGenerator.Strength.STRONG
        score >= 2 -> PasswordGenerator.Strength.MEDIUM
        else -> PasswordGenerator.Strength.WEAK
    }
}
