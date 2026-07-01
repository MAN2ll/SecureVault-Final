@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordRotationDialog(
    serviceName: String,
    currentHint: String?,
    generationType: String,
    rotationMonth: Int?,
    rotationYear: Int?,
    onDismiss: () -> Unit,
    onPasswordReplaced: (newPassword: String, newHint: String?, newGenerationType: String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    var selectedMode by remember { mutableIntStateOf(0) }
    
    var randomPassword by remember { mutableStateOf("") }
    var randomStrength by remember { mutableStateOf(PasswordGenerator.Strength.STRONG) }
    
    var mnemonicPhrase by remember { mutableStateOf(currentHint ?: "") }
    var mnemonicVariants by remember { mutableStateOf<List<MnemonicPasswordGenerator.GenerationResult>>(emptyList()) }
    var selectedMnemonicIndex by remember { mutableIntStateOf(-1) }
    var variantOffset by remember { mutableIntStateOf(0) }
    
    var includeLeet by remember { mutableStateOf(true) }
    var includeServiceCode by remember { mutableStateOf(true) }
    var includeRotationCode by remember { mutableStateOf(true) }
    
    var manualPassword by remember { mutableStateOf("") }
    var manualStrength by remember { mutableStateOf(PasswordGenerator.Strength.WEAK) }
    
    var validationError by remember { mutableStateOf<String?>(null) }

    fun generateRandomPassword() {
        val result = PasswordGenerator.generate(16, true, true, true)
        randomPassword = result.password
        randomStrength = result.strength
    }

    fun generateMnemonicVariants() {
        validationError = null
        
        if (mnemonicPhrase.isBlank()) {
            mnemonicVariants = emptyList()
            validationError = "Введите мнемоническую фразу"
            return
        }
        
        if (includeServiceCode && serviceName.isBlank()) {
            mnemonicVariants = emptyList()
            validationError = "Код сервиса включён, но название сервиса пустое"
            return
        }
        
        val options = MnemonicPasswordGenerator.GenerationOptions(
            phrase = mnemonicPhrase,
            serviceName = serviceName,
            rotationMonth = rotationMonth,
            rotationYear = rotationYear,
            targetLength = 16,
            includeLeet = includeLeet,
            includeServiceCode = includeServiceCode,
            includeRotationCode = includeRotationCode,
            variantOffset = variantOffset
        )
        
        mnemonicVariants = MnemonicPasswordGenerator.generateVariants(options, count = 5)
        selectedMnemonicIndex = -1
    }

    LaunchedEffect(Unit) {
        generateRandomPassword()
        if (currentHint != null) {
            mnemonicPhrase = currentHint
            generateMnemonicVariants()
        }
    }

    LaunchedEffect(mnemonicPhrase, includeLeet, includeServiceCode, includeRotationCode, variantOffset) {
        if (selectedMode == 1) {
            generateMnemonicVariants()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Замена пароля", fontWeight = FontWeight.Bold)
                    Text("Сервис: $serviceName", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Выберите способ замены:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == 0,
                        onClick = { selectedMode = 0 },
                        label = { Text("Случайный", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedMode == 1,
                        onClick = { selectedMode = 1 },
                        label = { Text("Мнемонический", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedMode == 2,
                        onClick = { selectedMode = 2 },
                        label = { Text("Вручную", fontSize = 11.sp) }
                    )
                }

                when (selectedMode) {
                    0 -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Новый пароль:", fontWeight = FontWeight.Bold)
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(randomPassword))
                                        android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    randomPassword,
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Сложность: ", fontSize = 12.sp)
                                    val strengthColor: Color = when (randomStrength) {
                                        PasswordGenerator.Strength.VERY_STRONG -> Color(0xFF4CAF50)
                                        PasswordGenerator.Strength.STRONG -> Color(0xFF2196F3)
                                        PasswordGenerator.Strength.MEDIUM -> Color(0xFFFF9800)
                                        PasswordGenerator.Strength.WEAK -> Color(0xFFF44336)
                                    }
                                    Text(
                                        randomStrength.name,
                                        fontWeight = FontWeight.Bold,
                                        color = strengthColor,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        
                        OutlinedButton(
                            onClick = { generateRandomPassword() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Сгенерировать другой")
                        }
                    }
                    
                    1 -> {
                        OutlinedTextField(
                            value = mnemonicPhrase,
                            onValueChange = { mnemonicPhrase = it },
                            label = { Text("Мнемоническая фраза") },
                            placeholder = { Text("например: моя кошка любит рыбу") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Параметры генерации:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = includeLeet, onCheckedChange = { includeLeet = it })
                                    // ✅ ИСПРАВЛЕНО: modifier = Modifier... (именованный параметр!)
                                    Text(
                                        "Leet-замены (a→@, o→0...)",
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = includeServiceCode, onCheckedChange = { includeServiceCode = it })
                                    // ✅ ИСПРАВЛЕНО: modifier = Modifier... (именованный параметр!)
                                    Text(
                                        "Код сервиса",
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = includeRotationCode, onCheckedChange = { includeRotationCode = it })
                                    // ✅ ИСПРАВЛЕНО: modifier = Modifier... (именованный параметр!)
                                    Text(
                                        "Код ротации (MMYY)",
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                        
                        if (currentHint != null && currentHint != mnemonicPhrase) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Старая подсказка: $currentHint",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        
                        OutlinedButton(
                            onClick = { variantOffset++ },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = mnemonicVariants.isNotEmpty() || (mnemonicPhrase.isNotBlank() && (!includeServiceCode || serviceName.isNotBlank()))
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ещё варианты (набор #$variantOffset)")
                        }
                        
                        if (validationError != null) {
                            Text(
                                validationError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                        
                        if (mnemonicVariants.isNotEmpty()) {
                            Text("Выберите вариант:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            
                            mnemonicVariants.forEachIndexed { index, result ->
                                val isSelected = selectedMnemonicIndex == index
                                val cardColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                                
                                // ✅ ЯВНО извлекаем значения в String
                                val vName: String = result.variantName
                                val pwd: String = result.password
                                val strengthStr: String = result.strength.name
                                val hintStr: String = result.mnemonicHint
                                val combined: String = strengthStr + " • " + hintStr
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = cardColor)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = vName,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = pwd,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = combined,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedMnemonicIndex = index }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    2 -> {
                        OutlinedTextField(
                            value = manualPassword,
                            onValueChange = { 
                                manualPassword = it
                                manualStrength = calculateManualStrength(it)
                            },
                            label = { Text("Новый пароль") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        if (manualPassword.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Сложность: ", fontSize = 12.sp)
                                val manualStrengthColor: Color = when (manualStrength) {
                                    PasswordGenerator.Strength.VERY_STRONG -> Color(0xFF4CAF50)
                                    PasswordGenerator.Strength.STRONG -> Color(0xFF2196F3)
                                    PasswordGenerator.Strength.MEDIUM -> Color(0xFFFF9800)
                                    PasswordGenerator.Strength.WEAK -> Color(0xFFF44336)
                                }
                                Text(
                                    manualStrength.name,
                                    fontWeight = FontWeight.Bold,
                                    color = manualStrengthColor,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedMode) {
                        0 -> {
                            if (randomPassword.isNotEmpty()) {
                                onPasswordReplaced(randomPassword, "Случайный пароль, создан при ротации", "random")
                            }
                        }
                        1 -> {
                            if (selectedMnemonicIndex >= 0 && selectedMnemonicIndex < mnemonicVariants.size) {
                                val selected = mnemonicVariants[selectedMnemonicIndex]
                                onPasswordReplaced(selected.password, selected.mnemonicHint, "mnemonic")
                            }
                        }
                        2 -> {
                            if (manualPassword.isNotEmpty()) {
                                onPasswordReplaced(manualPassword, null, "manual")
                            }
                        }
                    }
                },
                enabled = when (selectedMode) {
                    0 -> randomPassword.isNotEmpty()
                    1 -> selectedMnemonicIndex >= 0
                    2 -> manualPassword.isNotEmpty()
                    else -> false
                }
            ) {
                Text("Заменить пароль")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        modifier = Modifier.fillMaxWidth(0.95f)
    )
}

private fun calculateManualStrength(password: String): PasswordGenerator.Strength {
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.length >= 16) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    
    return when {
        score >= 6 -> PasswordGenerator.Strength.VERY_STRONG
        score >= 4 -> PasswordGenerator.Strength.STRONG
        score >= 2 -> PasswordGenerator.Strength.MEDIUM
        else -> PasswordGenerator.Strength.WEAK
    }
}
