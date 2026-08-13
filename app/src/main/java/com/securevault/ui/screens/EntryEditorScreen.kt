@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.security.MasterPasswordHasher
import com.securevault.ui.components.LockActionButton
import com.securevault.ui.components.ProfileAccessDialog
import com.securevault.ui.components.UnifiedPasswordGeneratorDialog
import com.securevault.utils.AccessMode
import com.securevault.utils.AccessResult
import com.securevault.utils.CryptoUtils
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordAccessPolicy
import com.securevault.utils.PasswordGenerator
import com.securevault.utils.PasswordValidator
import com.securevault.viewmodel.AuthViewModel
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    id: String?,
    profileId: Int? = null,
    onBack: () -> Unit,
    onLock: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val isNewEntry = id == null || id == "new"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    
    var showProfileAccessDialog by remember { mutableStateOf(false) }
    var currentAccessAllowBiometric by remember { mutableStateOf(false) }
    var showPinNotSetDialog by remember { mutableStateOf(false) }

    //  Единый диалог генератора (заменяет старые showGeneratorDialog и showMnemonicDialog)
    var showUnifiedGenerator by remember { mutableStateOf(false) }
    
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

    //  Очистка чувствительных данных при блокировке
    LaunchedEffect(Unit) {
        authViewModel.clearSensitiveEvent.collect {
            showPassword = false
            showProfileAccessDialog = false
            showUnifiedGenerator = false
            if (!isNewEntry && existingEntry != null) {
                password = ""
            }
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
                currentAccessAllowBiometric = false
                showProfileAccessDialog = true
            }
            is AccessResult.BiometricOrPin -> {
                currentAccessAllowBiometric = true
                showProfileAccessDialog = true
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
                        //  Единая кнопка генератора
                        IconButton(onClick = { showUnifiedGenerator = true }) { Icon(Icons.Default.Casino, "Генератор паролей") }
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

    //  Диалог доступа к паролю
    if (showProfileAccessDialog && existingEntry != null) {
        val profile = profiles.find { it.id == effectiveProfileId }
        if (profile != null) {
            val dialogSubtitle = if (currentAccessAllowBiometric) "Используйте отпечаток или введите PIN профиля" else "Введите PIN профиля"
            
            ProfileAccessDialog(
                profile = profile,
                title = "Подтверждение доступа",
                subtitle = dialogSubtitle,
                allowBiometric = currentAccessAllowBiometric,
                onConfirmed = {
                    password = existingEntry.password
                    passwordChanged = false
                    showPassword = true
                    showProfileAccessDialog = false
                },
                onDismiss = { showProfileAccessDialog = false }
            )
        }
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

    //  Единый диалог генератора паролей
    if (showUnifiedGenerator) {
        UnifiedPasswordGeneratorDialog(
            onDismiss = { showUnifiedGenerator = false },
            onGenerated = { pwd, hint, type ->
                password = pwd
                passwordChanged = true
                if (hint != null) textHint = hint
                generationType = type
                showUnifiedGenerator = false
            },
            initialServiceName = service
        )
    }

    if (showSaveErrorDialog) {
        AlertDialog(onDismissRequest = { showSaveErrorDialog = false }, icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) }, title = { Text("Ошибка сохранения") }, text = { Text(saveErrorMessage ?: "Неизвестная ошибка") }, confirmButton = { TextButton(onClick = { showSaveErrorDialog = false }) { Text("Понятно") } })
    }
}
