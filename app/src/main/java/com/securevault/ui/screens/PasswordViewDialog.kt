@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.components

import androidx.compose.foundation.clickable
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
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(selected = selectedMode == 0, onClick = { selectedMode = 0 }, label = { Text("Случайный", modifier = Modifier.weight(1f)) })
                    FilterChip(selected = selectedMode == 1, onClick = { selectedMode = 1 }, label = { Text("2 части", modifier = Modifier.weight(1f)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(selected = selectedMode == 2, onClick = { selectedMode = 2 }, label = { Text("Якорь", modifier = Modifier.weight(1f)) })
                    FilterChip(selected = selectedMode == 3, onClick = { selectedMode = 3 }, label = { Text("AMPG", modifier = Modifier.weight(1f)) })
                }

                when (selectedMode) {
                    0 -> RandomGeneratorContent(onGenerated)
                    1 -> TwoPartGeneratorContent(onGenerated)
                    2 -> AnchorGeneratorContent(onGenerated, initialServiceName)
                    3 -> AmpgGeneratorContent(onGenerated, initialServiceName) //  ПОЛНОЦЕННЫЙ КОМПОНЕНТ, БЕЗ ЗАГЛУШЕК
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
        val result = PasswordGenerator.generate(length, useLower, useUpper, useDigits, useSpecial)
        result.onSuccess { res -> generatedPassword = res.password; errorMsg = null }
            .onFailure { err -> generatedPassword = ""; errorMsg = err.message ?: "Ошибка" }
    }

    if (errorMsg != null) Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    if (generatedPassword.isNotEmpty()) Text(generatedPassword, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Длина: $length", modifier = Modifier.weight(1f))
            Slider(value = length.toFloat(), onValueChange = { length = it.toInt() }, valueRange = 8f..32f, modifier = Modifier.weight(2f))
        }
        Button(onClick = { onGenerated(generatedPassword, null, "random") }, enabled = generatedPassword.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Выбрать") }
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
        val result = PasswordGenerator.generateTwoPart(length, true, true, true, true)
        result.onSuccess { res -> generatedPassword = res.password; errorMsg = null }
            .onFailure { err -> generatedPassword = ""; errorMsg = err.message ?: "Ошибка" }
    }

    if (errorMsg != null) Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    if (generatedPassword.isNotEmpty()) {
        val half = length / 2
        Text("${generatedPassword.substring(0, half)} / ${generatedPassword.substring(half)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        allowedLengths.forEachIndexed { index, len ->
            FilterChip(selected = lengthIndex == index, onClick = { lengthIndex = index; length = len }, label = { Text("$len", modifier = Modifier.weight(1f)) })
        }
    }
    Button(onClick = { onGenerated(generatedPassword, null, "random_two_part") }, enabled = generatedPassword.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Выбрать") }
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

    LaunchedEffect(anchor, length, addService, addYear) {
        if (anchor.isBlank()) { generatedPassword = ""; return@LaunchedEffect }
        val result = PasswordGenerator.generateWithAnchor(anchor, length, true, true, true, true, addService, initialService, addYear, 2026)
        result.onSuccess { res -> generatedPassword = res.password; explanation = res.explanation; errorMsg = null }
            .onFailure { err -> generatedPassword = ""; explanation = ""; errorMsg = err.message ?: "Ошибка" }
    }

    if (errorMsg != null) Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    OutlinedTextField(value = anchor, onValueChange = { anchor = it }, label = { Text("Якорное слово") }, modifier = Modifier.fillMaxWidth())
    
    if (generatedPassword.isNotEmpty()) {
        Text(generatedPassword, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(explanation, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(value = length.toFloat(), onValueChange = { length = it.toInt() }, valueRange = 12f..32f, modifier = Modifier.weight(1f))
        Button(onClick = { onGenerated(generatedPassword, anchor, "random_anchor") }, enabled = generatedPassword.isNotEmpty()) { Text("Выбрать") }
    }
}

//  ПОЛНОЦЕННЫЙ КОМПОНЕНТ AMPG (БЕЗ ЗАГЛУШЕК, С ДИНАМИЧЕСКИМ ПОДСЧЁТОМ ВАРИАНТОВ)
@Composable
private fun AmpgGeneratorContent(onGenerated: (String, String?, String) -> Unit, initialService: String) {
    var phrase1 by remember { mutableStateOf("") }
    var phrase2 by remember { mutableStateOf("") }
    var splitMode by remember { mutableStateOf(MnemonicPasswordGenerator.SplitMode.SINGLE_USER) }
    var targetLength by remember { mutableIntStateOf(16) }
    
    var variants by remember { mutableStateOf<List<MnemonicPasswordGenerator.GenerationResult>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    fun generate() {
        if (phrase1.isBlank()) { errorMsg = "Введите мнемоническую фразу"; return }
        if (splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS && phrase2.isBlank()) {
            errorMsg = "Введите вторую фразу для режима TWO_USERS"; return
        }

        isGenerating = true
        errorMsg = null
        
        val options = MnemonicPasswordGenerator.GenerationOptions(
            phrase = phrase1,
            phrase2 = if (splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS) phrase2 else null,
            serviceName = initialService,
            targetLength = targetLength,
            splitMode = splitMode,
            addServiceMarker = true,
            addYearMarker = true,
            year = 2026
        )

        val results = MnemonicPasswordGenerator.generateVariants(options, count = 3)
        
        if (results.isEmpty()) {
            errorMsg = "Не удалось сгенерировать варианты. Попробуйте изменить фразу (сделайте её длиннее) или увеличить длину пароля."
            variants = emptyList()
        } else {
            variants = results
        }
        isGenerating = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = phrase1, onValueChange = { phrase1 = it; errorMsg = null },
            label = { Text("Мнемоническая фраза 1") },
            placeholder = { Text("например: это был обычный август") },
            modifier = Modifier.fillMaxWidth()
        )

        if (splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS) {
            OutlinedTextField(
                value = phrase2, onValueChange = { phrase2 = it; errorMsg = null },
                label = { Text("Мнемоническая фраза 2") },
                placeholder = { Text("например: для второго пользователя") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Режим:", modifier = Modifier.weight(1f))
            Row {
                RadioButton(selected = splitMode == MnemonicPasswordGenerator.SplitMode.SINGLE_USER, onClick = { splitMode = MnemonicPasswordGenerator.SplitMode.SINGLE_USER })
                Text("Один", modifier = Modifier.padding(end = 16.dp))
                RadioButton(selected = splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS, onClick = { splitMode = MnemonicPasswordGenerator.SplitMode.TWO_USERS })
                Text("Два")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Длина: $targetLength", modifier = Modifier.weight(1f))
            Slider(value = targetLength.toFloat(), onValueChange = { targetLength = it.toInt() }, valueRange = 12f..32f, steps = 20)
        }

        Button(onClick = { generate() }, modifier = Modifier.fillMaxWidth(), enabled = !isGenerating) { 
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Сгенерировать варианты") 
        }

        if (errorMsg != null) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
        }

        if (variants.isNotEmpty()) {
            //  ДИНАМИЧЕСКИЙ ПОДСЧЁТ: показывает реальное количество найденных вариантов
            Text("Найдено вариантов: ${variants.size}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            
            variants.forEachIndexed { index, variant ->
                Card(modifier = Modifier.fillMaxWidth().clickable { 
                    onGenerated(variant.password, variant.mnemonicHint, "ampg_v${variant.variantOffset + 1}") 
                }) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(variant.password, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(variant.variantName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(variant.explanation, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
