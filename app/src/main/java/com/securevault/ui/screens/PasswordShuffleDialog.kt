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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.VaultViewModel

data class ShufflePair(
    val target: Entry,
    val source: Entry
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordShuffleDialog(
    entries: List<Entry>,
    onDismiss: () -> Unit,
    onShuffleApplied: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var isShuffling by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Строим схему циклического сдвига
    // Если 2 записи: A <-> B
    // Если 3+: A -> B, B -> C, C -> A
    val shufflePlan = remember(entries) {
        if (entries.size < 2) emptyList()
        else entries.mapIndexed { index, entry ->
            val sourceIndex = (index + 1) % entries.size
            ShufflePair(
                target = entry,
                source = entries[sourceIndex]
            )
        }
    }

    AlertDialog(
        onDismissRequest = if (isShuffling) {} else onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shuffle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Перекрёстная ротация", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Пароли будут заменены по кругу. Каждый сервис получит пароль от следующего.",
                    fontSize = 13.sp
                )

                //  ВИЗУАЛЬНАЯ СХЕМА
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Схема замены:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        
                        shufflePlan.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Слева: Сервис, который меняем
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        pair.target.service,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "Текущий пароль",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Стрелка (двусторонняя если их 2, обычная если больше)
                                Icon(
                                    if (entries.size == 2) Icons.Default.SwapHoriz else Icons.Default.ArrowForward,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                // Справа: Сервис, откуда берём пароль
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text(
                                        pair.source.service,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "Получит пароль отсюда",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            if (pair != shufflePlan.last()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Старые пароли будут сохранены в истории. Если пароль уже был в истории сервиса, замена будет отменена.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { showMasterPasswordDialog = true },
                enabled = !isShuffling && shufflePlan.isNotEmpty()
            ) {
                if (isShuffling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Перемешать")
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

    // Диалог мастер-пароля
    if (showMasterPasswordDialog) {
        MasterPasswordConfirmDialog(
            title = "Подтверждение перекрёстной ротации",
            onConfirmed = {
                showMasterPasswordDialog = false
                isShuffling = true
                viewModel.shufflePasswords(entries) { result ->
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

    // Ошибки
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
