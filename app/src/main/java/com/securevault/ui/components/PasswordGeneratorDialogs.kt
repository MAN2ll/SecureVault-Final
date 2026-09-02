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
                // БЛОК 5: Два ряда кнопок, чтобы не съезжали
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
        // ИСПРАВЛЕНО: нет параметра context, правильное извлечение из Result
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
        // ✅ БЛОК 5: Кнопка занимает всю ширину и отключена, если пароль не создан
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
        // ИСПРАВЛЕНО: правильное извлечение из Result
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

    LaunchedEffect(anchor, length, addService, addYear) {
        if (anchor.isBlank()) {
            generatedPassword = ""
            return@LaunchedEffect
        }
        // ИСПРАВЛЕНО: правильное извлечение из Result
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
            year = 2026
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

@Composable
private fun AmpgGeneratorContent(onGenerated: (String, String?, String) -> Unit, initialService: String) {
    // Здесь код AMPG диалога (он не использует PasswordGenerator.generate, поэтому ошибок там быть не должно, если ты взял его из прошлого ответа)
    // Для краткости оставь тот вариант AmpgGeneratorContent, который я давал ранее, он не содержит ошибок с Context.
    Text("AMPG интерфейс (используй код из предыдущего ответа)")
}
