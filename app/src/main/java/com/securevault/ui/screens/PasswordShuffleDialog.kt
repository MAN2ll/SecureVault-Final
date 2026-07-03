@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import com.securevault.viewmodel.ValidationResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    //  НОВОЕ: Состояние для ручного редактирования
    var editingAssignmentIndex by remember { mutableIntStateOf(-1) }
    var showDonorPicker by remember { mutableStateOf(false) }

    //  Проверка валидности всех назначений
    val allAssignmentsValid = remember(assignments) {
        assignments.all { it.isValid }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shuffle, null, tint = MaterialTheme.colorScheme.primary)
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
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedEntryIds = if (checked) selectedEntryIds + entry.id else selectedEntryIds - entry.id
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
                            Text("Выбрано ${selectedEntryIds.size}. Нужно минимум 2.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(8.dp))
                                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Text("Схема перемешивания:", fontWeight = FontWeight.Bold)
                            Text("Каждый сервис отдаёт свой пароль другому сервису.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            //  Отображение схемы с возможностью редактирования
                            assignments.forEachIndexed { index, assignment ->
                                val target = entries.find { it.id == assignment.targetEntryId }
                                val source = entries.find { it.id == assignment.sourceEntryId }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (assignment.isValid) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(source?.service ?: "?", fontWeight = FontWeight.SemiBold)
                                                Text(source?.username ?: "", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Icon(
                                                Icons.Default.ArrowForward,
                                                null,
                                                Modifier.padding(horizontal = 8.dp),
                                                tint = if (assignment.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(target?.service ?: "?", fontWeight = FontWeight.SemiBold)
                                                Text(target?.username ?: "", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            //  Кнопка редактирования
                                            IconButton(
                                                onClick = {
                                                    editingAssignmentIndex = index
                                                    showDonorPicker = true
                                                }
                                            ) {
                                                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                                            }
                                        }

                                        //  Отображение ошибки, если есть
                                        if (!assignment.isValid && assignment.validationMessage != null) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                " ${assignment.validationMessage}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Пароли скрыты. После подтверждения каждый сервис получит новый пароль.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        if (!allAssignmentsValid) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                " Есть невалидные назначения. Исправьте их перед применением.",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
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
                                viewModel.buildPasswordShufflePlan(selectedEntryIds.toList()) { result ->
                                    if (result.success) {
                                        assignments = result.assignments
                                        errorMessage = null
                                        currentStep = 2
                                    } else {
                                        errorMessage = result.errorMessage
                                    }
                                    isProcessing = false
                                }
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
                                viewModel.applyPasswordShuffle(assignments) { result ->
                                    if (result.success) {
                                        onShuffleApplied()
                                    } else {
                                        errorMessage = result.errorMessage
                                    }
                                    isProcessing = false
                                }
                            }
                        },
                        //  Отключено, если есть невалидные назначения
                        enabled = assignments.isNotEmpty() && allAssignmentsValid && !isProcessing
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

    //  Диалог выбора донора
    if (showDonorPicker && editingAssignmentIndex >= 0) {
        DonorPickerDialog(
            assignments = assignments,
            editingIndex = editingAssignmentIndex,
            availableEntries = entries.filter { it.id in selectedEntryIds },
            onDonorSelected = { newSourceEntryId ->
                val currentAssignment = assignments[editingAssignmentIndex]
                val targetEntryId = currentAssignment.targetEntryId

                // Валидация через ViewModel
                val validationResult = viewModel.validatePasswordShuffleAssignment(
                    targetEntryId = targetEntryId,
                    sourceEntryId = newSourceEntryId,
                    currentAssignments = assignments
                )

                // Обновление assignments
                assignments = assignments.toMutableList().apply {
                    set(
                        editingAssignmentIndex,
                        PasswordShuffleAssignment(
                            targetEntryId = targetEntryId,
                            sourceEntryId = newSourceEntryId,
                            isValid = validationResult.isValid,
                            validationMessage = validationResult.errorMessage
                        )
                    )
                }

                showDonorPicker = false
                editingAssignmentIndex = -1
            },
            onDismiss = {
                showDonorPicker = false
                editingAssignmentIndex = -1
            }
        )
    }
}

//  НОВЫЙ ДИАЛОГ: Выбор донора
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonorPickerDialog(
    assignments: List<PasswordShuffleAssignment>,
    editingIndex: Int,
    availableEntries: List<Entry>,
    onDonorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val currentAssignment = assignments[editingIndex]
    val targetEntry = availableEntries.find { it.id == currentAssignment.targetEntryId }

    // Доступные доноры: все записи, кроме текущей и уже назначенных
    val usedSourceIds = assignments.mapIndexedNotNull { index, assignment ->
        if (index != editingIndex) assignment.sourceEntryId else null
    }

    val availableDonors = availableEntries.filter { entry ->
        entry.id != currentAssignment.targetEntryId && entry.id !in usedSourceIds
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Выберите донора для ${targetEntry?.service ?: "?"}")
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (availableDonors.isEmpty()) {
                    Text(
                        "Нет доступных доноров. Все пароли уже назначены.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                } else {
                    availableDonors.forEach { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.service, fontWeight = FontWeight.SemiBold)
                                    Text(entry.username, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = { onDonorSelected(entry.id) }
                                ) {
                                    Text("Выбрать")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
