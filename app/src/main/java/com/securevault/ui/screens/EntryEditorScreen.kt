@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.security.ProfilePasswordHasher
import com.securevault.utils.AccessMode
import com.securevault.utils.AccessResult
import com.securevault.utils.CryptoUtils
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordAccessPolicy
import com.securevault.utils.PasswordGenerator
import com.securevault.utils.PasswordValidator
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel
import com.securevault.ui.components.LockActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    id: String?,
    profileId: Int? = null,
    onBack: () -> Unit,
    onLock: () -> Unit, 
    viewModel: VaultViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val isNewEntry = id == null || id == "new"
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val allEntries by viewModel.allEntries.collectAsState()
    val existingEntry = remember(id, allEntries) {
        if (isNewEntry) null else allEntries.find { e -> e.id == id }
    }

    LaunchedEffect(profileId) {
        if (profileId != null) {
            viewModel.setCurrentProfile(profileId)
        }
    }

    val currentProfileId by viewModel.currentProfileId.collectAsState()
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
    var mnemonicPhraseHint by remember { mutableStateOf<String?>(null) }
    var mnemonicOptionsJson by remember { mutableStateOf<String?>(null) }
    var passwordAccessMode by remember { mutableStateOf(AccessMode.INHERIT.value) }

    var passwordChanged by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    
    var showAccessDialog by remember { mutableStateOf(false) }
    var accessPinInput by remember { mutableStateOf("") }
    var accessPinError by remember { mutableStateOf<String?>(null) }
    var showPinNotSetDialog by remember { mutableStateOf(false) }

    var showGeneratorDialog by remember { mutableStateOf(false) }
    var showMnemonicDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    var showSaveErrorDialog by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

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
            mnemonicPhraseHint = entry.mnemonicPhraseHint
            mnemonicOptionsJson = entry.mnemonicOptionsJson
            passwordAccessMode = entry.passwordAccessMode ?: AccessMode.INHERIT.value
            passwordChanged = false
        }
    }

    fun requestPasswordAccess() {
        if (existingEntry == null) return
        val profile = profiles.find { it.id == effectiveProfileId } ?: return
        val result = PasswordAccessPolicy.resolve(existingEntry, profile)
        
        when (result) {
            is AccessResult.Granted -> {
                password = existingEntry.password
                passwordChanged = false
                showPassword = true
            }
            is AccessResult.PinRequired -> {
                showAccessDialog = true
            }
            is AccessResult.BiometricOrPin -> {
                val biometricManager = BiometricManager.from(context)
                val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS && activity != null) {
                    val executor = ContextCompat.getMainExecutor(context)
                    val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            password = existingEntry.password
                            passwordChanged = false
                            showPassword = true
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            showAccessDialog = true
                        }
                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            showAccessDialog = true
                        }
                    })
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Подтвердите личность")
                        .setSubtitle("Для просмотра текущего пароля")
                        .setNegativeButtonText("Использовать PIN")
                        .build()
                    biometricPrompt.authenticate(promptInfo)
                } else {
                    showAccessDialog = true
                }
            }
            is AccessResult.PinNotSet -> {
                showPinNotSetDialog = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewEntry) "Новая запись" else "Изменить", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = {
                    LockActionButton(onLock = onLock)
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                            contentDescription = if (isFavorite) "Убрать из избранного" else "В избранное",
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

                        val finalProfileId = effectiveProfileId
                        if (finalProfileId == null) {
                            saveErrorMessage = "Профиль не выбран"
                            showSaveErrorDialog = true
                            return@IconButton
                        }

                        val now = System.currentTimeMillis()
                        val finalEntry = if (existingEntry != null) {
                            if (passwordChanged) {
                                val finalPassword = if (password.isBlank()) existingEntry.password else password
                                val validation = PasswordValidator.validateNewPasswordForEntry(entry = existingEntry, newPassword = finalPassword, context = context)
                                if (!validation.isValid) {
                                    saveErrorMessage = validation.errorMessage
                                    showSaveErrorDialog = true
                                    return@IconButton
                                }

                                val encryptedPwd = CryptoUtils.encrypt(finalPassword)
                                val newFingerprint = PasswordValidator.buildPasswordFingerprint(finalPassword, context)
                                val oldFingerprint = PasswordValidator.buildPasswordFingerprint(existingEntry.password, context)
                                val newNextRotationDate = if (rotationEnabled) {
                                    if (!existingEntry.rotationEnabled || existingEntry.rotationPeriodMonths != rotationMonths) now + (rotationMonths * 30L * 24 * 60 * 60 * 1000) else existingEntry.nextRotationDate
                                } else null

                                existingEntry.addToPasswordHistory(oldPassword = existingEntry.password, generationType = existingEntry.generationType, oldPasswordFingerprint = oldFingerprint).copy(
                                    id = existingEntry.id, profileId = existingEntry.profileId, service = service, username = username,
                                    encryptedPassword = encryptedPwd, url = url.ifBlank { null }, notes = notes.ifBlank { null },
                                    textHint = textHint.ifBlank { null }, rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationMonths,
                                    nextRotationDate = newNextRotationDate, isFavorite = isFavorite, lastChanged = now,
                                    generationType = generationType, passwordFingerprint = newFingerprint,
                                    mnemonicPhraseHint = mnemonicPhraseHint, mnemonicOptionsJson = mnemonicOptionsJson,
                                    createdAt = existingEntry.createdAt, passwordAccessMode = passwordAccessMode
                                )
                            } else {
                                val newNextRotationDate = if (rotationEnabled) {
                                    if (!existingEntry.rotationEnabled || existingEntry.rotationPeriodMonths != rotationMonths) now + (rotationMonths * 30L * 24 * 60 * 60 * 1000) else existingEntry.nextRotationDate
                                } else null
                                existingEntry.copy(
                                    service = service, username = username, url = url.ifBlank { null }, notes = notes.ifBlank { null },
                                    textHint = textHint.ifBlank { null }, rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationMonths,
                                    nextRotationDate = newNextRotationDate, isFavorite = isFavorite, generationType = generationType,
                                    mnemonicPhraseHint = mnemonicPhraseHint, mnemonicOptionsJson = mnemonicOptionsJson,
                                    lastChanged = now, passwordAccessMode = passwordAccessMode
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
                            val nextRotationDate = if (rotationEnabled) now + (rotationMonths * 30L * 24 * 60 * 60 * 1000) else null

                            Entry.create(
                                service = service, username = username, password = password, profileId = finalProfileId,
                                passwordFingerprint = fingerprint, url = url.ifBlank { null }, notes = notes.ifBlank { null },
                                textHint = textHint.ifBlank { null }, rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationMonths,
                                isFavorite = isFavorite, generationType = generationType, mnemonicPhraseHint = mnemonicPhraseHint,
                                mnemonicOptionsJson = mnemonicOptionsJson, passwordAccessMode = passwordAccessMode
                            ).copy(nextRotationDate = nextRotationDate)
                        }

                        isSaving = true
                        if (isNewEntry) {
                            viewModel.insertEntry(finalEntry) { result ->
                                isSaving = false
                                when (result) {
                                    is PasswordOperationResult.Success -> { showSuccess = true; onBack() }
                                    is PasswordOperationResult.Error -> { saveErrorMessage = result.message; showSaveErrorDialog = true }
                                }
                            }
                        } else {
                            viewModel.updateEntry(finalEntry) { result ->
                                isSaving = false
                                when (result) {
                                    is PasswordOperationResult.Success -> { showSuccess = true; onBack() }
                                    is PasswordOperationResult.Error -> { saveErrorMessage = result.message; showSaveErrorDialog = true }
                                }
                            }
                        }
                    }) { Icon(Icons.Default.Check, "Сохранить") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showSuccess) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Сохранено!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            if (showError != null) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(showError!!, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                    }
                }
                showError = null
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(text = profileName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Medium)
                }
            }

            OutlinedTextField(value = service, onValueChange = { service = it }, label = { Text("Сервис *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Логин / Email") }, modifier = Modifier.fillMaxWidth())

            OutlinedTextField(
                value = password, onValueChange = { password = it; passwordChanged = true },
                label = { Text(if (!isNewEntry && !passwordChanged && password.isBlank()) "Пароль (скрыт)" else "Пароль *") },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                placeholder = { if (!isNewEntry && !passwordChanged && password.isBlank()) Text("••••••••••••") },
                trailingIcon = {
                    Row {
                        if (!isNewEntry && existingEntry != null && !passwordChanged) {
                            IconButton(onClick = { requestPasswordAccess() }) { Icon(Icons.Default.Visibility, "Показать текущий пароль") }
                        } else if (showPassword) {
                            IconButton(onClick = { showPassword = false }) { Icon(Icons.Default.VisibilityOff, "Скрыть пароль") }
                        }
                        IconButton(onClick = { showGeneratorDialog = true }) { Icon(Icons.Default.Casino, "Обычный генератор") }
                        IconButton(onClick = { showMnemonicDialog = true }) { Icon(Icons.Default.Lightbulb, "Мнемонический генератор") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (!isNewEntry && passwordChanged) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text("Пароль будет изменён и добавлен в историю", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (generationType == "mnemonic") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (generationType == "mnemonic") Icons.Default.Lightbulb else Icons.Default.Casino, null, tint = if (generationType == "mnemonic") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(if (generationType == "mnemonic") "Мнемонический пароль (AMPG v2)" else "Случайный пароль", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(if (generationType == "mnemonic") "Запоминается по подсказке" else "Криптостойкий, не запоминается", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL (необязательно)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Заметки") }, modifier = Modifier.fillMaxWidth().height(100.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Напоминание о смене пароля", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Switch(checked = rotationEnabled, onCheckedChange = { rotationEnabled = it })
                    }
                    if (rotationEnabled) {
                        Spacer(Modifier.height(12.dp))
                        var expandedMonths by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expandedMonths, onExpandedChange = { expandedMonths = !expandedMonths }) {
                            OutlinedTextField(readOnly = true, value = "$rotationMonths мес.", onValueChange = {}, label = { Text("Менять каждые") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonths) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded = expandedMonths, onDismissRequest = { expandedMonths = false }) {
                                listOf(3, 6, 12).forEach { m -> DropdownMenuItem(text = { Text("$m мес.") }, onClick = { rotationMonths = m; expandedMonths = false }) }
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Защита этой записи", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    val currentMode = AccessMode.values().find { it.value == passwordAccessMode } ?: AccessMode.INHERIT
                    
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            readOnly = true,
                            value = when (currentMode) {
                                AccessMode.INHERIT -> "Как в профиле"
                                AccessMode.NO_CONFIRMATION -> "Без подтверждения"
                                AccessMode.BIOMETRIC_OR_PIN -> "Отпечаток или PIN профиля"
                                AccessMode.PIN_REQUIRED -> "Только PIN профиля"
                                else -> "Как в профиле"
                            },
                            onValueChange = {}, label = { Text("Режим защиты") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf(
                                AccessMode.INHERIT to "Как в профиле",
                                AccessMode.NO_CONFIRMATION to "Без подтверждения",
                                AccessMode.BIOMETRIC_OR_PIN to "Отпечаток или PIN профиля",
                                AccessMode.PIN_REQUIRED to "Только PIN профиля"
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { passwordAccessMode = mode.value; expanded = false })
                            }
                        }
                    }
                    Text("Опасные действия (удаление, экспорт) всегда требуют мастер-пароль.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
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
                    OutlinedTextField(value = textHint, onValueChange = { textHint = it }, label = { Text("Текстовая подсказка") }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (showAccessDialog) {
        AlertDialog(
            onDismissRequest = { showAccessDialog = false },
            title = { Text("Введите PIN профиля") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = accessPinInput,
                        onValueChange = { accessPinInput = it; accessPinError = null },
                        label = { Text("PIN профиля") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = accessPinError != null
                    )
                    if (accessPinError != null) Text(accessPinError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val profile = profiles.find { it.id == effectiveProfileId }
                    if (profile != null && ProfilePasswordHasher.verify(accessPinInput, profile.passwordHash, profile.passwordSalt)) {
                        password = existingEntry?.password ?: ""
                        passwordChanged = false
                        showPassword = true
                        showAccessDialog = false
                        accessPinInput = ""
                    } else {
                        accessPinError = "Неверный PIN профиля"
                    }
                }) { Text("Подтвердить") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessDialog = false; accessPinInput = "" }) { Text("Отмена") }
            }
        )
    }

    if (showPinNotSetDialog) {
        AlertDialog(
            onDismissRequest = { showPinNotSetDialog = false },
            title = { Text("PIN профиля не задан") },
            text = { Text("Для этого действия нужно сначала задать PIN профиля в настройках.") },
            confirmButton = {
                TextButton(onClick = { showPinNotSetDialog = false }) { Text("Понятно") }
            }
        )
    }

    if (showGeneratorDialog) {
        SimplePasswordGeneratorDialog(onDismiss = { showGeneratorDialog = false }, onGenerated = { pwd -> password = pwd; passwordChanged = true; generationType = "random"; showGeneratorDialog = false })
    }
    if (showMnemonicDialog) {
        MnemonicGeneratorDialog(
            username = username,
            profileId = effectiveProfileId,
            initialServiceName = service,
            onDismiss = { showMnemonicDialog = false },
            onGenerated = { pwd, hint -> password = pwd; passwordChanged = true; textHint = hint; generationType = "mnemonic"; showMnemonicDialog = false }
        )
    }
    if (showSaveErrorDialog) {
        AlertDialog(onDismissRequest = { showSaveErrorDialog = false }, icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) }, title = { Text("Ошибка сохранения") }, text = { Text(saveErrorMessage ?: "Неизвестная ошибка") }, confirmButton = { TextButton(onClick = { showSaveErrorDialog = false }) { Text("Понятно") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimplePasswordGeneratorDialog(onDismiss: () -> Unit, onGenerated: (String) -> Unit) {
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

    AlertDialog(onDismissRequest = onDismiss, title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Casino, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Column { Text("Обычный генератор", fontWeight = FontWeight.Bold); Text("Криптостойкий случайный пароль", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }, text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (generatedPwd.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Пароль:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(text = generatedPwd, fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Сложность: ", fontSize = 12.sp)
                            Text(strength.name, fontWeight = FontWeight.Bold, color = when (strength) { PasswordGenerator.Strength.VERY_STRONG -> androidx.compose.ui.graphics.Color(0xFF4CAF50); PasswordGenerator.Strength.STRONG -> MaterialTheme.colorScheme.primary; PasswordGenerator.Strength.MEDIUM -> MaterialTheme.colorScheme.tertiary; PasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                        }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Параметры", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Длина: $length", modifier = Modifier.weight(1f)); Slider(value = length.toFloat(), onValueChange = { length = it.toInt() }, valueRange = 8f..32f, steps = 24, modifier = Modifier.weight(2f)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = useUpper, onCheckedChange = { useUpper = it }); Text("Заглавные (A-Z)", Modifier.padding(start = 8.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = useDigits, onCheckedChange = { useDigits = it }); Text("Цифры (0-9)", Modifier.padding(start = 8.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it }); Text("Спецсимволы (!@#$)", Modifier.padding(start = 8.dp)) }
                }
            }
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { clipboardManager.setText(AnnotatedString(generatedPwd)); android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Копировать") }
                OutlinedButton(onClick = { val result = PasswordGenerator.generate(length, useUpper, useDigits, useSpecial, context); generatedPwd = result.password; strength = result.strength }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Icon(Icons.Default.Refresh, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Ещё раз") }
            }
        }
    }, confirmButton = { Button(onClick = { onGenerated(generatedPwd) }) { Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Использовать") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }, modifier = Modifier.fillMaxWidth(0.95f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MnemonicGeneratorDialog(
    username: String,
    profileId: Int?,
    initialServiceName: String,
    onDismiss: () -> Unit,
    onGenerated: (String, String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var phrase by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf(initialServiceName) }
    var splitMode by remember { mutableStateOf(MnemonicPasswordGenerator.SplitMode.SINGLE_USER) }
    var targetLength by remember { mutableIntStateOf(16) }
    
    var variantPages by remember { mutableStateOf<List<List<MnemonicPasswordGenerator.GenerationResult>>>(emptyList()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var nextOffset by remember { mutableIntStateOf(0) }
    var isGenerating by remember { mutableStateOf(false) }
    var noMoreVariants by remember { mutableStateOf(false) }
    var selectedVariantIndex by remember { mutableIntStateOf(-1) }
    var showExplanation by remember { mutableStateOf<MnemonicPasswordGenerator.GenerationResult?>(null) }

    fun loadNextPage() {
        if (isGenerating || noMoreVariants) return
        isGenerating = true
        
        val options = MnemonicPasswordGenerator.GenerationOptions(
            phrase = phrase,
            serviceName = serviceName,
            username = username,
            profileId = profileId,
            targetLength = if (splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS) {
                when { targetLength <= 16 -> 16; targetLength <= 18 -> 18; else -> 20 }
            } else { targetLength },
            rotationMonth = null,
            rotationYear = null,
            variantOffset = nextOffset,
            splitMode = splitMode
        )
        
        val newVariants = MnemonicPasswordGenerator.generateVariants(options, count = 3)
        
        if (newVariants.isNotEmpty()) {
            variantPages = variantPages + listOf(newVariants)
            currentPageIndex = variantPages.size - 1
            nextOffset = options.variantOffset + 300
            noMoreVariants = newVariants.size < 3
        } else {
            noMoreVariants = true
        }
        isGenerating = false
        selectedVariantIndex = -1
    }

    fun loadPreviousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            selectedVariantIndex = -1
        }
    }

    LaunchedEffect(phrase, serviceName, splitMode, targetLength) {
        variantPages = emptyList()
        currentPageIndex = 0
        nextOffset = 0
        noMoreVariants = false
        selectedVariantIndex = -1
        if (phrase.isNotBlank()) loadNextPage()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Column { Text("Мнемонический генератор", fontWeight = FontWeight.Bold); Text("AMPG v2", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Режим", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = splitMode == MnemonicPasswordGenerator.SplitMode.SINGLE_USER, onClick = { splitMode = MnemonicPasswordGenerator.SplitMode.SINGLE_USER }); Text(text = "Обычный", modifier = Modifier.padding(start = 4.dp), fontSize = 12.sp) }
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS, onClick = { splitMode = MnemonicPasswordGenerator.SplitMode.TWO_USERS }); Column(Modifier.padding(start = 4.dp)) { Text("Для двух пользователей", fontSize = 12.sp); Text("Один пароль на две равные части", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    }
                }
                OutlinedTextField(value = phrase, onValueChange = { phrase = it }, label = { Text("Мнемоническая фраза") }, placeholder = { Text("например: мой кот любит молоко") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = serviceName, onValueChange = { serviceName = it }, label = { Text("Сервис") }, placeholder = { Text("например: Gmail") }, modifier = Modifier.fillMaxWidth())
                if (splitMode == MnemonicPasswordGenerator.SplitMode.SINGLE_USER) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Длина: $targetLength", modifier = Modifier.weight(1f), fontSize = 12.sp); Slider(value = targetLength.toFloat(), onValueChange = { targetLength = it.toInt() }, valueRange = 12f..24f, steps = 12, modifier = Modifier.weight(2f)) }
                } else {
                    Text("Длина:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(16, 18, 20).forEach { length -> FilterChip(selected = targetLength == length, onClick = { targetLength = length }, label = { Text("$length") }) } }
                    Text("Каждая часть по ${targetLength / 2} символов", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (variantPages.isNotEmpty()) {
                    val currentPage = variantPages[currentPageIndex]
                    Text("Варианты ${currentPageIndex * 3 + 1}–${currentPageIndex * 3 + currentPage.size}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    
                    currentPage.forEachIndexed { index, result ->
                        val isSelected = selectedVariantIndex == index
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedVariantIndex = index }, colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Вариант ${currentPageIndex * 3 + index + 1}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(2.dp))
                                        Text(text = result.password, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        if (result.splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS) Text("Части: ${result.part1?.length ?: 0} + ${result.part2?.length ?: 0}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column {
                                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(result.password)); android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.ContentCopy, contentDescription = "Копировать", Modifier.size(18.dp)) }
                                        RadioButton(selected = isSelected, onClick = { selectedVariantIndex = index })
                                    }
                                }
                                TextButton(onClick = { showExplanation = result }, modifier = Modifier.align(Alignment.End)) { Text("Как собран", fontSize = 10.sp) }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { loadPreviousPage() }, enabled = currentPageIndex > 0 && !isGenerating) {
                            Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Предыдущие")
                        }
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            TextButton(onClick = { loadNextPage() }, enabled = !noMoreVariants && !isGenerating) {
                                Text("Следующие")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp))
                            }
                        }
                    }
                    if (noMoreVariants && !isGenerating) {
                        Text("Новых подходящих вариантов не найдено.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }
                } else if (phrase.isNotBlank() && !isGenerating) {
                    Text("Не удалось сгенерировать валидные варианты. Попробуйте изменить фразу или длину.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                if (selectedVariantIndex >= 0 && variantPages.isNotEmpty()) {
                    val selected = variantPages[currentPageIndex][selectedVariantIndex]
                    onGenerated(selected.password, selected.mnemonicHint) 
                }
            }, enabled = selectedVariantIndex >= 0 && variantPages.isNotEmpty()) { 
                Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Выбрать") 
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } },
        modifier = Modifier.fillMaxWidth(0.95f)
    )

    if (showExplanation != null) {
        AlertDialog(
            onDismissRequest = { showExplanation = null },
            title = { Text("Как собран пароль") },
            text = { Text(showExplanation!!.explanation, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
            confirmButton = { TextButton(onClick = { showExplanation = null }) { Text("Понятно") } }
        )
    }
}
