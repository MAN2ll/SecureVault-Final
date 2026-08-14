@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.utils.MnemonicPasswordGenerator
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordRotationDialog(
    onDismiss: () -> Unit,
    onGenerated: (String, String?, String) -> Unit,
    initialServiceName: String = ""
) {
    var phrase1 by remember { mutableStateOf("") }
    var phrase2 by remember { mutableStateOf("") }
    var isTwoUsers by remember { mutableStateOf(false) }
    var serviceName by remember { mutableStateOf(initialServiceName) }
    var year by remember { mutableStateOf("") }
    var length by remember { mutableIntStateOf(16) }
    var addService by remember { mutableStateOf(false) }
    var addYear by remember { mutableStateOf(false) }

    var variants by remember { mutableStateOf<List<MnemonicPasswordGenerator.GenerationResult>>(emptyList()) }
    var selectedIdx by remember { mutableIntStateOf(-1) }
    var isWeakPhrase by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(phrase1, phrase2, isTwoUsers, serviceName, year, length, addService, addYear) {
        val y = year.toIntOrNull()
        val opts = MnemonicPasswordGenerator.GenerationOptions(
            phrase = phrase1,
            phrase2 = if (isTwoUsers) phrase2 else null,
            serviceName = serviceName,
            year = y,
            targetLength = length,
            splitMode = if (isTwoUsers) MnemonicPasswordGenerator.SplitMode.TWO_USERS else MnemonicPasswordGenerator.SplitMode.SINGLE_USER,
            addServiceMarker = addService,
            addYearMarker = addYear
        )
        variants = MnemonicPasswordGenerator.generateVariants(opts, 3)
        selectedIdx = if (variants.isNotEmpty()) 0 else -1

        isWeakPhrase = phrase1.lowercase().trim() in setOf(
            "мама мыла раму", "ма мыла раму", "я люблю тебя",
            "мой пароль", "пароль от сайта", "qwerty", "password"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ротация паролей (AMPG)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isTwoUsers, onCheckedChange = { isTwoUsers = it })
                    Text("Два пользователя")
                }

                OutlinedTextField(
                    value = phrase1,
                    onValueChange = { phrase1 = it },
                    label = { Text(if (isTwoUsers) "Фраза 1-й половины" else "Мнемоническая фраза") },
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
                    Checkbox(checked = addService, onCheckedChange = { addService = it })
                    Text("Сервис", fontSize = 12.sp, modifier = Modifier.padding(end = 16.dp))
                    Checkbox(checked = addYear, onCheckedChange = { addYear = it })
                    Text("Год", fontSize = 12.sp)
                }

                if (isWeakPhrase) {
                    Text(
                        "Фраза слишком простая. Добавьте ещё 2–3 личных слова.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                } else if (variants.isEmpty() && phrase1.length >= 4) {
                    Text(
                        if (isTwoUsers) "Фраза слишком простая для двух пользователей." else "Не удалось сгенерировать валидные варианты.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                variants.forEachIndexed { index, res ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = { selectedIdx = index },
                        colors = CardDefaults.cardColors(
                            if (selectedIdx == index) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(res.variantName, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            Text(res.password, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(res.explanation, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedIdx >= 0) {
                        onGenerated(
                            variants[selectedIdx].password,
                            variants[selectedIdx].mnemonicHint,
                            "mnemonic"
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
