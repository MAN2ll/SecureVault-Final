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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator
import com.securevault.utils.PasswordValidator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordRotationDialog(
    serviceName: String,
    currentHint: String?,
    generationType: String,
    rotationMonth: Int?,
    rotationYear: Int?,
    onDismiss: () -> Unit,
    onPasswordReplaced: (
        newPassword: String,
        newHint: String?,
        newGenerationType: String,
        mnemonicPhrase: String?,
        mnemonicOptions: String?
    ) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var selectedMode by remember { mutableStateOf(if (generationType == "mnemonic") "mnemonic" else "random") }
    var manualPassword by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf<String?>(null) }
    
    // ✅ НОВОЕ: AlertDialog для ошибок
    var showReplaceErrorDialog by remember { mutableStateOf(false) }
    var replaceErrorMessage by remember { mutableStateOf<String?>(null) }

    var phrase by remember { mutableStateOf(currentHint ?: "") }
    var includeLeet by remember { mutableStateOf(true) }
    var includeServiceCode by remember { mutableStateOf(true) }
    var includeRotationCode by remember { mutableStateOf(true) }
    var variantOffset by remember { mutableIntStateOf(0) }
    var variants by remember { mutableStateOf<List<MnemonicPasswordGenerator.GenerationResult>>(emptyList()) }
    var selectedVariantIndex by remember { mutableIntStateOf(-1) }

    var generatedRandomPwd by remember { mutableStateOf("") }
    var randomLength by remember { mutableIntStateOf(16) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSpecial by remember { mutableStateOf(true) }

    fun generateRandom() {
        val result = PasswordGenerator.generate(randomLength, useUpper, useDigits, useSpecial, context)
        generatedRandomPwd = result.password
    }

    fun generateMnemonicVariants() {
        if (phrase.isBlank()) {
            variants = emptyList()
            return
        }

        val options = MnemonicPasswordGenerator.GenerationOptions(
            phrase = phrase,
            serviceName = serviceName,
            targetLength = 16,
            includeLeet = includeLeet,
            includeServiceCode = includeServiceCode,
            includeRotationCode = includeRotationCode,
            rotationMonth = rotationMonth,
            rotationYear = rotationYear,
            variantOffset = variantOffset
        )

        variants = MnemonicPasswordGenerator.generateVariants(options, count = 5)
        selectedVariantIndex = -1
        
        if (variants.isEmpty()) {
            showError = "Не удалось сгенерировать варианты без повторов"
        }
    }

    LaunchedEffect(phrase, includeLeet, includeServiceCode, includeRotationCode, variantOffset) {
        if (selectedMode == "mnemonic") {
            generateMnemonicVariants()
        }
    }

    LaunchedEffect(selectedMode) {
        if (selectedMode == "random") {
            generateRandom()
        } else {
            generateMnemonicVariants()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Ротация: $serviceName", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Режим генерации", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedMode == "random", onClick = { selectedMode = "random" })
                            Text("Случайный пароль", Modifier.padding(start = 8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedMode == "mnemonic", onClick = { selectedMode = "mnemonic" })
                            Text("Мнемонический (AMPG v2)", Modifier.padding(start = 8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedMode == "manual", onClick = { selectedMode = "manual" })
                            Text("Ввести вручную", Modifier.padding(start = 8.dp))
                        }
                    }
                }

                when (selectedMode) {
                    "random" -> {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Параметры", fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Длина: $randomLength", modifier = Modifier.weight(1f))
                                    Slider(
                                        value = randomLength.toFloat(),
                                        onValueChange = { randomLength = it.toInt() },
                                        valueRange = 8f..32f,
                                        steps = 24,
                                        modifier = Modifier.weight(2f)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = useUpper, onCheckedChange = { useUpper = it })
                                    Text("Заглавные", Modifier.padding(start = 8.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = useDigits, onCheckedChange = { useDigits = it })
                                    Text("Цифры", Modifier.padding(start = 8.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it })
                                    Text("Спецсимволы", Modifier.padding(start = 8.dp))
                                }
                                
                                if (generatedRandomPwd.isNotEmpty()) {
                                    Text(
                                        generatedRandomPwd,
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Row {
                                    OutlinedButton(onClick = { generateRandom() }) {
                                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ещё раз")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(generatedRandomPwd))
                                        android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Копировать")
                                    }
                                }
                            }
                        }
                    }
                    
                    "mnemonic" -> {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("AMPG v2 — Уникальный поток", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                
                                OutlinedTextField(
                                    value = phrase,
                                    onValueChange = { phrase = it },
                                    label = { Text("Мнемоническая фраза") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = includeLeet, onCheckedChange = { includeLeet = it })
                                    Text("Leet-замены", Modifier.padding(start = 8.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = includeServiceCode, onCheckedChange = { includeServiceCode = it })
                                    Text("Код сервиса", Modifier.padding(start = 8.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = includeRotationCode, onCheckedChange = { includeRotationCode = it })
                                    Text("Код ротации", Modifier.padding(start = 8.dp))
                                }
                                
                                OutlinedButton(
                                    onClick = { variantOffset++ },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Ещё варианты (набор №${variantOffset + 1})")
                                }
                                
                                if (variants.isNotEmpty()) {
                                    Text("Выберите вариант:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                    
                                    variants.forEachIndexed { index, result ->
                                        val isSelected = selectedVariantIndex == index
                                        
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) 
                                                    MaterialTheme.colorScheme.primaryContainer 
                                                else 
                                                    MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(result.variantName, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                                    Text(
                                                        result.password,
                                                        fontSize = 12.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { selectedVariantIndex = index }
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                    
                    "manual" -> {
                        OutlinedTextField(
                            value = manualPassword,
                            onValueChange = { manualPassword = it; showError = null },
                            label = { Text("Новый пароль") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (showError != null) {
                    Text(showError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when (selectedMode) {
                    "random" -> {
                        if (generatedRandomPwd.isBlank()) {
                            showError = "Сгенерируйте пароль"
                            return@Button
                        }
                        onPasswordReplaced(generatedRandomPwd, null, "random", null, null)
                    }
                    "mnemonic" -> {
                        if (selectedVariantIndex < 0 || selectedVariantIndex >= variants.size) {
                            showError = "Выберите вариант"
                            return@Button
                        }
                        val selected = variants[selectedVariantIndex]
                        
                        //  ФИНАЛЬНАЯ ПРОВЕРКА
                        if (PasswordValidator.hasDuplicateCharacters(selected.password)) {
                            replaceErrorMessage = "Выбранный пароль содержит повторяющиеся символы. Выберите другой вариант."
                            showReplaceErrorDialog = true
                            return@Button
                        }
                        
                        onPasswordReplaced(
                            selected.password,
                            selected.mnemonicHint,
                            "mnemonic",
                            phrase,
                            null
                        )
                    }
                    "manual" -> {
                        if (manualPassword.isBlank()) {
                            showError = "Введите пароль"
                            return@Button
                        }
                        onPasswordReplaced(manualPassword, null, "manual", null, null)
                    }
                }
            }) {
                Text("Заменить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )

    //  AlertDialog для ошибок замены
    if (showReplaceErrorDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceErrorDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ошибка замены пароля") },
            text = { Text(replaceErrorMessage ?: "Неизвестная ошибка") },
            confirmButton = {
                TextButton(onClick = { showReplaceErrorDialog = false }) {
                    Text("Понятно")
                }
            }
        )
    }
}
