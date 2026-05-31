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
    viewModel: VaultViewModel = hiltViewModel()
) {
    var phrase by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    
    // Настройки пароля
    var targetLength by remember { mutableStateOf(12) }
    var useLeetSpeak by remember { mutableStateOf(false) } // Замена a->@, e->3
    var addDigits by remember { mutableStateOf(true) }
    var addSpecial by remember { mutableStateOf(false) }
    var enableRotation by remember { mutableStateOf(false) }
    var rotationPeriod by remember { mutableStateOf(6) }
    
    var generatedResult by remember { mutableStateOf<MnemonicPasswordGenerator.GenerationResult?>(null) }
    var showError by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    
    val generator = remember { MnemonicPasswordGenerator() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Автогенерация при изменении параметров
    LaunchedEffect(
        phrase, targetLength, useLeetSpeak, addDigits, addSpecial, enableRotation, rotationPeriod
    ) {
        if (phrase.isNotEmpty()) {
            isGenerating = true
            val options = MnemonicPasswordGenerator.GenerationOptions(
                phrase = phrase,
                emoji = emoji.takeIf { it.isNotEmpty() },
                targetLength = targetLength,
                includeUppercase = true, // Всегда делаем первую букву заглавной для красоты
                includeDigits = addDigits,
                includeSpecial = addSpecial,
                useLeetSpeak = useLeetSpeak,
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
            // Название сервиса
            OutlinedTextField(
                value = serviceName,
                onValueChange = { serviceName = it },
                label = { Text("Название сервиса (например, Google)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Фраза
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                label = { Text("Фраза для запоминания") },
                placeholder = { Text("мой синий автомобиль") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Пароль будет создан на основе этой фразы") }
            )

            // Эмодзи
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("Эмодзи-подсказка") },
                    placeholder = { Text("🚗") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { emoji = "" }, enabled = emoji.isNotEmpty()) {
                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                }
            }

            // Карточка настроек
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Настройки сложности", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    
                    // Длина
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
                    
                    // Чекбоксы вместо сложных свитчей для простоты
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = addDigits, onCheckedChange = { addDigits = it })
                        Text("Добавить цифру", modifier = Modifier.padding(start = 8.dp))
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = useLeetSpeak, onCheckedChange = { useLeetSpeak = it })
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("Заменить похожие буквы")
                            Text("a→@, e→3, o→0 (легче запомнить)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = addSpecial, onCheckedChange = { addSpecial = it })
                        Text("Добавить спецсимвол (!@#...)", modifier = Modifier.padding(start = 8.dp))
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = enableRotation, onCheckedChange = { enableRotation = it })
                        Text("Добавить дату ротации", modifier = Modifier.padding(start = 8.dp))
                    }
                    
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

            // Результат
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
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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
                                includeUppercase = true,
                                includeDigits = addDigits,
                                includeSpecial = addSpecial,
                                useLeetSpeak = useLeetSpeak,
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
                    Text("Обновить")
                }
                
                Button(
                    onClick = {
                        generatedResult?.let { result ->
                            if (serviceName.isBlank()) {
                                showError = "Введите название сервиса"
                                return@Button
                            }
                            
                            val newEntry = Entry.create(
                                service = serviceName,
                                username = "user",
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
        
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showSuccessDialog = false },
                title = { Text("Успешно!") },
                text = { Text("Пароль сохранен.") },
                confirmButton = {
                    TextButton(onClick = { 
                        showSuccessDialog = false
                        onBack()
                    }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
