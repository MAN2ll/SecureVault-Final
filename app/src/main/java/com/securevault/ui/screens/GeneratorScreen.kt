@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import com.securevault.ui.components.AnimatedPasswordStrength
import com.securevault.utils.ClipboardHelper
import com.securevault.utils.PasswordGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    onGenerated: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardHelper = remember { ClipboardHelper(context) }
    
    var length by remember { mutableStateOf(16) }
    var useUppercase by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSpecial by remember { mutableStateOf(false) }
    var excludeSimilar by remember { mutableStateOf(true) }
    var mnemonicMode by remember { mutableStateOf(false) }
    var mnemonicWordCount by remember { mutableStateOf(4) }
    
    var generatedResult by remember { mutableStateOf<PasswordGenerator.GenerationResult?>(null) }
    var showCopiedHint by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(length, useUppercase, useDigits, useSpecial, excludeSimilar, mnemonicMode, mnemonicWordCount) {
        isGenerating = true
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
        isGenerating = false
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Генератор паролей",
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
            generatedResult?.let { result ->
                val animatedBorderColor by animateColorAsState(
                    targetValue = when (result.strength) {
                        PasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error
                        PasswordGenerator.Strength.MEDIUM -> MaterialTheme.colorScheme.tertiary
                        PasswordGenerator.Strength.STRONG -> Color(0xFF4CAF50)
                        PasswordGenerator.Strength.VERY_STRONG -> Color(0xFF2E7D32)
                    },
                    label = "borderColorAnimation"
                )
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(animatedBorderColor.copy(alpha = 0.1f)),
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
                            IconButton(
                                onClick = {
                                    clipboardHelper.copy(result.password)
                                    showCopiedHint = true
                                    scope.launch {
                                        delay(2000)
                                        showCopiedHint = false
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                            }
                        }
                        
                        result.mnemonicHint?.let { hint ->
                            Text(
                                text = "Подсказка: $hint",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        AnimatedPasswordStrength(
                            strength = result.strength,
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
                        Text(
                            text = "Скопировано",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Настройки", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    
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
                    
                    SwitchSetting("Заглавные буквы", useUppercase) { useUppercase = it }
                    SwitchSetting("Цифры", useDigits) { useDigits = it }
                    SwitchSetting("Спецсимволы", useSpecial) { useSpecial = it }
                    SwitchSetting("Исключить похожие символы", excludeSimilar) { excludeSimilar = it }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    SwitchSetting("Режим запоминания (слова)", mnemonicMode) { mnemonicMode = it }
                    
                    if (mnemonicMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Количество слов: $mnemonicWordCount", modifier = Modifier.weight(1f))
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
                    modifier = Modifier.weight(1f),
                    enabled = !isGenerating
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Обновить")
                }
                
                Button(
                    onClick = { generatedResult?.password?.let(onGenerated) },
                    enabled = generatedResult != null && !isGenerating,
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
