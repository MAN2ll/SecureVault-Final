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
import com.securevault.ui.components.UnifiedPasswordGeneratorDialog
import com.securevault.utils.AccessMode
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordValidator
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
    
    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    
    // Храним зашифрованный пароль отдельно. Не расшифровываем при загрузке!
    var encryptedPasswordState by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var originalPassword by remember { mutableStateOf("") }
    var isPasswordDecrypted by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordChangedManually by remember { mutableStateOf(false) }

    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tagsCsv by remember { mutableStateOf("") }
    
    var isFavorite by remember { mutableStateOf(false) }
    var rotationEnabled by remember { mutableStateOf(false) }
    var rotationPeriodMonths by remember { mutableIntStateOf(6) }
    var passwordAccessMode by remember { mutableStateOf(AccessMode.INHERIT.value) }
    
    var showGeneratorDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val currentProfileIdState by viewModel.currentProfileId.collectAsState()
    val targetProfileId = profileId ?: currentProfileIdState ?: 0

    // Считаем новой, если id == null или id == "new"
    val isEditMode = id != null && id != "new"
    
    // Загрузка через прямой запрос к репозиторию
    LaunchedEffect(id) {
        if (isEditMode) {
            isLoading = true
            val entry = viewModel.getEntryById(id!!)
            if (entry != null) {
                service = entry.service
                username = entry.username
                encryptedPasswordState = entry.encryptedPassword
                url = entry.url ?: ""
                notes = entry.notes ?: ""
                tagsCsv = entry.tagsCsv
                isFavorite = entry.isFavorite
                rotationEnabled = entry.rotationEnabled
                rotationPeriodMonths = entry.rotationPeriodMonths
                passwordAccessMode = entry.passwordAccessMode
            } else {
                errorMessage = "Запись не найдена в базе данных"
            }
            isLoading = false
        } else {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Редактирование" else "Новая запись", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = {
                    // Кнопка блокировки в верхней панели
                    LockActionButton(onLock = { 
                        onLock()
                        // Очищаем открытый пароль из состояния при блокировке
                        password = ""
                        isPasswordDecrypted = false
                        passwordVisible = false
                    })
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { 
                CircularProgressIndicator() 
            }
        } else if (errorMessage != null && isEditMode) {
            // Показываем ошибку и кнопку возврата, если запись не найдена
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
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

                // Поле пароля с отложенной расшифровкой
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        if (isPasswordDecrypted && it != originalPassword) passwordChangedManually = true
                    },
                    label = { Text("Пароль *") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = {
                                if (!isPasswordDecrypted) {
                                    password = try { CryptoUtils.decrypt(encryptedPasswordState) } catch(e: Exception) { "" }
                                    originalPassword = password
                                    isPasswordDecrypted = true
                                    passwordVisible = true
                                } else {
                                    passwordVisible = !passwordVisible
                                }
                            }) { 
                                Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Показать/скрыть") 
                            }
                            IconButton(onClick = { showGeneratorDialog = true }) { Icon(Icons.Default.AutoAwesome, "Сгенерировать") }
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

                var expandedAccess by remember { mutableStateOf(false) }
                val accessModes = AccessMode.values().map { it.value }
                ExposedDropdownMenuBox(expanded = expandedAccess, onExpandedChange = { expandedAccess = !expandedAccess }) {
                    OutlinedTextField(readOnly = true, value = accessModes.firstOrNull { it == passwordAccessMode } ?: AccessMode.INHERIT.value, onValueChange = {}, label = { Text("Режим доступа к паролю") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedAccess) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = expandedAccess, onDismissRequest = { expandedAccess = false }) {
                        accessModes.forEach { mode -> DropdownMenuItem(text = { Text(mode) }, onClick = { passwordAccessMode = mode; expandedAccess = false }) }
                    }
                }

                if (errorMessage != null) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = {
                        scope.launch {
                            errorMessage = null
                            if (service.isBlank() || password.isBlank()) {
                                errorMessage = "Сервис и пароль обязательны для заполнения"
                                return@launch
                            }
                            if (targetProfileId <= 0) {
                                errorMessage = "Ошибка: профиль не выбран"
                                return@launch
                            }

                            if (passwordChangedManually) {
                                val uniqueCheck = PasswordValidator.validateUniqueCharacters(password)
                                if (!uniqueCheck.isValid) {
                                    errorMessage = uniqueCheck.errorMessage
                                    return@launch
                                }
                            }

                            val fingerprint = PasswordValidator.buildPasswordFingerprint(password, context)

                            if (!isEditMode) {
                                // СОЗДАНИЕ НОВОЙ ЗАПИСИ
                                val newEntry = Entry.create(
                                    service = service, username = username, password = password, profileId = targetProfileId,
                                    passwordFingerprint = fingerprint, url = url.ifBlank { null }, notes = notes.ifBlank { null },
                                    rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationPeriodMonths,
                                    isFavorite = isFavorite, generationType = if (passwordChangedManually) "manual" else "random",
                                    tagsCsv = tagsCsv, passwordAccessMode = passwordAccessMode
                                )
                                viewModel.insert(newEntry) { result ->
                                    when (result) {
                                        is PasswordOperationResult.Success -> onBack()
                                        is PasswordOperationResult.Error -> errorMessage = result.message
                                    }
                                }
                            } else {
                                // ОБНОВЛЕНИЕ СУЩЕСТВУЮЩЕЙ ЗАПИСИ
                                val oldEntry = viewModel.getEntryById(id!!) ?: return@launch
                                
                                //  учитываем и ручной ввод, и генерацию
                                val isPasswordChanged = isPasswordDecrypted && (passwordChangedManually || password != originalPassword)

                                var finalEncryptedPassword = oldEntry.encryptedPassword
                                var finalHistoryJson = oldEntry.passwordHistoryJson
                                var finalFingerprint = oldEntry.passwordFingerprint
                                var finalNextRotationDate = oldEntry.nextRotationDate
                                var finalGenerationType = oldEntry.generationType

                                if (isPasswordChanged) {
                                    finalEncryptedPassword = CryptoUtils.encrypt(password)
                                    finalFingerprint = fingerprint
                                    finalGenerationType = if (passwordChangedManually) "manual" else oldEntry.generationType

                                    // Добавляем старый пароль в историю
                                    val updatedWithHistory = oldEntry.addToPasswordHistory(
                                        oldPassword = originalPassword,
                                        generationType = finalGenerationType,
                                        oldPasswordFingerprint = oldEntry.passwordFingerprint ?: ""
                                    )
                                    finalHistoryJson = updatedWithHistory.passwordHistoryJson

                                    // Пересчитываем nextRotationDate
                                    if (rotationEnabled) {
                                        finalNextRotationDate = System.currentTimeMillis() + (rotationPeriodMonths * 30L * 24 * 60 * 60 * 1000)
                                    }
                                }
                                // Если пароль не менялся, все вышеуказанные переменные остаются старыми

                                val updatedEntry = oldEntry.copy(
                                    service = service,
                                    username = username,
                                    encryptedPassword = finalEncryptedPassword,
                                    url = url.ifBlank { null },
                                    notes = notes.ifBlank { null },
                                    tagsCsv = tagsCsv,
                                    isFavorite = isFavorite,
                                    rotationEnabled = rotationEnabled,
                                    rotationPeriodMonths = rotationPeriodMonths,
                                    passwordHistoryJson = finalHistoryJson,
                                    generationType = finalGenerationType,
                                    passwordFingerprint = finalFingerprint,
                                    nextRotationDate = finalNextRotationDate,
                                    passwordAccessMode = passwordAccessMode,
                                    lastChanged = System.currentTimeMillis()
                                )
                                
                                viewModel.updateEntry(updatedEntry) { result ->
                                    when (result) {
                                        is PasswordOperationResult.Success -> onBack()
                                        is PasswordOperationResult.Error -> errorMessage = result.message
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Сохранить")
                }
            }
        }
    }

    if (showGeneratorDialog) {
        UnifiedPasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false },
            onGenerated = { pwd, _, _ ->
                password = pwd
                //  Не перезаписываем originalPassword, чтобы система поняла, что пароль изменился
                isPasswordDecrypted = true
                passwordChangedManually = true
                showGeneratorDialog = false
            },
            initialServiceName = service
        )
    }
}
