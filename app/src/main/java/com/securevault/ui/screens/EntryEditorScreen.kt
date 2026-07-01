@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.utils.CryptoUtils
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel

@Composable
fun EntryEditorScreen(
    id: String?,
    profileId: Int? = null,
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val isNewEntry = id == null || id == "new"
    
    val allEntries by viewModel.entries.collectAsState()
    val existingEntry = remember(id, allEntries) {
        if (isNewEntry) null else allEntries.find { e -> e.id == id }
    }
    
    val currentProfileId by viewModel.currentProfileId.collectAsState()
    val effectiveProfileId = profileId ?: currentProfileId
    
    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var textHint by remember { mutableStateOf("") }
    var rotationEnabled by remember { mutableStateOf(false) }
    var rotationMonths by remember { mutableIntStateOf(6) }
    var isFavorite by remember { mutableStateOf(false) }
    var generationType by remember { mutableStateOf("random") }
    
    var showPassword by remember { mutableStateOf(false) }
    var showGeneratorDialog by remember { mutableStateOf(false) }
    var showMnemonicDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(existingEntry) {
        existingEntry?.let { entry ->
            service = entry.service
            username = entry.username
            password = entry.password
            url = entry.url ?: ""
            notes = entry.notes ?: ""
            textHint = entry.textHint ?: ""
            rotationEnabled = entry.rotationEnabled
            rotationMonths = entry.rotationPeriodMonths
            isFavorite = entry.isFavorite
            generationType = entry.generationType
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isNewEntry) "Новая запись" else "Редактировать", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = {
                    IconButton(onClick = { isFavorite = !isFavorite }) { 
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                            contentDescription = null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    }
                    IconButton(onClick = {
                        if (service.isBlank() || password.isBlank()) {
                            showError = "Заполните обязательные поля"
                            return@IconButton
                        }
                        if (effectiveProfileId == null) {
                            showError = "Профиль не выбран. Вернитесь назад и войдите в профиль."
                            return@IconButton
                        }
                        
                        val encryptedPwd = CryptoUtils.encrypt(password)
                        
                        // ✅ ИСПРАВЛЕНО: правильная логика ротации
                        val now = System.currentTimeMillis()
                        val newNextRotationDate = if (rotationEnabled) {
                            // Если ротация включена, устанавливаем/пересчитываем дату
                            val existingNextDate = existingEntry?.nextRotationDate
                            if (existingNextDate == null || existingEntry?.rotationPeriodMonths != rotationMonths) {
                                // Новая ротация или изменён период
                                now + (rotationMonths * 30L * 24 * 60 * 60 * 1000)
                            } else {
                                // Оставляем существующую дату
                                existingNextDate
                            }
                        } else {
                            // Ротация выключена
                            null
                        }
                        
                        // ✅ ИСПРАВЛЕНО: добавление в историю только если пароль изменился
                        val finalEntry = if (existingEntry != null) {
                            val passwordChanged = existingEntry.password != password
                            val baseEntry = if (passwordChanged) {
                                existingEntry.addToPasswordHistory(existingEntry.password, existingEntry.generationType)
                            } else {
                                existingEntry
                            }
                            baseEntry.copy(
                                service = service, username = username, encryptedPassword = encryptedPwd,
                                url = url.ifBlank { null }, notes = notes.ifBlank { null },
                                textHint = textHint.ifBlank { null },
                                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationMonths,
                                nextRotationDate = newNextRotationDate,
                                isFavorite = isFavorite, lastChanged = now,
                                generationType = generationType
                            )
                        } else {
                            Entry.create(
                                service = service, username = username, password = password,
                                profileId = effectiveProfileId!!,
                                url = url.ifBlank { null }, notes = notes.ifBlank { null },
                                textHint = textHint.ifBlank { null },
                                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationMonths,
                                isFavorite = isFavorite,
                                generationType = generationType
                            )
                        }
                        
                        viewModel.insert(finalEntry)
                        showSuccess = true
                        onBack()
                    }) { 
                        Icon(Icons.Default.Check, "Сохранить") 
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()), 
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Сохранено!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            
            if (showError != null) { 
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(showError!!, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                    }
                }
                showError = null 
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (effectiveProfileId != null) "Профиль ID: $effectiveProfileId" else "⚠️ Профиль не выбран",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            OutlinedTextField(
                value = service,
                onValueChange = { service = it },
                label = { Text("Сервис *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Логин / Email") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = password, 
                onValueChange = { password = it }, 
                label = { Text("Пароль *") }, 
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), 
                trailingIcon = { 
                    Row { 
                        IconButton(onClick = { showPassword = !showPassword }) { 
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) 
                        } 
                        IconButton(onClick = { showGeneratorDialog = true }) { 
                            Icon(Icons.Default.Casino, "Обычный генератор") 
                        }
                        IconButton(onClick = { showMnemonicDialog = true }) { 
                            Icon(Icons.Default.Lightbulb, "Мнемонический генератор") 
                        } 
                    } 
                }, 
                modifier = Modifier.fillMaxWidth()
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (generationType == "mnemonic") 
                        MaterialTheme.colorScheme.tertiaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (generationType == "mnemonic") Icons.Default.Lightbulb else Icons.Default.Casino,
                        null,
                        tint = if (generationType == "mnemonic") 
                            MaterialTheme.colorScheme.tertiary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            if (generationType == "mnemonic") "Мнемонический пароль (AMPG v1)" else "Случайный пароль",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (generationType == "mnemonic") "Запоминается по подсказке" else "Криптостойкий, не запоминается",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL (необязательно)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметки") },
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.SpaceBetween, 
                        verticalAlignment = Alignment.CenterVertically
                    ) { 
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Напоминание о смене пароля", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Switch(checked = rotationEnabled, onCheckedChange = { rotationEnabled = it }) 
                    }
                    if (rotationEnabled) {
                        Spacer(Modifier.height(12.dp))
                        var expandedMonths by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedMonths,
                            onExpandedChange = { expandedMonths = !expandedMonths }
                        ) {
                            OutlinedTextField(
                                readOnly = true, 
                                value = "$rotationMonths мес.", 
                                onValueChange = {}, 
                                label = { Text("Менять каждые") }, 
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonths) }, 
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedMonths,
                                onDismissRequest = { expandedMonths = false }
                            ) { 
                                listOf(3, 6, 12).forEach { m -> 
                                    DropdownMenuItem(
                                        text = { Text("$m мес.") }, 
                                        onClick = { 
                                            rotationMonths = m
                                            expandedMonths = false
                                        }
                                    ) 
                                } 
                            }
                        }
                    }
                }
            }
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Мнемоническая подсказка", fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textHint,
                        onValueChange = { textHint = it },
                        label = { Text("Текстовая подсказка") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
    
    if (showGeneratorDialog) {
        SimplePasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false }, 
            onGenerated = { pwd -> 
                password = pwd
                generationType = "random"
                showGeneratorDialog = false 
            }
        )
    }
    
    if (showMnemonicDialog) {
        MnemonicGeneratorDialog(
            onDismiss = { showMnemonicDialog = false },
            onGenerated = { pwd, hint ->
                password = pwd
                textHint = hint
                generationType = "mnemonic"
                showMnemonicDialog = false
            }
        )
    }
}

// ===== ПРОСТОЙ ГЕНЕРАТОР (SecureRandom) =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimplePasswordGeneratorDialog(
    onDismiss: () -> Unit, 
    onGenerated: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    var length by remember { mutableIntStateOf(16) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSpecial by remember { mutableStateOf(true) }
    
    var generatedPwd by remember { mutableStateOf("") }
    var strength by remember { mutableStateOf(PasswordGenerator.Strength.STRONG) }
    
    LaunchedEffect(length, useUpper, useDigits, useSpecial) {
        val result = PasswordGenerator.generate(length, useUpper, useDigits, useSpecial)
        generatedPwd = result.password
        strength = result.strength
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Casino, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Обычный генератор", fontWeight = FontWeight.Bold)
                    Text("Криптостойкий случайный пароль", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = androidx.compose.foundation.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (generatedPwd.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Пароль:", fontWeight = FontWeight.Bold)
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedPwd))
                                    android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = generatedPwd,
                                fontSize = 20.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Сложность: ", fontSize = 12.sp)
                                Text(
                                    strength.name,
                                    fontWeight = FontWeight.Bold,
                                    color = when (strength) {
                                        PasswordGenerator.Strength.VERY_STRONG -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                        PasswordGenerator.Strength.STRONG -> MaterialTheme.colorScheme.primary
                                        PasswordGenerator.Strength.MEDIUM -> MaterialTheme.colorScheme.tertiary
                                        PasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }
                    }
                }
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Параметры", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Длина: $length", modifier = Modifier.weight(1f))
                            Slider(
                                value = length.toFloat(),
                                onValueChange = { length = it.toInt() },
                                valueRange = 8f..32f,
                                steps = 24,
                                modifier = Modifier.weight(2f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            Checkbox(checked = useUpper, onCheckedChange = { useUpper = it })
                            Text("Заглавные (A-Z)", Modifier.padding(start = 8.dp)) 
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            Checkbox(checked = useDigits, onCheckedChange = { useDigits = it })
                            Text("Цифры (0-9)", Modifier.padding(start = 8.dp)) 
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it })
                            Text("Спецсимволы (!@#$)", Modifier.padding(start = 8.dp)) 
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val result = PasswordGenerator.generate(length, useUpper, useDigits, useSpecial)
                            generatedPwd = result.password
                            strength = result.strength
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ещё раз")
                    }
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(generatedPwd))
                            android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Копировать")
                    }
                }
            }
        },
        confirmButton = { 
            Button(onClick = { onGenerated(generatedPwd) }) { 
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Использовать") 
            } 
        },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } },
        modifier = Modifier.fillMaxWidth(0.95f)
    )
}

// ===== МНЕМОНИЧЕСКИЙ ГЕНЕРАТОР (AMPG v1) =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MnemonicGeneratorDialog(
    onDismiss: () -> Unit,
    onGenerated: (String, String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    var phrase by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    var includeLeet by remember { mutableStateOf(true) }
    var includeServiceCode by remember { mutableStateOf(true) }
    var includeRotationCode by remember { mutableStateOf(true) }
    var variantOffset by remember { mutableIntStateOf(0) }
    
    var variants by remember { mutableStateOf<List<MnemonicPasswordGenerator.GenerationResult>>(emptyList()) }
    var selectedVariantIndex by remember { mutableIntStateOf(-1) }

    fun generateVariants() {
        if (phrase.isBlank() || serviceName.isBlank()) {
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
            variantOffset = variantOffset
        )
        
        variants = MnemonicPasswordGenerator.generateVariants(options, count = 5)
        selectedVariantIndex = -1
    }

    LaunchedEffect(phrase, serviceName, includeLeet, includeServiceCode, includeRotationCode, variantOffset) {
        generateVariants()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Мнемонический генератор", fontWeight = FontWeight.Bold)
                    Text("AMPG v1 — 5 вариантов на выбор", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text("Мнемоническая фраза") },
                    placeholder = { Text("например: моя кошка любит рыбу") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = serviceName,
                    onValueChange = { serviceName = it },
                    label = { Text("Название сервиса") },
                    placeholder = { Text("например: Gmail") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeLeet, onCheckedChange = { includeLeet = it })
                    Text("Leet-замены (a→@, o→0...)", Modifier.padding(start = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeServiceCode, onCheckedChange = { includeServiceCode = it })
                    Text("Код сервиса", Modifier.padding(start = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeRotationCode, onCheckedChange = { includeRotationCode = it })
                    Text("Код ротации (MMYY)", Modifier.padding(start = 8.dp))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { variantOffset++ },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ещё варианты")
                    }
                }
                
                if (variants.isNotEmpty()) {
                    Text("Выберите вариант:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    
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
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment
