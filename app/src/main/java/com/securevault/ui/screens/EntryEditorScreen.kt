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
    viewModel: VaultViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    //  РАЗДЕЛЕНИЕ СОСТОЯНИЙ
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
    
    // Состояния пароля
    var passwordDraft by remember { mutableStateOf("") }
    var passwordChanged by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var decryptedOriginalPassword by remember { mutableStateOf<String?>(null) }
    
    var generationType by remember { mutableStateOf("random") }
    var textHint by remember { mutableStateOf<String?>(null) }
    var mnemonicPhraseHint by remember { mutableStateOf<String?>(null) }
    var mnemonicOptionsJson by remember { mutableStateOf<String?>(null) }
    
    var showGeneratorDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Состояния для диалога доступа
    var showAccessDialog by remember { mutableStateOf(false) }
    var requireBiometricForAccess by remember { mutableStateOf(false) }

    val currentProfileIdState by viewModel.currentProfileId.collectAsState()
    val targetProfileId = profileId ?: currentProfileIdState ?: 0
    val currentProfile by viewModel.currentProfile.collectAsState()

    val isEditMode = id != null && id != "new"

    //  ПРЯМАЯ ЗАГРУЗКА ЗАПИСИ
    LaunchedEffect(id) {
        if (isEditMode) {
            isLoading = true
            val entry = viewModel.getEntryById(id!!)
            if (entry != null) {
                service = entry.service
                username = entry.username
                url = entry.url ?: ""
                notes = entry.notes ?: ""
                tagsCsv = entry.tagsCsv
                isFavorite = entry.isFavorite
                rotationEnabled = entry.rotationEnabled
                rotationPeriodMonths = entry.rotationPeriodMonths
                passwordAccessMode = entry.passwordAccessMode
                generationType = entry.generationType
                textHint = entry.textHint
                mnemonicPhraseHint = entry.mnemonicPhraseHint
                mnemonicOptionsJson = entry.mnemonicOptionsJson
                
                // Пароль НЕ расшифровываем здесь!
                decryptedOriginalPassword = null
                passwordDraft = "" 
            } else {
                loadError = "Запись не найдена в базе данных"
            }
            isLoading = false
        } else {
            isLoading = false
        }
    }

    //  ОБРАБОТКА ТЕГОВ
    fun processTags(input: String): String {
        return input.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(",")
    }

    //  ЗАПРОС ДОСТУПА К ПАРОЛЮ
    fun requestPasswordAccess() {
        formError = null
        if (currentProfile == null) {
            formError = "Ошибка: профиль не загружен"
            return
        }
        
        val result = resolveAccess(
            Entry(
                id = id ?: "", service = service, username = username, 
                encryptedPassword = "", profileId = targetProfileId,
                passwordAccessMode = passwordAccessMode
            ), 
            currentProfile!!
        )
        
        when (result) {
            is AccessResult.Granted -> {
                decryptAndEnablePassword()
            }
            is AccessResult.PinRequired -> {
                requireBiometricForAccess = false
                showAccessDialog = true
            }
            is AccessResult.BiometricOrPin -> {
                requireBiometricForAccess = true
                showAccessDialog = true
            }
            is AccessResult.PinNotSet -> {
                formError = "Для редактирования пароля необходимо задать PIN в настройках профиля."
            }
        }
    }

    fun decryptAndEnablePassword() {
        scope.launch {
            try {
                // Загружаем актуальную запись для расшифровки
                val entry = viewModel.getEntryById(id!!)
                if (entry != null) {
                    val decrypted = CryptoUtils.decrypt(entry.encryptedPassword)
                    decryptedOriginalPassword = decrypted
                    passwordDraft = decrypted
                    passwordVisible = true
                    // При первом раскрытии не считаем это изменением, пока пользователь не начнёт печатать
                }
            } catch (e: Exception) {
                formError = "Ошибка расшифровки: ${e.message}"
            }
        }
    }

    //  ПРОВЕРКА ПОХОЖЕСТВА ПАРОЛЯ (минимум 60% отличий)
    fun isPasswordDifferentEnough(oldPwd: String, newPwd: String): Boolean {
        if (oldPwd == newPwd) return false
        val maxLength = maxOf(oldPwd.length, newPwd.length)
        if (maxLength == 0) return true
        
        var matches = 0
        val minLen = minOf(oldPwd.length, newPwd.length)
        for (i in 0 until minLen) {
            if (oldPwd[i] == newPwd[i]) matches++
        }
        val similarity = matches.toDouble() / maxLength
        return similarity <= 0.4 // То есть отличий >= 60%
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Редактирование" else "Новая запись", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = {
                    LockActionButton(onLock = { 
                        onLock()
                        passwordDraft = ""
                        decryptedOriginalPassword = null
                        passwordVisible = false
                        passwordChanged = false
                    })
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { 
                CircularProgressIndicator() 
            }
        } else if (loadError != null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loadError!!, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Вернуться назад") }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(value = service, onValueChange = { service = it }, label = { Text("Сервис *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Логин / Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                //  ПОЛЕ ПАРОЛЯ С ОТЛОЖЕННОЙ РАСШИФРОВКОЙ
                OutlinedTextField(
                    value = passwordDraft,
                    onValueChange = { newPwd ->
                        passwordDraft = newPwd
                        if (!isEditMode) {
                            // Для новой записи ручной ввод сразу устанавливает флаги
                            passwordChanged = true
                            generationType = "manual"
                        } else {
                            // Для существующей записи проверяем, отличается ли от оригинала
                            if (decryptedOriginalPassword != null) {
                                passwordChanged = (newPwd != decryptedOriginalPassword)
                            }
                        }
                    },
                    label = { Text("Пароль *") },
                    singleLine = true,
                    readOnly = isEditMode && decryptedOriginalPassword == null, // Нельзя редактировать, пока не подтверждён доступ
                    placeholder = { 
                        if (isEditMode && decryptedOriginalPassword == null) {
                            Text("••••••••••••", fontSize = 16.sp) 
                        }
                    },
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            if (isEditMode && decryptedOriginalPassword == null) {
                                IconButton(onClick = { requestPasswordAccess() }) { 
                                    Icon(Icons.Default.Visibility, "Показать/изменить пароль") 
                                }
                            } else {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) { 
                                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Показать/скрыть") 
                                }
                            }
                            IconButton(onClick = { showGeneratorDialog = true }) { 
                                Icon(Icons.Default.AutoAwesome, "Сгенерировать") 
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("Пароль можно ввести вручную или сгенерировать", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))

                OutlinedTextField(value = tagsCsv, onValueChange = { tagsCsv = it }, label = { Text("Теги") }, supportingText = { Text("Через запятую: работа, почта") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL сайта") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Заметки") }, modifier = Modifier.fillMaxWidth().height(100.dp))

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
                    ExposedDropdownMenuBox(expanded = expandedRotation, onExpandedChange = { expandedRotation = !expandedRotation }) {
                        OutlinedTextField(readOnly = true, value = "$rotationPeriodMonths мес.", onValueChange = {}, label = { Text("Период ротации") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedRotation) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = expandedRotation, onDismissRequest = { expandedRotation = false }) {
                            periods.forEach { period -> DropdownMenuItem(text = { Text("$period мес.") }, onClick = { rotationPeriodMonths = period; expandedRotation = false }) }
                        }
                    }
                }

                //  ВЫПАДАЮЩИЙ СПИСОК С РУССКИМИ ПОДПИСЯМИ
                var expandedAccess by remember { mutableStateOf(false) }
                val accessModeLabels = mapOf(
                    AccessMode.INHERIT.value to "Как в профиле",
                    AccessMode.NO_CONFIRMATION.value to "Без подтверждения",
                    AccessMode.PIN_REQUIRED.value to "Только PIN профиля",
                    AccessMode.BIOMETRIC_OR_PIN.value to "Биометрия или PIN"
                )
                val currentAccessLabel = accessModeLabels[passwordAccessMode] ?: "Как в профиле"
                
                ExposedDropdownMenuBox(expanded = expandedAccess, onExpandedChange = { expandedAccess = !expandedAccess }) {
                    OutlinedTextField(
                        readOnly = true, 
                        value = currentAccessLabel, 
                        onValueChange = {}, 
                        label = { Text("Режим доступа к паролю") }, 
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedAccess) }, 
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expandedAccess, onDismissRequest = { expandedAccess = false }) {
                        accessModeLabels.forEach { (modeValue, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { passwordAccessMode = modeValue; expandedAccess = false })
                        }
                    }
                }

                // ОШИБКА ФОРМЫ НАД КНОПКОЙ (не заменяет весь экран)
                if (formError != null) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(text = formError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }

                Spacer(Modifier.weight(1f))

                //  КНОПКА СОХРАНЕНИЯ С ЗАЩИТОЙ isSaving
                Button(
                    onClick = {
                        scope.launch {
                            formError = null
                            isSaving = true
                            
                            if (service.isBlank() || passwordDraft.isBlank()) {
                                formError = "Сервис и пароль обязательны для заполнения"
                                isSaving = false
                                return@launch
                            }
                            if (targetProfileId <= 0) {
                                formError = "Ошибка: профиль не выбран"
                                isSaving = false
                                return@launch
                            }

                            val finalTags = processTags(tagsCsv)
                            val fingerprint = PasswordValidator.buildPasswordFingerprint(passwordDraft, context)

                            if (!isEditMode) {
                                // === СОЗДАНИЕ НОВОЙ ЗАПИСИ ===
                                val newEntry = Entry.create(
                                    service = service, username = username, password = passwordDraft, profileId = targetProfileId,
                                    passwordFingerprint = fingerprint, url = url.ifBlank { null }, notes = notes.ifBlank { null },
                                    rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationPeriodMonths,
                                    isFavorite = isFavorite, generationType = generationType,
                                    tagsCsv = finalTags, passwordAccessMode = passwordAccessMode,
                                    textHint = textHint, mnemonicPhraseHint = mnemonicPhraseHint, mnemonicOptionsJson = mnemonicOptionsJson
                                )
                                viewModel.insert(newEntry) { result ->
                                    when (result) {
                                        is PasswordOperationResult.Success -> onBack()
                                        is PasswordOperationResult.Error -> { formError = result.message; isSaving = false }
                                    }
                                }
                            } else {
                                // === ОБНОВЛЕНИЕ СУЩЕСТВУЮЩЕЙ ЗАПИСИ ===
                                val oldEntry = viewModel.getEntryById(id!!) ?: run {
                                    formError = "Запись не найдена"
                                    isSaving = false
                                    return@launch
                                }

                                var finalEncryptedPassword = oldEntry.encryptedPassword
                                var finalHistoryJson = oldEntry.passwordHistoryJson
                                var finalFingerprint = oldEntry.passwordFingerprint
                                var finalGenerationType = oldEntry.generationType
                                var finalLastChanged = oldEntry.lastChanged
                                var finalNextRotationDate = oldEntry.nextRotationDate

                                if (passwordChanged && decryptedOriginalPassword != null) {
                                    // 1. Проверка отсутствия повторяющихся символов
                                    val uniqueCheck = PasswordValidator.validateUniqueCharacters(passwordDraft)
                                    if (!uniqueCheck.isValid) {
                                        formError = uniqueCheck.errorMessage
                                        isSaving = false
                                        return@launch
                                    }

                                    // 2. Проверка отличия от текущего пароля минимум на 60%
                                    if (!isPasswordDifferentEnough(decryptedOriginalPassword!!, passwordDraft)) {
                                        formError = "Новый пароль должен отличаться от текущего минимум на 60%"
                                        isSaving = false
                                        return@launch
                                    }

                                    // 3. Проверка отсутствия в истории (упрощённая: сравниваем с расшифрованными, если нужно, или полагаемся на fingerprint)
                                    // Для надёжности проверяем, не совпадает ли fingerprint с текущим
                                    if (fingerprint == oldEntry.passwordFingerprint) {
                                        formError = "Этот пароль уже использовался"
                                        isSaving = false
                                        return@launch
                                    }

                                    // Шифруем новый пароль
                                    finalEncryptedPassword = CryptoUtils.encrypt(passwordDraft)
                                    finalFingerprint = fingerprint
                                    
                                    // Если пароль сгенерирован, тип обновляется в callback генератора. Если вручную - "manual"
                                    // (generationType уже обновлён при вводе или выборе генератора)
                                    finalGenerationType = generationType

                                    // Добавляем предыдущий пароль в историю с указанием СТАРОГО generationType
                                    val updatedWithHistory = oldEntry.addToPasswordHistory(
                                        oldPassword = decryptedOriginalPassword!!,
                                        generationType = oldEntry.generationType, // Старый тип
                                        oldPasswordFingerprint = oldEntry.passwordFingerprint ?: ""
                                    )
                                    finalHistoryJson = updatedWithHistory.passwordHistoryJson
                                    
                                    // Обновляем lastChanged
                                    finalLastChanged = System.currentTimeMillis()
                                }
                                // Если пароль не изменялся, все переменные выше остаются старыми (включая lastChanged)

                                //  РАСЧЁТ nextRotationDate
                                finalNextRotationDate = if (!rotationEnabled) {
                                    null
                                } else if (!oldEntry.rotationEnabled || rotationPeriodMonths != oldEntry.rotationPeriodMonths || passwordChanged) {
                                    // Ротация включена впервые, изменён период или изменён пароль
                                    System.currentTimeMillis() + (rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
                                } else {
                                    // Остальные случаи — сохраняем прежнюю дату
                                    oldEntry.nextRotationDate
                                }

                                val updatedEntry = oldEntry.copy(
                                    service = service,
                                    username = username,
                                    encryptedPassword = finalEncryptedPassword,
                                    url = url.ifBlank { null },
                                    notes = notes.ifBlank { null },
                                    tagsCsv = finalTags,
                                    isFavorite = isFavorite,
                                    rotationEnabled = rotationEnabled,
                                    rotationPeriodMonths = rotationPeriodMonths,
                                    passwordHistoryJson = finalHistoryJson,
                                    generationType = finalGenerationType,
                                    passwordFingerprint = finalFingerprint,
                                    nextRotationDate = finalNextRotationDate,
                                    passwordAccessMode = passwordAccessMode,
                                    lastChanged = finalLastChanged,
                                    textHint = textHint,
                                    mnemonicPhraseHint = mnemonicPhraseHint,
                                    mnemonicOptionsJson = mnemonicOptionsJson
                                )
                                
                                viewModel.updateEntry(updatedEntry) { result ->
                                    when (result) {
                                        is PasswordOperationResult.Success -> onBack()
                                        is PasswordOperationResult.Error -> { formError = result.message; isSaving = false }
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving //  Отключаем кнопку во время сохранения
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
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

    //  ДИАЛОГ ДОСТУПА (PIN / Биометрия)
    if (showAccessDialog && currentProfile != null) {
        ProfileAccessDialog(
            profile = currentProfile!!,
            requireBiometric = requireBiometricForAccess,
            onDismiss = { showAccessDialog = false },
            onGranted = {
                showAccessDialog = false
                decryptAndEnablePassword()
            }
        )
    }

    //  ДИАЛОГ ГЕНЕРАТОРА
    if (showGeneratorDialog) {
        UnifiedPasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false },
            onGenerated = { pwd, hint, type ->
                passwordDraft = pwd
                generationType = type //  Сохраняем настоящий тип (например, "ampg_v1", "random")
                textHint = hint
                passwordChanged = true
                showGeneratorDialog = false
            },
            initialServiceName = service
        )
    }
}
