@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.utils.MnemonicPasswordGenerator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicGeneratorScreen(
    onGenerated: (String, String?, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var phrase by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var targetLength by remember { mutableStateOf(12) }
    var includeUppercase by remember { mutableStateOf(true) }
    var includeDigits by remember { mutableStateOf(true) }
    var includeSpecial by remember { mutableStateOf(true) }
    var useRussianReplacement by remember { mutableStateOf(false) }
    var useLatinReplacement by remember { mutableStateOf(false) }
    var enableRotation by remember { mutableStateOf(false) }
    var rotationPeriod by remember { mutableStateOf(6) }
    
    var generatedResult by remember { mutableStateOf<MnemonicPasswordGenerator.GenerationResult?>(null) }
    var showError by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    
    val generator = remember { MnemonicPasswordGenerator() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Генератор по фразе",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                label = { Text("Мнемоническая фраза") },
                placeholder = { Text("например: мой синий автомобиль") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Введите фразу на русском языке для генерации пароля")
                }
            )

            //  Эмодзи поле: вес через Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Эмодзи-подсказка") },
                        placeholder = { Text("например: авто") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                IconButton(
                    onClick = { emoji = "" },
                    enabled = emoji.isNotEmpty()
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Настройки пароля", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Длина: $targetLength", modifier = Modifier.weight(1f))
                        Slider(
                            value = targetLength.toFloat(),
                            onValueChange = { targetLength = it.toInt() },
                            valueRange = 8f..20f,
                            steps = 12,
                            modifier = Modifier.weight(2f)
                        )
                    }
                    
                    SwitchSetting("Заглавные буквы", includeUppercase) { includeUppercase = it }
                    SwitchSetting("Цифры", includeDigits) { includeDigits = it }
                    SwitchSetting("Спецсимволы", includeSpecial) { includeSpecial = it }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    SwitchSetting("Замена русских букв (@, $, 0...)", useRussianReplacement) { 
                        useRussianReplacement = it 
                    }
                    SwitchSetting("Замена латинских букв (5, !, 3...)", useLatinReplacement) { 
                        useLatinReplacement = it 
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    SwitchSetting("Добавить дату ротации", enableRotation) { enableRotation = it }
                    
                    if (enableRotation) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Период:", modifier = Modifier.weight(1f))
                            DropdownPeriodSelector(
                                selected = rotationPeriod,
                                onSelected = { rotationPeriod = it }
                            )
                        }
                    }
                }
            }

            if (generatedResult != null) {
                val result = generatedResult!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = result.password,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                            }
                        }
                        
                        Text(
                            text = "Подсказка: ${result.mnemonicHint}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        
                        if (result.rotationSuffix != null) {
                            Text(
                                text = "Ротация: ${result.rotationSuffix}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        
                        if (!result.isUnique) {
                            Text(
                                text = "Не удалось создать уникальный пароль за ${result.attempts} попыток",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            showError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        showError = null
                        val validation = generator.validatePhrase(phrase)
                        if (validation is MnemonicPasswordGenerator.ValidationResult.Error) {
                            showError = validation.message
                            return@Button
                        }
                        
                        isGenerating = true
                        scope.launch {
                            val options = MnemonicPasswordGenerator.GenerationOptions(
                                phrase = phrase,
                                emoji = emoji.takeIf { it.isNotEmpty() },
                                targetLength = targetLength,
                                includeUppercase = includeUppercase,
                                includeDigits = includeDigits,
                                includeSpecial = includeSpecial,
                                useRussianSymbolReplacement = useRussianReplacement,
                                useLatinSymbolReplacement = useLatinReplacement,
                                enableRotation = enableRotation,
                                rotationPeriodMonths = rotationPeriod,
                                rotationDate = if (enableRotation) System.currentTimeMillis() else null,
                                previousHashes = emptyList()
                            )
                            generatedResult = generator.generate(options)
                            isGenerating = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isGenerating && phrase.isNotBlank()
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Сгенерировать")
                    }
                }
                
                Button(
                    onClick = {
                        generatedResult?.let { result ->
                            onGenerated(result.password, result.emoji, enableRotation)
                        }
                    },
                    enabled = generatedResult != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Использовать")
                }
            }
        }
    }
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DropdownPeriodSelector(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    val periods = listOf(3, 6, 12)
    var expanded by remember { mutableStateOf(false) }
    
    //  Вес через Box
    Box(modifier = Modifier.weight(1f)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = "$selected мес",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                periods.forEach { period ->
                    DropdownMenuItem(
                        text = { Text("$period мес") },
                        onClick = {
                            onSelected(period)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
