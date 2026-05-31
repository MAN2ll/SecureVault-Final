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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicGeneratorScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel() // ✅ Добавляем ViewModel
) {
    var phrase by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") } // ✅ Поле для названия сервиса
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
    var showSuccessDialog by remember { mutableStateOf(false) } // ✅ Диалог успеха
    
    val generator = remember { MnemonicPasswordGenerator() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // ✅ Генерируем пароль при изменении ЛЮБОЙ настройки
    LaunchedEffect(
        phrase, targetLength, includeUppercase, includeDigits, includeSpecial,
        useRussianReplacement, useLatinReplacement, enableRotation, rotationPeriod
    ) {
        if (phrase.isNotEmpty()) {
            isGenerating = true
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
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Генератор по фразе", fontWeight = FontWeight.Bold) },
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
            // Поле названия сервиса
            OutlinedTextField(
                value = serviceName,
                onValueChange = { serviceName = it },
                label = { Text("Название сервиса (например, Google)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Поле ввода фразы
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                label = { Text("Мнемоническая фраза") },
                placeholder = { Text("например: мой синий автомобиль") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Введите фразу на русском языке") }
            )

            // Поле эмодзи
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("Эмодзи-подсказка") },
                    placeholder = { Text("например: 🚗") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { emoji = "" }, enabled = emoji.isNotEmpty()) {
                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                }
            }

            // Настройки
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
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                                OutlinedTextField(
                                    value = "$rotationPeriod мес",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier.menuAnchor().weight(1f)
                                )
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    listOf(3, 6, 12).forEach { period ->
                                        DropdownMenuItem(
                                            text = { Text("$period мес") },
                                            onClick = { rotationPeriod = period; expanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Результат генерации
            if (generatedResult != null) {
                val result = generatedResult!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = result.password,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("password", result.password))
                            }) {
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
                    }
                }
            }

            // Кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (phrase.isNotEmpty()) {
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
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = phrase.isNotEmpty() && !isGenerating
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Сгенерировать")
                }
                
                // ✅ Кнопка "Сохранить" - нормального размера
                Button(
                    onClick = {
                        generatedResult?.let { result ->
                            if (serviceName.isBlank()) {
                                showError = "Введите название сервиса"
                                return@Button
                            }
                            
                            // ✅ СОЗДАЕМ И СОХРАНЯЕМ ЗАПИСЬ
                            val newEntry = Entry.create(
                                service = serviceName,
                                username = "user", // Можно добавить поле для логина
                                password = result.password,
                                profile = Profile.PERSONAL,
                                emojiHint = result.emoji,
                                rotationEnabled = enableRotation,
                                rotationPeriodMonths = rotationPeriod
                            )
                            
                            viewModel.insert(newEntry)
                            showSuccessDialog = true
                        }
                    },
                    enabled = generatedResult != null && serviceName.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Сохранить")
                }
            }
            
            if (showError != null) {
                Text(text = showError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
        
        // ✅ Диалог успеха
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showSuccessDialog = false },
                title = { Text("Успешно!") },
                text = { Text("Пароль сохранен в хранилище.") },
                confirmButton = {
                    TextButton(onClick = { 
                        showSuccessDialog = false
                        onBack() // Возвращаемся назад
                    }) {
                        Text("OK")
                    }
                }
            )
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
