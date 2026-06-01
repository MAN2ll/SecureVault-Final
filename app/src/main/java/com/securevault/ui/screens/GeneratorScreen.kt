@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.ui.components.AnimatedPasswordStrength
import com.securevault.utils.ClipboardHelper
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    // Режим генерации: Simple или Mnemonic
    var generationMode by remember { mutableStateOf(GenerationMode.SIMPLE) }
    
    // === НАСТРОЙКИ ПРОСТОГО ГЕНЕРАТОРА ===
    var simpleLength by remember { mutableStateOf(16) }
    var simpleUseUppercase by remember { mutableStateOf(true) }
    var simpleUseDigits by remember { mutableStateOf(true) }
    var simpleUseSpecial by remember { mutableStateOf(true) }
    var simpleExcludeSimilar by remember { mutableStateOf(true) }
    
    // === НАСТРОЙКИ МНЕМОНИЧЕСКОГО ГЕНЕРАТОРА ===
    var mnemonicPhrase by remember { mutableStateOf("") }
    var mnemonicEmoji by remember { mutableStateOf("") }
    var mnemonicLength by remember { mutableStateOf(12) }
    var mnemonicUseLeetSpeak by remember { mutableStateOf(false) }
    var mnemonicAddDigits by remember { mutableStateOf(true) }
    var mnemonicEnableRotation by remember { mutableStateOf(false) }
    var mnemonicRotationPeriod by remember { mutableStateOf(6) }
    
    // Общий результат
    var generatedPassword by remember { mutableStateOf("") }
    var passwordStrength by remember { mutableStateOf(PasswordGenerator.Strength.MEDIUM) }
    var mnemonicHint by remember { mutableStateOf<String?>(null) }
    
    var isGenerating by remember { mutableStateOf(false) }
    var showCopiedHint by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val clipboardHelper = remember { ClipboardHelper(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val mnemonicGenerator = remember { MnemonicPasswordGenerator() }

    // Генерация пароля при изменении настроек
    LaunchedEffect(
        generationMode,
        simpleLength, simpleUseUppercase, simpleUseDigits, simpleUseSpecial, simpleExcludeSimilar,
        mnemonicPhrase, mnemonicLength, mnemonicUseLeetSpeak, mnemonicAddDigits, mnemonicEnableRotation
    ) {
        isGenerating = true
        when (generationMode) {
            GenerationMode.SIMPLE -> {
                val options = PasswordGenerator.GeneratorOptions(
                    length = simpleLength,
                    useUppercase = simpleUseUppercase,
                    useDigits = simpleUseDigits,
                    useSpecial = simpleUseSpecial,
                    excludeSimilar = simpleExcludeSimilar
                )
                val result = PasswordGenerator.generate(options)
                generatedPassword = result.password
                passwordStrength = result.strength
                mnemonicHint = null
            }
            GenerationMode.MNEMONIC -> {
                if (mnemonicPhrase.isNotEmpty()) {
                    val options = MnemonicPasswordGenerator.GenerationOptions(
                        phrase = mnemonicPhrase,
                        emoji = mnemonicEmoji.takeIf { it.isNotEmpty() },
                        targetLength = mnemonicLength,
                        includeUppercase = true,
                        includeDigits = mnemonicAddDigits,
                        includeSpecial = false,
                        useLeetSpeak = mnemonicUseLeetSpeak,
                        enableRotation = mnemonicEnableRotation,
                        rotationPeriodMonths = mnemonicRotationPeriod,
                        rotationDate = if (mnemonicEnableRotation) System.currentTimeMillis() else null,
                        previousHashes = emptyList()
                    )
                    val result = mnemonicGenerator.generate(options)
                    generatedPassword = result.password
                    passwordStrength = estimateStrength(result.password)
                    mnemonicHint = result.mnemonicHint
                }
            }
        }
        isGenerating = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Генератор паролей", fontWeight = FontWeight.Bold) },
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
            // 🔘 Переключатель режима
            SegmentedButton(
                selectedMode = generationMode,
                onModeSelected = { generationMode = it },
                modifier = Modifier.fillMaxWidth()
            )
            
            // 📦 Контент в зависимости от режима
            when (generationMode) {
                GenerationMode.SIMPLE -> SimpleGeneratorSettings(
                    length = simpleLength,
                    onLengthChange = { simpleLength = it },
                    useUppercase = simpleUseUppercase,
                    onUppercaseChange = { simpleUseUppercase = it },
                    useDigits = simpleUseDigits,
                    onDigitsChange = { simpleUseDigits = it },
                    useSpecial = simpleUseSpecial,
                    onSpecialChange = { simpleUseSpecial = it },
                    excludeSimilar = simpleExcludeSimilar,
                    onExcludeSimilarChange = { simpleExcludeSimilar = it }
                )
                GenerationMode.MNEMONIC -> MnemonicGeneratorSettings(
                    phrase = mnemonicPhrase,
                    onPhraseChange = { mnemonicPhrase = it },
                    emoji = mnemonicEmoji,
                    onEmojiChange = { mnemonicEmoji = it },
                    length = mnemonicLength,
                    onLengthChange = { mnemonicLength = it },
                    useLeetSpeak = mnemonicUseLeetSpeak,
                    onLeetSpeakChange = { mnemonicUseLeetSpeak = it },
                    addDigits = mnemonicAddDigits,
                    onAddDigitsChange = { mnemonicAddDigits = it },
                    enableRotation = mnemonicEnableRotation,
                    onEnableRotationChange = { mnemonicEnableRotation = it },
                    rotationPeriod = mnemonicRotationPeriod,
                    onRotationPeriodChange = { mnemonicRotationPeriod = it }
                )
            }
            
            // 🔐 Результат генерации
            if (generatedPassword.isNotEmpty()) {
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
                                text = generatedPassword,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                clipboardHelper.copy(generatedPassword)
                                showCopiedHint = true
                                scope.launch {
                                    delay(2000)
                                    showCopiedHint = false
                                }
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                            }
                        }
                        
                        mnemonicHint?.let { hint ->
                            Text(
                                text = "💡 Подсказка: $hint",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        AnimatedPasswordStrength(
                            strength = passwordStrength,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                
                if (showCopiedHint) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF323232))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(text = "Скопировано", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
            
            if (isGenerating) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            
            // ✅ Кнопки действий
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        // Принудительная перегенерация
                        // (триггерится автоматически, но кнопка для удобства)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isGenerating && (generationMode == GenerationMode.MNEMONIC && mnemonicPhrase.isNotEmpty() || generationMode == GenerationMode.SIMPLE)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Обновить")
                }
                
                Button(
                    onClick = {
                        // Сохранение пароля
                        val newEntry = com.securevault.data.Entry.create(
                            service = "Новый пароль",
                            username = "user",
                            password = generatedPassword,
                            profile = com.securevault.data.Profile.PERSONAL,
                            emojiHint = mnemonicHint,
                            rotationEnabled = mnemonicEnableRotation,
                            rotationPeriodMonths = mnemonicRotationPeriod
                        )
                        viewModel.insert(newEntry)
                        onBack()
                    },
                    enabled = generatedPassword.isNotEmpty() && !isGenerating,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Сохранить")
                }
            }
        }
    }
}

// === ВСПОМОГАТЕЛЬНЫЕ КОМПОНЕНТЫ ===

enum class GenerationMode { SIMPLE, MNEMONIC }

@Composable
private fun SegmentedButton(
    selectedMode: GenerationMode,
    onModeSelected: (GenerationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = { onModeSelected(GenerationMode.SIMPLE) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedMode == GenerationMode.SIMPLE) 
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.weight(1f)
        ) {
            Text("Простой")
        }
        Button(
            onClick = { onModeSelected(GenerationMode.MNEMONIC) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedMode == GenerationMode.MNEMONIC) 
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.weight(1f)
        ) {
            Text("По фразе")
        }
    }
}

@Composable
private fun SimpleGeneratorSettings(
    length: Int, onLengthChange: (Int) -> Unit,
    useUppercase: Boolean, onUppercaseChange: (Boolean) -> Unit,
    useDigits: Boolean, onDigitsChange: (Boolean) -> Unit,
    useSpecial: Boolean, onSpecialChange: (Boolean) -> Unit,
    excludeSimilar: Boolean, onExcludeSimilarChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Настройки", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Длина: $length", modifier = Modifier.weight(1f))
                Slider(value = length.toFloat(), onValueChange = { onLengthChange(it.toInt()) },
                    valueRange = 8f..64f, steps = 56, modifier = Modifier.weight(2f))
            }
            SwitchSetting("Заглавные буквы", useUppercase, onUppercaseChange)
            SwitchSetting("Цифры", useDigits, onDigitsChange)
            SwitchSetting("Спецсимволы", useSpecial, onSpecialChange)
            SwitchSetting("Исключить похожие (0/O, 1/l...)", excludeSimilar, onExcludeSimilarChange)
        }
    }
}

@Composable
private fun MnemonicGeneratorSettings(
    phrase: String, onPhraseChange: (String) -> Unit,
    emoji: String, onEmojiChange: (String) -> Unit,
    length: Int, onLengthChange: (Int) -> Unit,
    useLeetSpeak: Boolean, onLeetSpeakChange: (Boolean) -> Unit,
    addDigits: Boolean, onAddDigitsChange: (Boolean) -> Unit,
    enableRotation: Boolean, onEnableRotationChange: (Boolean) -> Unit,
    rotationPeriod: Int, onRotationPeriodChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = phrase, onValueChange = onPhraseChange,
            label = { Text("Фраза для запоминания") },
            placeholder = { Text("мой синий автомобиль") },
            modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = emoji, onValueChange = onEmojiChange,
                label = { Text("Эмодзи") }, placeholder = { Text("🚗") },
                modifier = Modifier.weight(1f), singleLine = true)
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Доп. настройки", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Длина: $length", modifier = Modifier.weight(1f))
                    Slider(value = length.toFloat(), onValueChange = { onLengthChange(it.toInt()) },
                        valueRange = 8f..20f, steps = 12, modifier = Modifier.weight(2f))
                }
                SwitchSetting("Заменить a→@, e→3, o→0", useLeetSpeak, onLeetSpeakChange)
                SwitchSetting("Добавить цифру", addDigits, onAddDigitsChange)
                SwitchSetting("Добавить дату ротации", enableRotation, onEnableRotationChange)
                if (enableRotation) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(value = "$rotationPeriod мес", onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf(3, 6, 12).forEach { period ->
                                DropdownMenuItem(text = { Text("$period мес") },
                                    onClick = { onRotationPeriodChange(period); expanded = false })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// Оценка надежности пароля (упрощенная)
private fun estimateStrength(password: String): PasswordGenerator.Strength {
    var score = 0
    if (password.length >= 12) score++
    if (password.length >= 16) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when {
        score <= 2 -> PasswordGenerator.Strength.WEAK
        score <= 3 -> PasswordGenerator.Strength.MEDIUM
        score <= 4 -> PasswordGenerator.Strength.STRONG
        else -> PasswordGenerator.Strength.VERY_STRONG
    }
}
