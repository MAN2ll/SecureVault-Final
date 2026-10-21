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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.ui.components.LockActionButton
import com.securevault.ui.components.ProfileAccessDialog
import com.securevault.ui.components.UnifiedPasswordGeneratorDialog
import com.securevault.utils.AccessMode
import com.securevault.utils.AccessResult
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordValidator
import com.securevault.utils.resolveAccess
import com.securevault.viewmodel.AuthViewModel
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    id: String? = null,
    profileId: Int?,
    onBack: () -> Unit,
    onLock: () -> Unit = {},
    authViewModel: AuthViewModel,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loadError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tagsCsv by remember { mutableStateOf("") }

    var isFavorite by remember { mutableStateOf(false) }
    var rotationEnabled by remember { mutableStateOf(false) }
    var rotationPeriodMonths by remember { mutableIntStateOf(6) }
    var passwordAccessMode by remember { mutableStateOf(AccessMode.INHERIT.value) }

    var generationType by remember { mutableStateOf("random") }
    var textHint by remember { mutableStateOf<String?>(null) }
    var mnemonicPhraseHint by remember { mutableStateOf<String?>(null) }
    var mnemonicOptionsJson by remember { mutableStateOf<String?>(null) }

    var originalEntry by remember { mutableStateOf<Entry?>(null) }
    var originalPassword by remember { mutableStateOf<String?>(null) }
    var passwordDraft by remember { mutableStateOf<String?>(null) }
    var isPasswordViewed by remember { mutableStateOf(false) }
    var isPasswordChanged by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    var showGeneratorDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    var showAccessDialog by remember { mutableStateOf(false) }
    var requireBiometricForAccess by remember { mutableStateOf(false) }
    var pendingAccessAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val currentProfileIdState by viewModel.currentProfileId.collectAsState()
    val targetProfileId: Int = profileId ?: currentProfileIdState ?: 0
    val profiles by viewModel.profiles.collectAsState()

    val isEditMode: Boolean = id != null && id != "new"

    LaunchedEffect(Unit) {
        authViewModel.clearSensitiveEvent.collect {
            originalPassword = null
            passwordDraft = null
            isPasswordViewed = false
            isPasswordChanged = false
            passwordVisible = false
            formError = null
            showAccessDialog = false
            showGeneratorDialog = false
            pendingAccessAction = null
        }
    }

    LaunchedEffect(id) {
        if (isEditMode) {
            isLoading = true
            try {
                val entry = viewModel.getEntryById(id!!)
                if (entry != null) {
                    originalEntry = entry
                    service = entry.service
                    username = entry.username
                    url = entry.url.orEmpty()
                    notes = entry.notes.orEmpty()
                    tagsCsv = entry.tagsCsv
                    isFavorite = entry.isFavorite
                    rotationEnabled = entry.rotationEnabled
                    rotationPeriodMonths = entry.rotationPeriodMonths
                    passwordAccessMode = entry.passwordAccessMode
                    generationType = entry.generationType
                    textHint = entry.textHint
                    mnemonicPhraseHint = entry.mnemonicPhraseHint
                    mnemonicOptionsJson = entry.mnemonicOptionsJson

                    originalPassword = null
                    passwordDraft = null
                    isPasswordViewed = false
                    isPasswordChanged = false
                } else {
                    loadError = "Запись не найдена в базе данных"
                }
            } catch (e: Exception) {
                loadError = "Ошибка загрузки: ${e.message}"
            } finally {
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    fun processTags(input: String): String {
        return input.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(",")
    }

    fun requestPasswordAccess(action: () -> Unit) {
        formError = null
        val accessProfileId: Int = originalEntry?.profileId ?: targetProfileId
        val profile = profiles.find { it.id == accessProfileId }

        if (profile == null) {
            formError = "Ошибка: профиль записи не найден"
            return
        }

        val dummyEntry = originalEntry ?: Entry(
            id = id.orEmpty(),
            service = service,
            username = username,
            encryptedPassword = "",
            profileId = accessProfileId,
            passwordAccessMode = passwordAccessMode
        )

        val result = resolveAccess(dummyEntry, profile)

        when (result) {
            is AccessResult.Granted -> action()
            is AccessResult.PinRequired -> {
                requireBiometricForAccess = false
                pendingAccessAction = action
                showAccessDialog = true
            }
            is AccessResult.BiometricOrPin -> {
                requireBiometricForAccess = true
                pendingAccessAction = action
                showAccessDialog = true
            }
            is AccessResult.PinNotSet -> {
                formError = "Для редактирования пароля необходимо задать PIN в настройках профиля."
            }
        }
    }

    fun isPasswordDifferentEnough(oldPwd: String, newPwd: String): Boolean {
        if (oldPwd == newPwd) return false
        val maxLength = maxOf(oldPwd.length, newPwd.length)
        if (maxLength == 0) return true

        var matches = 0
        val minLen = minOf(oldPwd.length, newPwd.length)
        for (i in 0 until minLen) {
            if (oldPwd[i] == newPwd[i]) matches++
        }
        return (matches.toDouble() / maxLength) <= 0.4
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Редактирование" else "Новая запись", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = {
                    LockActionButton(onLock = {
                        onLock()
                        originalPassword = null
                        passwordDraft = null
                        isPasswordViewed = false
                        isPasswordChanged = false
                        passwordVisible = false
                    })
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (loadError != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(loadError!!, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Вернуться назад") }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = service,
                    onValueChange = { service = it },
                    label = { Text("Сервис *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Логин / Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = if (isEditMode && !isPasswordViewed) "" else passwordDraft.orEmpty(),
                    onValueChange = { newPwd ->
                        passwordDraft = newPwd
                        if (!isEditMode) {
                            isPasswordChanged = true
                            generationType = "manual"
                            textHint = null
                            mnemonicPhraseHint = null
                            mnemonicOptionsJson = null
                        } else {
                            if (isPasswordViewed && originalPassword != null && newPwd != originalPassword) {
                                isPasswordChanged = true
                                generationType = "manual"
                                textHint = null
                                mnemonicPhraseHint = null
                                mnemonicOptionsJson = null
                            }
                        }
                    },
                    label = { Text("Пароль *") },
                    singleLine = true,
                    readOnly = isEditMode && !isPasswordViewed,
                    placeholder = {
                        if (isEditMode && !isPasswordViewed) {
                            Text("••••••••••••", fontSize = 16.sp)
                        }
                    },
                    visualTransformation = if (passwordVisible)
                        androidx.compose.ui.text.input.VisualTransformation.None
                    else
                        androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            if (isEditMode && !isPasswordViewed) {
                                IconButton(onClick = {
                                    requestPasswordAccess {
                                        scope.launch {
                                            try {
                                                val entry = viewModel.getEntryById(id!!)
                                                if (entry != null) {
                                                    originalPassword = CryptoUtils.decrypt(entry.encryptedPassword)
                                                    passwordDraft = originalPassword
                                                    isPasswordViewed = true
                                                    passwordVisible = true
                                                }
                                            } catch (e: Exception) {
                                                formError = "Ошибка расшифровки: ${e.message}"
                                            }
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Visibility, "Разблокировать для изменения")
                                }
                            } else {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        "Показать/скрыть"
                                    )
                                }
                            }
                            IconButton(onClick = {
                                if (isEditMode && !isPasswordViewed) {
                                    requestPasswordAccess { showGeneratorDialog = true }
                                } else {
                                    showGeneratorDialog = true
                                }
                            }) {
                                Icon(Icons.Default.AutoAwesome, "Сгенерировать")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Пароль можно ввести вручную или сгенерировать",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp)
                )

                OutlinedTextField(
                    value = tagsCsv,
                    onValueChange = { tagsCsv = it },
                    label = { Text("Теги") },
                    supportingText = { Text("Через запятую: работа, почта") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL сайта") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Заметки") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isFavorite, onCheckedChange = { isFavorite = it })
                    Text("Добавить в избранное", fontSize = 14.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rotationEnabled, onCheckedChange = { rotationEnabled = it })
                    Text("Включить авто-ротацию", fontSize = 14.sp)
                }

                if (rotationEnabled) {
                    var expandedRotation by remember { mutableStateOf(false) }
                    val periods = listOf(1, 3, 6, 12)
                    ExposedDropdownMenuBox(
                        expanded = expandedRotation,
                        onExpandedChange = { expandedRotation = !expandedRotation }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = "$rotationPeriodMonths мес.",
                            onValueChange = {},
                            label = { Text("Период ротации") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedRotation) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedRotation,
                            onDismissRequest = { expandedRotation = false }
                        ) {
                            periods.forEach { period ->
                                DropdownMenuItem(
                                    text = { Text("$period мес.") },
                                    onClick = { rotationPeriodMonths = period; expandedRotation = false }
                                )
                            }
                        }
                    }
                }

                var expandedAccess by remember { mutableStateOf(false) }
                val accessModeLabels = mapOf(
                    AccessMode.INHERIT.value to "Как в профиле",
                    AccessMode.NO_CONFIRMATION.value to "Без подтверждения",
                    AccessMode.PIN_REQUIRED.value to "Только PIN профиля",
                    AccessMode.BIOMETRIC_OR_PIN.value to "Биометрия или PIN"
                )
                val currentAccessLabel: String = accessModeLabels[passwordAccessMode] ?: "Как в профиле"

                ExposedDropdownMenuBox(
                    expanded = expandedAccess,
                    onExpandedChange = { expandedAccess = !expandedAccess }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = currentAccessLabel,
                        onValueChange = {},
                        label = { Text("Режим доступа к паролю") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedAccess) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedAccess,
                        onDismissRequest = { expandedAccess = false }
                    ) {
                        accessModeLabels.forEach { (modeValue, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { passwordAccessMode = modeValue; expandedAccess = false }
                            )
                        }
                    }
                }

                if (formError != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = formError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = {
                        scope.launch {
                            formError = null
                            isSaving = true
                            try {
                                if (service.isBlank()) {
                                    formError = "Сервис обязателен для заполнения"
                                    return@launch
                                }

                                if (!isEditMode && passwordDraft.isNullOrBlank()) {
                                    formError = "Пароль обязателен для новой записи"
                                    return@launch
                                }
                                if (isEditMode && isPasswordChanged && passwordDraft.isNullOrBlank()) {
                                    formError = "Пароль не может быть пустым"
                                    return@launch
                                }

                                val currentOriginalEntry: Entry? = originalEntry
                                val originalAccessMode: String =
                                    currentOriginalEntry?.passwordAccessMode ?: AccessMode.INHERIT.value

                                val isDowngrade: Boolean =
                                    (originalAccessMode == AccessMode.PIN_REQUIRED.value ||
                                            originalAccessMode == AccessMode.BIOMETRIC_OR_PIN.value) &&
                                            passwordAccessMode == AccessMode.NO_CONFIRMATION.value

                                if (isDowngrade) {
                                    formError = "Для отключения защиты требуется подтверждение PIN-кодом профиля."
                                    return@launch
                                }

                                val finalTags: String = processTags(tagsCsv)
                                val targetProfileIdForSave: Int =
                                    currentOriginalEntry?.profileId ?: targetProfileId

                                var encryptedPasswordToSave: String =
                                    currentOriginalEntry?.encryptedPassword.orEmpty()
                                var fingerprintToSave: String =
                                    currentOriginalEntry?.passwordFingerprint.orEmpty()
                                var historyJsonToSave: String =
                                    currentOriginalEntry?.passwordHistoryJson ?: "[]"
                                var lastChangedToSave: Long =
                                    currentOriginalEntry?.lastChanged ?: System.currentTimeMillis()
                                var nextRotationDateToSave: Long? =
                                    currentOriginalEntry?.nextRotationDate
                                val finalGenerationType: String = generationType

                                if (isPasswordChanged && !passwordDraft.isNullOrBlank()) {
                                    val pwd: String = passwordDraft.orEmpty()

                                    val uniqueCheck = PasswordValidator.validateUniqueCharacters(pwd)
                                    if (!uniqueCheck.isValid) {
                                        formError = uniqueCheck.errorMessage
                                        return@launch
                                    }

                                    if (originalPassword != null) {
                                        if (!isPasswordDifferentEnough(originalPassword!!, pwd)) {
                                            formError = "Новый пароль должен отличаться от текущего минимум на 60%"
                                            return@launch
                                        }
                                    }

                                    val newFingerprint: String =
                                        PasswordValidator.buildPasswordFingerprint(pwd, context)
                                    if (currentOriginalEntry != null &&
                                        newFingerprint == currentOriginalEntry.passwordFingerprint
                                    ) {
                                        formError = "Этот пароль уже использовался"
                                        return@launch
                                    }

                                    encryptedPasswordToSave = CryptoUtils.encrypt(pwd)
                                    fingerprintToSave = newFingerprint
                                    lastChangedToSave = System.currentTimeMillis()

                                    if (currentOriginalEntry != null && originalPassword != null) {
                                        val updatedWithHistory = currentOriginalEntry.addToPasswordHistory(
                                            oldPassword = originalPassword!!,
                                            generationType = currentOriginalEntry.generationType,
                                            oldPasswordFingerprint =
                                                currentOriginalEntry.passwordFingerprint.orEmpty()
                                        )
                                        historyJsonToSave = updatedWithHistory.passwordHistoryJson
                                    }
                                }

                                nextRotationDateToSave = if (!rotationEnabled) {
                                    null
                                } else if (currentOriginalEntry == null ||
                                    !currentOriginalEntry.rotationEnabled ||
                                    rotationPeriodMonths != currentOriginalEntry.rotationPeriodMonths ||
                                    isPasswordChanged
                                ) {
                                    System.currentTimeMillis() +
                                            (rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
                                } else {
                                    currentOriginalEntry.nextRotationDate
                                }

                                if (!isEditMode) {
                                    val pwdToSave: String = passwordDraft.orEmpty()

                                    val newEntry = Entry.create(
                                        service = service,
                                        username = username,
                                        password = pwdToSave,
                                        profileId = targetProfileIdForSave,
                                        passwordFingerprint = fingerprintToSave,
                                        url = url.ifBlank { null },
                                        notes = notes.ifBlank { null },
                                        rotationEnabled = rotationEnabled,
                                        rotationPeriodMonths = rotationPeriodMonths,
                                        isFavorite = isFavorite,
                                        generationType = finalGenerationType,
                                        tagsCsv = finalTags,
                                        passwordAccessMode = passwordAccessMode,
                                        textHint = textHint,
                                        mnemonicPhraseHint = mnemonicPhraseHint,
                                        mnemonicOptionsJson = mnemonicOptionsJson
                                    )
                                    viewModel.insert(newEntry) { result ->
                                        when (result) {
                                            is PasswordOperationResult.Success -> onBack()
                                            is PasswordOperationResult.Error -> formError = result.message
                                        }
                                    }
                                } else if (currentOriginalEntry != null) {
                                    val updatedEntry = currentOriginalEntry.copy(
                                        service = service,
                                        username = username,
                                        encryptedPassword = encryptedPasswordToSave,
                                        url = url.ifBlank { null },
                                        notes = notes.ifBlank { null },
                                        tagsCsv = finalTags,
                                        isFavorite = isFavorite,
                                        rotationEnabled = rotationEnabled,
                                        rotationPeriodMonths = rotationPeriodMonths,
                                        passwordHistoryJson = historyJsonToSave,
                                        generationType = finalGenerationType,
                                        passwordFingerprint = fingerprintToSave,
                                        nextRotationDate = nextRotationDateToSave,
                                        passwordAccessMode = passwordAccessMode,
                                        lastChanged = lastChangedToSave,
                                        textHint = textHint,
                                        mnemonicPhraseHint = mnemonicPhraseHint,
                                        mnemonicOptionsJson = mnemonicOptionsJson
                                    )

                                    viewModel.updateEntry(updatedEntry) { result ->
                                        when (result) {
                                            is PasswordOperationResult.Success -> onBack()
                                            is PasswordOperationResult.Error -> formError = result.message
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                formError = "Критическая ошибка сохранения: ${e.message}"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.size(8.dp))
                    } else {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                    }
                    Text("Сохранить")
                }
            }
        }
    }

    if (showAccessDialog) {
        val accessProfileId: Int = originalEntry?.profileId ?: targetProfileId
        val profile = profiles.find { it.id == accessProfileId }
        if (profile != null) {
            ProfileAccessDialog(
                profile = profile,
                allowBiometric = requireBiometricForAccess,
                onDismiss = {
                    showAccessDialog = false
                    pendingAccessAction = null
                },
                onGranted = {
                    showAccessDialog = false
                    pendingAccessAction?.invoke()
                    pendingAccessAction = null
                }
            )
        }
    }

    if (showGeneratorDialog) {
        val accessProfileId: Int = originalEntry?.profileId ?: targetProfileId
        val profile = profiles.find { it.id == accessProfileId }

        UnifiedPasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false },
            onGenerated = { pwd, type, hint, mnemonicHint, mnemonicJson ->
                passwordDraft = pwd
                generationType = type
                textHint = hint
                mnemonicPhraseHint = mnemonicHint
                mnemonicOptionsJson = mnemonicJson
                isPasswordChanged = true
                showGeneratorDialog = false
            },
            initialServiceName = service,
            profile = profile
        )
    }
}
