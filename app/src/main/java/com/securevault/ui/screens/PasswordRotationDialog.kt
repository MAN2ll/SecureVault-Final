@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordRotationDialog(
    currentEntryId: String,
    serviceName: String,
    currentHint: String?,
    generationType: String,
    rotationMonth: Int?,
    rotationYear: Int?,
    allProfileEntries: List<Entry>,
    onPasswordReplaced: (
        newPassword: String,
        newHint: String?,
        newGenerationType: String,
        newMnemonicPhraseHint: String?,
        newMnemonicOptionsJson: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var phrase1 by remember { mutableStateOf(currentHint ?: "") }
    var phrase2 by remember { mutableStateOf("") }
    var isTwoUsers by remember { mutableStateOf(false) }
    var length by remember { mutableIntStateOf(16) }
    var addService by remember { mutableStateOf(false) }
    var addYear by remember { mutableStateOf(false) }

    var variants by remember {
        mutableStateOf<List<MnemonicPasswordGenerator.GenerationResult>>(emptyList())
    }
    var selectedIdx by remember { mutableIntStateOf(-1) }
    var isWeakPhrase by remember { mutableStateOf(false) }
    var yearError by remember { mutableStateOf<String?>(null) }

    val weakPhrasesSet = setOf(
        "мама мыла раму", "ма мыла раму", "я люблю тебя",
        "мой пароль", "пароль от сайта", "qwerty", "password"
    )

    LaunchedEffect(phrase1, phrase2, isTwoUsers, serviceName, length, addService, addYear) {
        yearError = null
        
        // Проверка года на повторяющиеся цифры
        val isYearInvalid = rotationYear != null && run {
            val yStr = rotationYear.toString().takeLast(2)
            yStr.length == 2 && yStr[0] == yStr[1]
        }
        
        // Проверка: помещается ли год в выбранную длину
        val isYearTooLong = if (isTwoUsers) {
            val part2Len = length / 2
            val part2Overhead = 4 // #5 (2) + year (2)
            val part2BaseLen = part2Len - part2Overhead
            addYear && part2BaseLen < 5
        } else {
            val reserveLen = 2
            val yearOverhead = if (addYear) 2 else 0
            val serviceOverhead = if (addService && serviceName.isNotEmpty()) 1 else 0
            val baseLength = length - reserveLen - yearOverhead - serviceOverhead
            addYear && baseLength < 4
        }
        
        if (addYear && isYearInvalid) {
            yearError = "Год $rotationYear нельзя добавить: цифры повторяются."
            variants = emptyList()
            selectedIdx = -1
            return@LaunchedEffect
        }
        
        if (addYear && isYearTooLong) {
            yearError = "Год не помещается в выбранную длину. Выберите длину 18 или отключите год."
            variants = emptyList()
            selectedIdx = -1
            return@LaunchedEffect
        }
        
        val opts = MnemonicPasswordGenerator.GenerationOptions(
            phrase = phrase1,
            phrase2 = if (isTwoUsers) phrase2 else null,
            serviceName = serviceName,
            year = rotationYear,
            targetLength = length,
            splitMode = if (isTwoUsers) {
                MnemonicPasswordGenerator.SplitMode.TWO_USERS
            } else {
                MnemonicPasswordGenerator.SplitMode.SINGLE_USER
            },
            addServiceMarker = addService,
            addYearMarker = addYear
        )
        variants = MnemonicPasswordGenerator.generateVariants(opts, 3)
        selectedIdx = if (variants.isNotEmpty()) 0 else -1

        isWeakPhrase = phrase1.lowercase().trim() in weakPhrasesSet ||
                (isTwoUsers && (phrase2.lowercase().trim() in weakPhrasesSet))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ротация пароля (AMPG)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isTwoUsers,
                        onCheckedChange = { isTwoUsers = it }
                    )
                    Text("Два пользователя")
                }

                OutlinedTextField(
                    value = phrase1,
                    onValueChange = { phrase1 = it },
                    label = {
                        Text(
                            if (isTwoUsers) "Фраза 1-й половины" else "Мнемоническая фраза"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isTwoUsers) {
                    OutlinedTextField(
                        value = phrase2,
                        onValueChange = { phrase2 = it },
                        label = { Text("Фраза 2-й половины") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = addService,
                        onCheckedChange = { addService = it },
                        enabled = serviceName.isNotEmpty()
                    )
                    Text(
                        "Добавить сервис ($serviceName) в начало",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 16.dp)
                    )

                    Checkbox(
                        checked = addYear,
                        onCheckedChange = { addYear = it },
                        enabled = rotationYear != null
                    )
                    Text(
                        "Добавить год (${rotationYear ?: "—"}) в конец",
                        fontSize = 12.sp
                    )
                }

                if (yearError != null) {
                    Text(
                        yearError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isWeakPhrase && yearError == null) {
                    Text(
                        if (isTwoUsers) {
                            "Фраза слишком простая для двух пользователей. Добавьте слова в каждую часть."
                        } else {
                            "Фраза слишком простая. Добавьте ещё 2–3 личных слова."
                        },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else if (variants.isEmpty() && phrase1.length >= 4 && yearError == null && !isWeakPhrase) {
                    Text(
                        if (isTwoUsers) {
                            "Фраза слишком простая для двух пользователей. Добавьте слова в каждую часть."
                        } else {
                            "Не удалось сгенерировать валидные варианты. Попробуйте другую фразу или длину."
                        },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                variants.forEachIndexed { index, res ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { selectedIdx = index },
                        colors = CardDefaults.cardColors(
                            if (selectedIdx == index) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                res.variantName,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                res.password,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (isTwoUsers && res.part1 != null && res.part2 != null) {
                                Text(
                                    "${res.part1} / ${res.part2}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                res.explanation,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedIdx >= 0) {
                        val res = variants[selectedIdx]
                        onPasswordReplaced(
                            res.password,
                            res.mnemonicHint,
                            "mnemonic",
                            res.mnemonicHint,
                            """{"targetLength":$length,"algorithmName":"AMPG v2"}"""
                        )
                    }
                },
                enabled = selectedIdx >= 0
            ) {
                Text("Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
