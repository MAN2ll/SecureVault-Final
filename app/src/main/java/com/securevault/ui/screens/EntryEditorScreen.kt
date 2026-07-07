@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.security.MasterPasswordHasher
import com.securevault.utils.CryptoUtils
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator
import com.securevault.utils.PasswordValidator
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    id: String?,
    profileId: Int? = null,
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val isNewEntry = id == null || id == "new"
    val context = LocalContext.current

    val allEntries by viewModel.entries.collectAsState()
    val existingEntry = remember(id, allEntries) {
        if (isNewEntry) null else allEntries.find { e -> e.id == id }
    }

    val currentProfileId by viewModel.currentProfileId.collectAsState()
    
    // ✅ ПРАВИЛЬНО: Используем currentProfileId, если profileId не передан
    val effectiveProfileId = profileId ?: currentProfileId

    val profiles by profileViewModel.profiles.collectAsState()
    val profileName = remember(effectiveProfileId, profiles) {
        val profile = profiles.find { it.id == effectiveProfileId }
        profile?.name ?: "Профиль #${effectiveProfileId ?: "?"}"
    }

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

    var passwordChanged by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPasswordDialog by remember { mutableStateOf(false) }

    var showGeneratorDialog by remember { mutableStateOf(false) }
    var showMnemonicDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    var showSaveErrorDialog by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existingEntry) {
        existingEntry?.let { entry ->
            service = entry.service
            username = entry.username
            password = ""
            url = entry.url ?: ""
            notes = entry.notes ?: ""
            textHint = entry.textHint ?: ""
            rotationEnabled = entry.rotationEnabled
            rotationMonths = entry.rotationPeriodMonths
            isFavorite = entry.isFavorite
            generationType = entry.generationType
            passwordChanged = false
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                            contentDescription = null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        if (service.isBlank()) {
                            saveErrorMessage = "Заполните название сервиса"
                            showSaveErrorDialog = true
                            return@IconButton
                        }
                        if (isNewEntry && password.isBlank()) {
                            saveErrorMessage = "Введите или сгенерируйте пароль"
                            showSaveErrorDialog = true
                            return@IconButton
                        }
                        
                        // ✅ ПРОВЕРКА: профиль должен быть выбран
                        val finalProfileId = effectiveProfileId
                        if (finalProfileId == null) {
                            saveErrorMessage = "Профиль не выбран. Вернитесь в список профилей и войдите в профиль."
                            showSaveErrorDialog = true
                            return@IconButton
                        }

                        val now = System.currentTimeMillis()

                        val finalEntry = if (existingEntry != null) {
                            if (passwordChanged) {
                                val finalPassword = if (password.isBlank()) existingEntry.password else password

                                val validation = PasswordValidator.validateNewPasswordForEntry(
                                    entry = existingEntry,
                                    newPassword = finalPassword,
                                    context = context
                                )
                                if (!validation.isValid) {
                                    saveErrorMessage = validation.errorMessage
                                    showSaveErrorDialog = true
                                    return@IconButton
                                }

                                val encryptedPwd = CryptoUtils.encrypt(finalPassword)
                                val newFingerprint = PasswordValidator.buildPasswordFingerprint(finalPassword, context)
                                val oldFingerprint = PasswordValidator.buildPasswordFingerprint(existingEntry.password, context)
                                val newNextRotationDate = if (rotationEnabled) {
                                    val existingNextDate = existingEntry.nextRotationDate
                                    if (existingNextDate == null || existingEntry.rotationPeriodMonths != rotationMonths) {
                                        now + (rotationMonths * 30L * 24 * 60 * 60 * 1000)
                                    } else {
                                        existingNextDate
                                    }
                                } else {
                                    null
                                }

                                existingEntry.addToPasswordHistory(
                                    oldPassword = existingEntry.password,
                                    generationType = existingEntry.generationType,
                                    oldPasswordFingerprint = oldFingerprint
                                ).copy(
                                    service = service,
                                    username = username,
                                    encryptedPassword = encryptedPwd,
                                    url = url.ifBlank { null },
                                    notes = notes.ifBlank { null },
                                    textHint = textHint.ifBlank { null },
                                    rotationEnabled = rotationEnabled,
                                    rotationPeriodMonths = rotationMonths,
                                    nextRotationDate = newNextRotationDate,
                                    isFavorite = isFavorite,
                                    lastChanged = now,
                                    generationType = generationType,
                                    passwordFingerprint = newFingerprint
                                )
                            } else {
                                val newNextRotationDate = if (rotationEnabled) {
                                    val existingNextDate = existingEntry.nextRotationDate
                                    if (existingNextDate == null || existingEntry.rotationPeriodMonths != rotationMonths) {
                                        now + (rotationMonths * 30L * 24 * 60 * 60 * 1000)
                                    } else {
                                        existingNextDate
                                    }
                                } else {
                                    null
                                }

                                existingEntry.copy(
                                    service = service,
                                    username = username,
                                    url = url.ifBlank { null },
                                    notes = notes.ifBlank { null },
                                    textHint = textHint.ifBlank { null },
                                    rotationEnabled = rotationEnabled,
                                    rotationPeriodMonths = rotationMonths,
                                    nextRotationDate = newNextRotationDate,
                                    isFavorite = isFavorite,
                                    generationType = generationType
                                )
                            }
                        } else {
                            val uniqueCheck = PasswordValidator.validateUniqueCharacters(password)
                            if (!uniqueCheck.isValid) {
                                saveErrorMessage = uniqueCheck.errorMessage
                                showSaveErrorDialog = true
                                return@IconButton
                            }

                            val fingerprint = PasswordValidator.buildPasswordFingerprint(password, context)

                            Entry.create(
                                service = service,
                                username = username,
                                password = password,
                                profileId = finalProfileId,
                                passwordFingerprint = fingerprint,
                                url = url.ifBlank { null },
                                notes = notes.ifBlank { null },
                                textHint = textHint.ifBlank { null },
                                rotationEnabled = rotationEnabled,
                                rotationPeriodMonths = rotationMonths,
                                isFavorite = isFavorite,
                                generationType = generationType
                            )
                        }

                        try {
                            viewModel.insert(finalEntry)
                            showSuccess = true
                            onBack()
                        } catch (e: Exception) {
                            saveErrorMessage = "Не удалось сохранить запись: ${e.message}"
                            showSaveErrorDialog = true
                        }
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
                        text = profileName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium
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
                onValueChange = {
                    password = it
                    passwordChanged = true
                },
                label = {
                    Text(
                        if (!isNewEntry && !passwordChanged && password.isBlank())
                            "Пароль (скрыт)"
                        else
                            "Пароль *"
                    )
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                placeholder = {
                    if (!isNewEntry && !passwordChanged && password.isBlank()) {
                        Text("••••••••••••")
                    }
                },
                trailingIcon = {
                    Row {
                        if (!isNewEntry && existingEntry != null && !passwordChanged) {
                            IconButton(onClick = { showConfirmPasswordDialog = true }) {
                                Icon(Icons.Default.Visibility, "Показать текущий пароль")
                            }
                        } else if (showPassword) {
                            IconButton(onClick = { showPassword = false }) {
                                Icon(Icons.Default.VisibilityOff, "Скрыть пароль")
                            }
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

            if (!isNewEntry && passwordChanged) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Пароль будет изменён и добавлен в историю",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

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
                            if (generationType == "mnemonic") "Мнемонический пароль (AMPG v2)" else "Случайный пароль",
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
                        Text(
                            "Напоминание о смене пароля",
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
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

    if (showConfirmPasswordDialog) {
        ConfirmMasterPasswordDialogForEditor(
            context = context,
            existingEntry = existingEntry,
            onConfirmed = { decryptedPassword ->
                password = decryptedPassword
                passwordChanged = false
                showPassword = true
                showConfirmPasswordDialog = false
            },
            onDismiss = { showConfirmPasswordDialog = false }
        )
    }

    if (showGeneratorDialog) {
        SimplePasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false },
            onGenerated = { pwd ->
                password = pwd
                passwordChanged = true
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
                passwordChanged = true
                textHint = hint
                generationType = "mnemonic"
                showMnemonicDialog = false
            }
        )
    }

    if (showSaveErrorDialog) {
        AlertDialog(
            onDismissRequest = { showSaveErrorDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ошибка сохранения") },
            text = { Text(saveErrorMessage ?: "Неизвестная ошибка") },
            confirmButton = {
                TextButton(onClick = { showSaveErrorDialog = false }) {
                    Text("Понятно")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmMasterPasswordDialogForEditor(
    context: Context,
    existingEntry: Entry?,
    onConfirmed: (decryptedPassword: String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Подтверждение") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Для просмотра текущего пароля введите мастер-пароль:", fontSize = 13.sp)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Мастер-пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val storedHash = prefs.getString("master_hash", null)
                val storedSalt = prefs.getString("master_salt", null)
                val iterations = prefs.getInt("master_iterations", 100_000)

                if (storedHash != null && storedSalt != null &&
                    MasterPasswordHasher.verify(password, storedHash, storedSalt, iterations)) {
                    try {
                        val decrypted = existingEntry?.password ?: ""
                        onConfirmed(decrypted)
                    } catch (e: Exception) {
                        error = "Не удалось расшифровать пароль"
                    }
                } else {
                    error = "Неверный пароль"
                }
                password = ""
            }) {
                Text("Подтвердить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

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
        val result = PasswordGenerator.generate(length, useUpper, useDigits, useSpecial, context)
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
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (generatedPwd.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Пароль:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(generatedPwd))
                            android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Копировать")
                    }

                    OutlinedButton(
                        onClick = {
                            val result = PasswordGenerator.generate(length, useUpper, useDigits, useSpecial, context)
                            generatedPwd = result.password
                            strength = result.strength
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ещё раз")
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
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        modifier = Modifier.fillMaxWidth(0.95f)
    )
}

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
    var validationError by remember { mutableStateOf<String?>(null) }

    fun generateVariants() {
        validationError = null

        if (phrase.isBlank()) {
            variants = emptyList()
            validationError = "Введите мнемоническую фразу"
            return
        }

        if (includeServiceCode && serviceName.isBlank()) {
            variants = emptyList()
            validationError = "Введите название сервиса для кода сервиса"
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
        
        if (variants.isEmpty()) {
            validationError = "Не удалось сгенерировать варианты без повторов"
        }
    }

    LaunchedEffect(phrase, serviceName, includeLeet, includeServiceCode, includeRotationCode) {
        variantOffset = 0
        generateVariants()
    }

    LaunchedEffect(variantOffset) {
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
                    Text("AMPG v2 — 5 вариантов на выбор", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text("Мнемоническая фраза *") },
                    placeholder = { Text("например: моя кошка любит рыбу") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = serviceName,
                    onValueChange = { serviceName = it },
                    label = {
                        Text(if (includeServiceCode) "Название сервиса *" else "Название сервиса (необяз.)")
                    },
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

                if (validationError != null) {
                    Text(validationError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                if (variants.isNotEmpty()) {
                    Text(
                        "Текущий набор: №${variantOffset + 1}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedButton(
                    onClick = { variantOffset++ },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = variants.isNotEmpty() || (phrase.isNotBlank() && (!includeServiceCode || serviceName.isNotBlank()))
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ещё варианты")
                }

                if (variants.isNotEmpty()) {
                    Text("Выберите вариант:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    variants.forEachIndexed { index, result ->
                        val isSelected = selectedVariantIndex == index

                        val variantNameValue: String = result.variantName
                        val passwordValue: String = result.password
                        val strengthNameValue: String = result.strength.name
                        val hintValue: String = result.mnemonicHint
                        val combinedTextValue: String = strengthNameValue + " • " + hintValue

                        val cardColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = variantNameValue, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(4.dp))
                                        Text(text = passwordValue, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(text = combinedTextValue, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Column {
                                        IconButton(onClick = {
                                            clipboardManager.setText(AnnotatedString(result.password))
                                            android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp))
                                        }
                                        RadioButton(selected = isSelected, onClick = { selectedVariantIndex = index })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedVariantIndex >= 0 && selectedVariantIndex < variants.size) {
                        val selected = variants[selectedVariantIndex]
                        onGenerated(selected.password, selected.mnemonicHint)
                    }
                },
                enabled = selectedVariantIndex >= 0
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Выбрать")
            }
        },
        dismissButton = {
            TextButton(onDismiss) {
                Text("Отмена")
            }
        },
        modifier = Modifier.fillMaxWidth(0.95f)
    )
}
