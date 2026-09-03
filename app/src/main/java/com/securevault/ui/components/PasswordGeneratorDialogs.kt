@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.components

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
import com.securevault.utils.PasswordGenerator
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedPasswordGeneratorDialog(
    onDismiss: () -> Unit,
    onGenerated: (String, String?, String) -> Unit,
    initialServiceName: String = ""
) {
    var selectedMode by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Генератор паролей", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Два ряда кнопок, чтобы не съезжали на узких экранах
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(selected = selectedMode == 0, onClick = { selectedMode = 0 }, label = { Text("Случайный", modifier = Modifier.weight(1f)) })
                        FilterChip(selected = selectedMode == 1, onClick = { selectedMode = 1 }, label = { Text("2 части", modifier = Modifier.weight(1f)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(selected = selectedMode == 2, onClick = { selectedMode = 2 }, label = { Text("Якорь", modifier = Modifier.weight(1f)) })
                        FilterChip(selected = selectedMode == 3, onClick = { selectedMode = 3 }, label = { Text("AMPG", modifier = Modifier.weight(1f)) })
                    }
                }

                when (selectedMode) {
                    0 -> RandomGeneratorContent(onGenerated)
                    1 -> TwoPartGeneratorContent(onGenerated)
                    2 -> AnchorGeneratorContent(onGenerated, initialServiceName)
                    3 -> AmpgGeneratorContent(onGenerated, initialServiceName)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun RandomGeneratorContent(onGenerated: (String, String?, String) -> Unit) {
    var length by remember { mutableIntStateOf(16) }
    var useLower by remember { mutableStateOf(true) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSpecial by remember { mutableStateOf(true) }
    
    var generatedPassword by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(length, useLower, useUpper, useDigits, useSpecial) {
        val result = PasswordGenerator.generate(
            length = length,
            useLower = useLower,
            useUpper = useUpper,
            useDigits = useDigits,
            useSpecials = useSpecial
        )
        result.onSuccess { res ->
            generatedPassword = res.password
            errorMsg = null
        }.onFailure { err ->
            generatedPassword = ""
            errorMsg = err.message ?: "Ошибка генерации"
        }
    }

    if (errorMsg != null) {
        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }

    Text(generatedPassword, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Длина: $length", modifier = Modifier.weight(1f))
            Slider(value = length.toFloat(), onValueChange = { length = it.toInt() }, valueRange = 8f..32f, modifier = Modifier.weight(2f))
        }
        Button(onClick = { onGenerated(generatedPassword, null, "random") }, enabled = generatedPassword.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Выбрать")
        }
    }
}

@Composable
private fun TwoPartGeneratorContent(onGenerated: (String, String?, String) -> Unit) {
    val allowedLengths = listOf(16, 18, 20)
    var lengthIndex by remember { mutableIntStateOf(0) }
    var length by remember { mutableIntStateOf(16) }
    
    var generatedPassword by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(length) {
        val result = PasswordGenerator.generateTwoPart(
            length = length,
            useLower = true,
            useUpper = true,
            useDigits = true,
            useSpecials = true
        )
        result.onSuccess { res ->
            generatedPassword = res.password
            errorMsg = null
        }.onFailure { err ->
            generatedPassword = ""
            errorMsg = err.message ?: "Ошибка генерации"
        }
    }

    if (errorMsg != null) {
        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }

    if (generatedPassword.isNotEmpty()) {
        val half = length / 2
        Text("${generatedPassword.substring(0, half)} / ${generatedPassword.substring(half)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        allowedLengths.forEachIndexed { index, len ->
            FilterChip(selected = lengthIndex == index, onClick = { lengthIndex = index; length = len }, label = { Text("$len", modifier = Modifier.weight(1f)) })
        }
    }

    Button(onClick = { onGenerated(generatedPassword, null, "random_two_part") }, enabled = generatedPassword.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        Text("Выбрать")
    }
}

@Composable
private fun AnchorGeneratorContent(onGenerated: (String, String?, String) -> Unit, initialService: String) {
    var anchor by remember { mutableStateOf("") }
    var length by remember { mutableIntStateOf(16) }
    var addService by remember { mutableStateOf(false) }
    var addYear by remember { mutableStateOf(false) }
    
    var generatedPassword by remember { mutableStateOf("") }
    var explanation by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    LaunchedEffect(anchor, length, addService, addYear) {
        if (anchor.isBlank()) {
            generatedPassword = ""
            return@LaunchedEffect
        }
        val result = PasswordGenerator.generateWithAnchor(
            anchorWord = anchor,
            totalLength = length,
            useLower = true,
            useUpper = true,
            useDigits = true,
            useSpecials = true,
            addService = addService,
            serviceName = initialService,
            addYear = addYear,
            year = currentYear
        )
        result.onSuccess { res ->
            generatedPassword = res.password
            explanation = res.explanation
            errorMsg = null
        }.onFailure { err ->
            generatedPassword = ""
            explanation = ""
            errorMsg = err.message ?: "Ошибка генерации"
        }
    }

    if (errorMsg != null) {
        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }

    OutlinedTextField(value = anchor, onValueChange = { anchor = it }, label = { Text("Якорное слово") }, modifier = Modifier.fillMaxWidth())
    
    if (generatedPassword.isNotEmpty()) {
        Text(generatedPassword, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(explanation, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(value = length.toFloat(), onValueChange = { length = it.toInt() }, valueRange = 12f..32f, modifier = Modifier.weight(1f))
        Button(onClick = { onGenerated(generatedPassword, anchor, "random_anchor") }, enabled = generatedPassword.isNotEmpty()) {
            Text("Выбрать")
        }
    }
}

//  ПОЛНОСТЬЮ РАБОЧИЙ AMPG ГЕНЕРАТОР (БЕЗ ЗАГЛУШЕК)
@Composable
private fun AmpgGeneratorContent(onGenerated: (String, String?, String) -> Unit, initialService: String) {
    var phrase1 by remember { mutableStateOf("") }
    var phrase2 by remember { mutableStateOf("") }
    var isTwoUsers by remember { mutableStateOf(false) }
    var serviceName by remember { mutableStateOf(initialService) }
    var year by remember { mutableStateOf("") }
    var length by remember { mutableIntStateOf(16) }
    var addService by remember { mutableStateOf(false) }
    var addYear by remember { mutableStateOf(false) }

    var variants by remember { mutableStateOf<List<MnemonicPasswordGenerator.GenerationResult>>(emptyList()) }
    var selectedIdx by remember { mutableIntStateOf(-1) }
    var isWeakPhrase by remember { mutableStateOf(false) }
    var serviceError by remember { mutableStateOf<String?>(null) }
    var yearError by remember { mutableStateOf<String?>(null) }

    val weakPhrasesSet = setOf(
        "мама мыла раму", "ма мыла раму", "я люблю тебя",
        "мой пароль", "пароль от сайта", "qwerty", "password"
    )

    LaunchedEffect(phrase1, phrase2, isTwoUsers, serviceName, year, length, addService, addYear) {
        serviceError = null
        yearError = null
        
        val y = year.toIntOrNull()
        
        val isYearInvalid = y != null && run {
            val yStr = y.toString().takeLast(2)
            yStr.length == 2 && yStr[0] == yStr[1]
        }
        
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
            yearError = "Год $y нельзя добавить: цифры повторяются."
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
            year = y,
            targetLength = length,
            splitMode = if (isTwoUsers) MnemonicPasswordGenerator.SplitMode.TWO_USERS else MnemonicPasswordGenerator.SplitMode.SINGLE_USER,
            addServiceMarker = addService,
            addYearMarker = addYear
        )
        
        variants = MnemonicPasswordGenerator.generateVariants(opts, 3)
        selectedIdx = if (variants.isNotEmpty()) 0 else -1

        isWeakPhrase = phrase1.lowercase().trim() in weakPhrasesSet ||
                (isTwoUsers && (phrase2.lowercase().trim() in weakPhrasesSet))
        
        if (addService && serviceName.isNotEmpty()) {
            if (variants.isEmpty() && !isWeakPhrase) {
                serviceError = "Сервис нельзя добавить: все подходящие символы уже используются в пароле."
            }
        }
        
        if (addYear && y != null && variants.isEmpty() && !isWeakPhrase && yearError == null) {
            yearError = "Год нельзя добавить: цифры уже используются в пароле."
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        OutlinedTextField(
            value = serviceName,
            onValueChange = { serviceName = it },
            label = { Text("Сервис (для маркера)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = year,
            onValueChange = { year = it },
            label = { Text("Год (для маркера, например 2026)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = addService, onCheckedChange = { addService = it }, enabled = serviceName.isNotEmpty())
            Column {
                Text("Добавить сервис в начало", fontSize = 12.sp)
                if (serviceError != null) Text(serviceError!!, fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.width(16.dp))

            Checkbox(checked = addYear, onCheckedChange = { addYear = it }, enabled = year.length == 4)
            Column {
                Text("Добавить год в конец", fontSize = 12.sp)
                if (yearError != null) Text(yearError!!, fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            }
        }

        if (isWeakPhrase) {
            Text(
                if (isTwoUsers) "Фраза слишком простая для двух пользователей. Добавьте слова в каждую часть."
                else "Фраза слишком простая. Добавьте ещё 2–3 личных слова.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        } else if (variants.isEmpty() && phrase1.length >= 4 && yearError == null && serviceError == null) {
            Text(
                if (isTwoUsers) "Фраза слишком простая для двух пользователей. Добавьте слова в каждую часть."
                else "Не удалось сгенерировать валидные варианты. Попробуйте другую фразу или длину.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }

        variants.forEachIndexed { index, res ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                onClick = { selectedIdx = index },
                colors = CardDefaults.cardColors(
                    if (selectedIdx == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(res.variantName, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    Text(res.password, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (isTwoUsers && res.part1 != null && res.part2 != null) {
                        Text("${res.part1} / ${res.part2}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(res.explanation, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = length.toFloat(),
                onValueChange = { length = it.toInt() },
                valueRange = 16f..24f,
                modifier = Modifier.weight(1f)
            )
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
                Text("Выбрать")
            }
        }
    }
}
