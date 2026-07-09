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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.data.Entry
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.BulkPasswordReplacement
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkRotationDialog(
    entries: List<Entry>,
    onDismiss: () -> Unit,
    onBulkReplace: (List<BulkPasswordReplacement>) -> Unit,
    viewModel: VaultViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    enum class BulkMode { RANDOM, MNEMONIC }

    var selectedMode by remember { mutableStateOf(BulkMode.RANDOM) }
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Параметры для random
    var randomLength by remember { mutableIntStateOf(16) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSpecial by remember { mutableStateOf(true) }

    // Параметры для AMPG
    var mnemonicPhrase by remember { mutableStateOf("") }
    var includeLeet by remember { mutableStateOf(true) }
    var includeServiceCode by remember { mutableStateOf(true) }
    var includeRotationCode by remember { mutableStateOf(true) }

    // Генерация паролей для preview
    val generatedPasswords = remember(entries, selectedMode, randomLength, useUpper, useDigits, useSpecial, mnemonicPhrase, includeLeet, includeServiceCode, includeRotationCode) {
        if (selectedMode == BulkMode.RANDOM) {
            entries.map { entry ->
                val result = PasswordGenerator.generate(randomLength, useUpper, useDigits, useSpecial, context)
                Pair(entry, result.password)
            }
        } else {
            if (mnemonicPhrase.isNotBlank()) {
                entries.mapNotNull { entry ->
                    try {
                        val options = MnemonicPasswordGenerator.GenerationOptions(
                            phrase = mnemonicPhrase,
                            serviceName = entry.service,
                            targetLength = 16,
                            includeLeet = includeLeet,
                            includeServiceCode = includeServiceCode,
                            includeRotationCode = includeRotationCode,
                            variantOffset = 0
                        )
                        val variants = MnemonicPasswordGenerator.generateVariants(options, count = 1)
                        if (variants.isNotEmpty()) {
                            Pair(entry, variants.first().password)
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
            } else {
                emptyList()
            }
        }
    }

    AlertDialog(
        onDismissRequest = if (isProcessing) {} else onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Массовая ротация (${entries.size})", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Выбор режима
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Режим генерации", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedMode == BulkMode.RANDOM, onClick = { selectedMode = BulkMode.RANDOM })
                            Text("Случайные пароли", Modifier.padding(start = 8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedMode == BulkMode.MNEMONIC, onClick = { selectedMode = BulkMode.MNEMONIC })
                            Text("AMPG v2 по общей фразе", Modifier.padding(start = 8.dp))
                        }
                    }
                }

                // Параметры для выбранного режима
                when (selectedMode) {
                    BulkMode.RANDOM -> {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Параметры", fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Длина: $randomLength", modifier = Modifier.weight(1f))
                                    Slider(value = randomLength.toFloat(), onValueChange = { randomLength = it.toInt() }, valueRange = 8f..32f, steps = 24, modifier = Modifier.weight(2f))
                                }
                                Row { Checkbox(checked = useUpper, onCheckedChange = { useUpper = it }); Text("A-Z", Modifier.padding(start = 8.dp)) }
                                Row { Checkbox(checked = useDigits, onCheckedChange = { useDigits = it }); Text("0-9", Modifier.padding(start = 8.dp)) }
                                Row { Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it }); Text("!@#", Modifier.padding(start = 8.dp)) }
                            }
                        }
                    }

                    BulkMode.MNEMONIC -> {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("AMPG v2 — Общая фраза", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                OutlinedTextField(
                                    value = mnemonicPhrase,
                                    onValueChange = { mnemonicPhrase = it },
                                    label = { Text("Мнемоническая фраза") },
                                    placeholder = { Text("например: моя кошка любит рыбу") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row { Checkbox(checked = includeLeet, onCheckedChange = { includeLeet = it }); Text("Leet-замены", Modifier.padding(start = 8.dp)) }
                                Row { Checkbox(checked = includeServiceCode, onCheckedChange = { includeServiceCode = it }); Text("Код сервиса", Modifier.padding(start = 8.dp)) }
                                Row { Checkbox(checked = includeRotationCode, onCheckedChange = { includeRotationCode = it }); Text("Код ротации", Modifier.padding(start = 8.dp)) }
                            }
                        }
                    }
                }

                // Preview сгенерированных паролей
                if (generatedPasswords.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Preview (${generatedPasswords.size} записей):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            generatedPasswords.take(5).forEach { (entry, password) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(entry.service, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Text(
                                        password.take(12) + "...",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (generatedPasswords.size > 5) {
                                Text(
                                    "... и ещё ${generatedPasswords.size - 5} записей",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                            "Все старые пароли будут сохранены в истории. Это действие необратимо.",
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
                enabled = !isProcessing && generatedPasswords.isNotEmpty()
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Заменить все")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("Отмена")
            }
        }
    )

    // Диалог мастер-пароля
    if (showMasterPasswordDialog) {
        MasterPasswordConfirmDialog(
            title = "Подтверждение массовой ротации",
            onConfirmed = {
                showMasterPasswordDialog = false
                isProcessing = true

                val replacements = generatedPasswords.map { (entry, password) ->
                    BulkPasswordReplacement(
                        entryId = entry.id,
                        newPassword = password,
                        generationType = if (selectedMode == BulkMode.RANDOM) "random" else "mnemonic",
                        textHint = if (selectedMode == BulkMode.MNEMONIC) mnemonicPhrase else null,
                        mnemonicPhraseHint = if (selectedMode == BulkMode.MNEMONIC) mnemonicPhrase else null,
                        mnemonicOptionsJson = null
                    )
                }

                onBulkReplace(replacements)
                isProcessing = false
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
