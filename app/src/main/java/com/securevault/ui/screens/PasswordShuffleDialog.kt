@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.securevault.viewmodel.PasswordShuffleAssignment
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordShuffleDialog(
    entries: List<Entry>,
    onDismiss: () -> Unit,
    onShuffleApplied: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    var selectedEntryIds by remember { mutableStateOf(entries.map { it.id }.toSet()) }
    var assignments by remember { mutableStateOf<List<PasswordShuffleAssignment>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentStep by remember { mutableIntStateOf(1) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Перемешивание паролей", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (currentStep) {
                    1 -> {
                        Text("Выберите сервисы для перемешивания (минимум 2):", fontWeight = FontWeight.Medium)
                        
                        entries.forEach { entry ->
                            val isSelected = selectedEntryIds.contains(entry.id)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
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
                                        Text(entry.service, fontWeight = FontWeight.SemiBold)
                                        Text(entry.username, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        
                        if (selectedEntryIds.size < 2) {
                            Text(
                                "Выбрано ${selectedEntryIds.size}. Нужно минимум 2.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    2 -> {
                        if (isProcessing) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (assignments.isEmpty() && errorMessage != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(8.dp))
                                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Text("Схема перемешивания:", fontWeight = FontWeight.Bold)
                            Text(
                                "Каждый сервис отдаёт свой пароль другому сервису.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            assignments.forEach { assignment ->
                                val target = entries.find { it.id == assignment.targetEntryId }
                                val source = entries.find { it.id == assignment.sourceEntryId }
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (assignment.isValid) 
                                            MaterialTheme.colorScheme.surfaceVariant 
                                        else 
                                            MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(source?.service ?: "?", fontWeight = FontWeight.SemiBold)
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            null,
                                            Modifier.padding(horizontal = 8.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(target?.service ?: "?", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Пароли скрыты. После подтверждения каждый сервис получит новый пароль.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
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
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                val result = viewModel.buildPasswordShufflePlan(selectedEntryIds.toList())
                                if (result.success) {
                                    assignments = result.assignments
                                    errorMessage = null
                                    currentStep = 2
                                } else {
                                    errorMessage = result.errorMessage
                                }
                                isProcessing = false
                            }
                        },
                        enabled = selectedEntryIds.size >= 2 && !isProcessing
                    ) {
                        Text("Построить схему")
                    }
                }
                2 -> {
                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                val result = viewModel.applyPasswordShuffle(assignments)
                                if (result.success) {
                                    onShuffleApplied()
                                } else {
                                    errorMessage = result.errorMessage
                                }
                                isProcessing = false
                            }
                        },
                        enabled = assignments.isNotEmpty() && !isProcessing
                    ) {
                        Text("Применить")
                    }
                }
            }
        },
        dismissButton = {
            if (currentStep == 2) {
                TextButton(onClick = { currentStep = 1 }) {
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
