@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.securevault.ui.components.PasswordStrengthIndicator
import com.securevault.utils.PasswordGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    onGenerated: (String) -> Unit,  // Callback: вернуть сгенерированный пароль
    onBack: () -> Unit
) {
    var length by remember { mutableStateOf(16) }
    var useUppercase by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSpecial by remember { mutableStateOf(false) }
    var excludeSimilar by remember { mutableStateOf(true) }
    var mnemonicMode by remember { mutableStateOf(false) }
    var mnemonicWordCount by remember { mutableStateOf(4) }
    
    var generatedResult by remember { mutableStateOf<PasswordGenerator.GenerationResult?>(null) }
    var showCopiedToast by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // Генерация при изменении настроек
    LaunchedEffect(length, useUppercase, useDigits, useSpecial, excludeSimilar, mnemonicMode, mnemonicWordCount) {
        val options = PasswordGenerator.GeneratorOptions(
            length = length,
            useUppercase = useUppercase,
            useDigits = useDigits,
            useSpecial = useSpecial,
            excludeSimilar = excludeSimilar,
            mnemonicMode = mnemonicMode,
            mnemonicWordCount = mnemonicWordCount
        )
        generatedResult = PasswordGenerator.generate(options)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(" Генератор", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
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
            //  Поле с паролем
            generatedResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = result.password,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                // Копировать в буфер
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("password", result.password))
                                showCopiedToast = true
                                // Автоочистка буфера через 30 сек (в реальном приложении — через Coroutine)
                            }) {
                                Icon(Icons.Default.ContentCopy, "Копировать")
                            }
                        }
                        
                        // Подсказка для мнемоники
                        result.mnemonicHint?.let { hint ->
                            Text(
                                text = " Запомни: $hint",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        // Индикатор сложности
                        PasswordStrengthIndicator(
                            strength = result.strength,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
            
            //  Настройки
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Настройки", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    
                    // Длина
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Длина: $length", modifier = Modifier.weight(1f))
                        Slider(
                            value = length.toFloat(),
                            onValueChange = { length = it.toInt() },
                            valueRange = 8f..64f,
                            steps = 56,
                            modifier = Modifier.weight(2f)
                        )
                    }
                    
                    // Чекбоксы
                    SwitchSetting("Заглавные буквы", useUppercase) { useUppercase = it }
                    SwitchSetting("Цифры", useDigits) { useDigits = it }
                    SwitchSetting("Спецсимволы (!@#...)", useSpecial) { useSpecial = it }
                    SwitchSetting("Исключить 0/O, 1/l", excludeSimilar) { excludeSimilar = it }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    //  Режим мнемоники
                    SwitchSetting(" Запоминаемый пароль (слова)", mnemonicMode) { mnemonicMode = it }
                    
                    if (mnemonicMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Слов: $mnemonicWordCount", modifier = Modifier.weight(1f))
                            Slider(
                                value = mnemonicWordCount.toFloat(),
                                onValueChange = { mnemonicWordCount = it.toInt() },
                                valueRange = 3f..5f,
                                steps = 2,
                                modifier = Modifier.weight(2f)
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
                        val options = PasswordGenerator.GeneratorOptions(
                            length = length,
                            useUppercase = useUppercase,
                            useDigits = useDigits,
                            useSpecial = useSpecial,
                            excludeSimilar = excludeSimilar,
                            mnemonicMode = mnemonicMode,
                            mnemonicWordCount = mnemonicWordCount
                        )
                        generatedResult = PasswordGenerator.generate(options)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Обновить")
                }
                
                Button(
                    onClick = { generatedResult?.password?.let(onGenerated) },
                    enabled = generatedResult != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Использовать")
                }
            }
        }
        
        //  Всплывающее уведомление о копировании
        if (showCopiedToast) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF323232))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("✓ Скопировано!", color = Color.White, fontSize = 14.sp)
            }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                showCopiedToast = false
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
