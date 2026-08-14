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
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Генератор паролей", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = selectedMode == 0, onClick = { selectedMode = 0 }, label = { Text("Случайный") })
                    FilterChip(selected = selectedMode == 1, onClick = { selectedMode = 1 }, label = { Text("2 части") })
                    FilterChip(selected = selectedMode == 2, onClick = { selectedMode = 2 }, label = { Text("Якорь") })
                    FilterChip(selected = selectedMode == 3, onClick = { selectedMode = 3 }, label = { Text("AMPG") })
                }

                when (selectedMode) {
                    0 -> RandomGeneratorContent(context, onGenerated)
                    1 -> TwoPartGeneratorContent(context, onGenerated)
                    2 -> AnchorGeneratorContent(context, onGenerated, initialServiceName)
                    3 -> AmpgGeneratorContent(context, onGenerated, initialServiceName)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun RandomGeneratorContent(context: android.content.Context, onGenerated: (String, String?, String) -> Unit) {
    var length by remember { mutableIntStateOf(16) }
    var pwd by remember { mutableStateOf("") }
    LaunchedEffect(length) { pwd = PasswordGenerator.generate(length, true, true, true, context).password }
    
    Text(pwd, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    Row {
        Slider(value = length.toFloat(), onValueChange = { length = it.toInt() }, valueRange = 8f..32f, modifier = Modifier.weight(1f))
        Button(onClick = { onGenerated(pwd, null, "random") }) { Text("Выбрать") }
    }
}

@Composable
private fun TwoPartGeneratorContent(context: android.content.Context, onGenerated: (String, String?, String) -> Unit) {
    var length by remember { mutableIntStateOf(16) }
    var pwd by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(length) {
        val res = PasswordGenerator.generateTwoPart(length, true, true, true, context)
        pwd = res?.password ?: ""
        errorMsg = if (res == null) "Не удалось сгенерировать валидный пароль. Попробуйте другую длину." else null
    }
    
    val currentError = errorMsg
    if (currentError != null) Text(currentError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    if (pwd.isNotEmpty()) {
        val half = length / 2
        Text("${pwd.substring(0, half)} / ${pwd.substring(half)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
    Row {
        Slider(value = length.toFloat(), onValueChange = { length = it.toInt() }, valueRange = 16f..20f, steps = 2, modifier = Modifier.weight(1f))
        Button(onClick = { if (pwd.isNotEmpty()) onGenerated(pwd, null, "random_two_part") }, enabled = pwd.isNotEmpty()) { Text("Выбрать") }
    }
}

@Composable
private fun AnchorGeneratorContent(context: android.content.Context, onGenerated: (String, String?, String) -> Unit, initialService: String) {
    var anchor by remember { mutableStateOf("") }
    var length by remember { mutableIntStateOf(16) }
    var pwd by remember { mutableStateOf("") }
    var explanation by remember { mutableStateOf("") }
    var addService by remember { mutableStateOf(false) }
    var addYear by remember { mutableStateOf(false) }
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    
    LaunchedEffect(anchor, length, addService, addYear) {
        val res = PasswordGenerator.generateWithAnchor(anchor, length, true, true, true, context, addService, initialService, addYear, currentYear)
        pwd = res?.password ?: ""
        explanation = res?.explanation ?: ""
    }
    
    OutlinedTextField(value = anchor, onValueChange = { anchor = it }, label = { Text("Якорное слово") })
    Row {
        Checkbox(checked = addService, onCheckedChange = { addService = it })
        Text("Добавить сервис ($initialService) в начало", modifier = Modifier.padding(top = 8.dp))
    }
    Row {
        Checkbox(checked = addYear, onCheckedChange = { addYear = it })
        Text("Добавить год ($currentYear) в конец", modifier = Modifier.padding(top = 8.dp))
    }

    if (pwd.isNotEmpty()) {
        Text(pwd, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(explanation, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (anchor.length >= 3) {
        Text("Не удалось построить пароль с этим якорем. Выберите другое слово.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
    
    Row {
        Slider(value = length.toFloat(), onValueChange = { length = it.toInt() }, valueRange = 12f..32f, modifier = Modifier.weight(1f))
        Button(onClick = { if (pwd.isNotEmpty()) onGenerated(pwd, anchor, "random_anchor") }, enabled = pwd.isNotEmpty()) { Text("Выбрать") }
    }
}

@Composable
private fun AmpgGeneratorContent(context: android.content.Context, onGenerated: (String, String?, String) -> Unit, initialService: String) {
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

    LaunchedEffect(phrase1, phrase2, isTwoUsers, serviceName, year, length, addService, addYear) {
        val y = year.toIntOrNull()
        val opts = MnemonicPasswordGenerator.GenerationOptions(
            phrase = phrase1, phrase2 = if (isTwoUsers) phrase2 else null,
            serviceName = serviceName, year = y, targetLength = length,
            splitMode = if (isTwoUsers) MnemonicPasswordGenerator.SplitMode.TWO_USERS else MnemonicPasswordGenerator.SplitMode.SINGLE_USER,
            addServiceMarker = addService, addYearMarker = addYear
        )
        variants = MnemonicPasswordGenerator.generateVariants(opts, 3)
        selectedIdx = if (variants.isNotEmpty()) 0 else -1
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = isTwoUsers, onCheckedChange = { isTwoUsers = it })
        Text("Два пользователя")
    }
    
    OutlinedTextField(value = phrase1, onValueChange = { phrase1 = it }, label = { Text(if (isTwoUsers) "Фраза 1" else "Фраза") }, modifier = Modifier.fillMaxWidth())
    if (isTwoUsers) OutlinedTextField(value = phrase2, onValueChange = { phrase2 = it }, label = { Text("Фраза 2") }, modifier = Modifier.fillMaxWidth())
    
    Row {
        Checkbox(checked = addService, onCheckedChange = { addService = it })
        Text("Сервис", modifier = Modifier.padding(top = 8.dp))
        Checkbox(checked = addYear, onCheckedChange = { addYear = it })
        Text("Год", modifier = Modifier.padding(top = 8.dp))
    }

    if (variants.isEmpty() && phrase1.length >= 4) {
        Text("Фраза слишком простая или маркеры создают конфликты повторов.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }

    variants.forEachIndexed { index, res ->
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { selectedIdx = index }, colors = CardDefaults.cardColors(if (selectedIdx == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(res.variantName, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                Text(res.password, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(res.explanation, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Row {
        Slider(value = length.toFloat(), onValueChange = { length = it.toInt() }, valueRange = 16f..24f, modifier = Modifier.weight(1f))
        Button(onClick = { if (selectedIdx >= 0) onGenerated(variants[selectedIdx].password, variants[selectedIdx].mnemonicHint, "mnemonic") }, enabled = selectedIdx >= 0) { Text("Выбрать") }
    }
}
