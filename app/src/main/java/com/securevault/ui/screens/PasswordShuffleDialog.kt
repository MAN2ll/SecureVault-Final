@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.utils.PasswordValidator
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.VaultViewModel

//  Вынесена функция построения автоматической схемы
private fun buildAutoAssignments(entries: List<Entry>): Map<String, String?> {
    if (entries.size < 2) return entries.associate { it.id to null }

    return entries.mapIndexed { index, entry ->
        val source = entries[(index + 1) % entries.size]
        entry.id to source.id
    }.toMap()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordShuffleDialog(
    entries: List<Entry>,
    onDismiss: () -> Unit,
    onShuffleApplied: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    var assignments by remember {
        mutableStateOf<Map<String, String?>>(
            entries.associate { it.id to null }
        )
    }

    //  Используем buildAutoAssignments для начальной схемы
    LaunchedEffect(entries) {
        if (entries.size >= 2 && assignments.values.all { it == null }) {
            assignments = buildAutoAssignments(entries)
        }
    }

    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var isShuffling by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var expandedFor by remember { mutableStateOf<String?>(null) }

    var swapSelectionMode by remember { mutableStateOf<Boolean>(false) }
    var firstSwapTarget by remember { mutableStateOf<String?>(null) }

    fun trySwap(targetId1: String, targetId2: String): Boolean {
        val source1 = assignments[targetId1] ?: return false
        val source2 = assignments[targetId2] ?: return false

        if (source1 == source2) return false

        val newAssignments = assignments.toMutableMap()
        newAssignments[targetId1] = source2
        newAssignments[targetId2] = source1

        if (targetId1 == source2 || targetId2 == source1) return false

        val donors = newAssignments.values.filterNotNull()
        if (donors.size != donors.toSet().size) return false

        if (newAssignments.values.any { it == null }) return false

        assignments = newAssignments
        return true
    }

    val validationErrors = remember(assignments) {
        val errors = mutableMapOf<String, String>()
        val usedSources = mutableSetOf<String>()

        for ((targetId, sourceId) in assignments) {
            if (sourceId == null) {
                errors[targetId] = "Донор не выбран"
                continue
            }

            if (targetId == sourceId) {
                errors[targetId] = "Нельзя выбрать себя"
                continue
            }

            if (sourceId in usedSources) {
                errors[targetId] = "Донор уже назначен другому"
                continue
            }

            usedSources.add(sourceId)
        }

        if (assignments.values.any { it == null }) {
            errors["__general__"] = "Не все получатели имеют донора"
        }

        errors
    }

    val canApply = validationErrors.isEmpty() && assignments.size == entries.size

    val deepValidationErrors = remember(assignments, context) {
        if (!canApply) return@remember emptyList()

        val errors = mutableListOf<String>()

        for ((targetId, sourceId) in assignments) {
            val target = entries.find { it.id == targetId } ?: continue
            val source = entries.find { it.id == sourceId } ?: continue

            try {
                val newPassword = source.password
                val validation = PasswordValidator.validateNewPasswordForEntry(
                    entry = target,
                    newPassword = newPassword,
                    context = context
                )
                if (!validation.isValid) {
                    errors.add("${target.service} ← ${source.service}: ${validation.errorMessage}")
                }
            } catch (e: Exception) {
                errors.add("${target.service}: не удалось расшифровать пароль ${source.service}")
            }
        }

        errors
    }

    AlertDialog(
        onDismissRequest = { if (!isShuffling) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shuffle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Управляемая перекрёстная ротация", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Для каждой записи выберите, от какого сервиса взять пароль.\n" +
                    "Один донор может быть назначен только одному получателю.\n" +
                    "Кнопка swap позволяет поменять местами доноров двух строк.",
                    fontSize = 12.sp
                )

                //  Кнопка "Сбросить схему" перед списком строк
                OutlinedButton(
                    onClick = {
                        assignments = buildAutoAssignments(entries)
                        swapSelectionMode = false
                        firstSwapTarget = null
                        expandedFor = null
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Сбросить схему")
                }

                if (swapSelectionMode && firstSwapTarget != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Выберите вторую строку для swap",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    swapSelectionMode = false
                                    firstSwapTarget = null
                                }
                            ) {
                                Text("Отмена", color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }

                entries.forEach { target ->
                    val selectedSourceId = assignments[target.id]
                    val isExpanded = expandedFor == target.id
                    val rowError = validationErrors[target.id]
                    val isSelectedForSwap = swapSelectionMode && firstSwapTarget == target.id

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isSelectedForSwap -> MaterialTheme.colorScheme.tertiaryContainer
                                rowError != null -> MaterialTheme.colorScheme.errorContainer
                                selectedSourceId != null -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        target.service,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        target.username,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "получает пароль от",
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    val source = entries.find { it.id == selectedSourceId }
                                    Text(
                                        source?.service ?: "Не выбран",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (source != null) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (source != null) {
                                        Text(
                                            source.username,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        if (selectedSourceId == null) {
                                            errorMessage = "Невозможно swap: у строки нет донора"
                                            return@IconButton
                                        }

                                        if (!swapSelectionMode) {
                                            firstSwapTarget = target.id
                                            swapSelectionMode = true
                                            expandedFor = null
                                        } else {
                                            if (firstSwapTarget == target.id) {
                                                swapSelectionMode = false
                                                firstSwapTarget = null
                                            } else {
                                                val success = trySwap(firstSwapTarget!!, target.id)
                                                if (!success) {
                                                    errorMessage = "Невозможно поменять местами: это нарушит правила ротации (self-assignment или повтор донора)"
                                                }
                                                swapSelectionMode = false
                                                firstSwapTarget = null
                                            }
                                        }
                                    },
                                    enabled = selectedSourceId != null
                                ) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = "Поменять местами доноров",
                                        tint = if (isSelectedForSwap)
                                            MaterialTheme.colorScheme.tertiary
                                        else if (selectedSourceId != null)
                                            MaterialTheme.colorScheme.secondary
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        expandedFor = if (isExpanded) null else target.id
                                        if (expandedFor != null) {
                                            swapSelectionMode = false
                                            firstSwapTarget = null
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Изменить донора",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (isExpanded) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))

                                val usedByOthers = assignments
                                    .filter { it.key != target.id && it.value != null }
                                    .values
                                    .mapNotNull { it }
                                    .toSet()

                                entries.forEach { source ->
                                    val isSelf = source.id == target.id
                                    val isUsedByOther = source.id in usedByOthers
                                    val isDisabled = isSelf || isUsedByOther

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = source.service,
                                            modifier = Modifier.weight(1f),
                                            fontSize = 12.sp,
                                            color = if (isDisabled)
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            else MaterialTheme.colorScheme.onSurface
                                        )

                                        if (isSelf) {
                                            Text(
                                                "нельзя",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else if (isUsedByOther) {
                                            Text(
                                                "занят",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (!isDisabled) {
                                                    assignments = assignments.toMutableMap().apply {
                                                        put(target.id, source.id)
                                                    }
                                                    expandedFor = null
                                                }
                                            },
                                            enabled = !isDisabled
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Выбрать",
                                                tint = if (isDisabled)
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            if (rowError != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    rowError,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                val generalError = validationErrors["__general__"]
                if (generalError != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(generalError, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                if (deepValidationErrors.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Ошибки валидации", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Spacer(Modifier.height(4.dp))
                            deepValidationErrors.take(5).forEach { error ->
                                Text("• $error", fontSize = 10.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            if (deepValidationErrors.size > 5) {
                                Text("... и ещё ${deepValidationErrors.size - 5}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onErrorContainer)
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
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Старые пароли будут сохранены в истории. Проверяется уникальность и отличие ≥60%.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { showMasterPasswordDialog = true },
                enabled = !isShuffling && canApply && deepValidationErrors.isEmpty()
            ) {
                if (isShuffling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Применить ротацию")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isShuffling
            ) {
                Text("Отмена")
            }
        }
    )

    if (showMasterPasswordDialog) {
        MasterPasswordConfirmDialog(
            title = "Подтверждение перекрёстной ротации",
            onConfirmed = {
                showMasterPasswordDialog = false
                isShuffling = true

                viewModel.applyManagedShuffle(assignments) { result ->
                    isShuffling = false
                    when (result) {
                        is PasswordOperationResult.Success -> {
                            onShuffleApplied()
                        }
                        is PasswordOperationResult.Error -> {
                            errorMessage = result.message
                        }
                    }
                }
            },
            onDismiss = { showMasterPasswordDialog = false }
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ошибка ротации") },
            text = { Text(errorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("Понятно")
                }
            }
        )
    }
}
