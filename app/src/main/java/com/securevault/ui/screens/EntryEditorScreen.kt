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
import com.securevault.ui.components.UnifiedPasswordGeneratorDialog
import com.securevault.utils.AccessMode
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordValidator
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    id: String? = null, // Может быть null, "new" или реальный UUID
    profileId: Int?,
    onBack: () -> Unit,
    onLock: () -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tagsCsv by remember { mutableStateOf("") }
    
    var isFavorite by remember { mutableStateOf(false) }
    var rotationEnabled by remember { mutableStateOf(false) }
    var rotationPeriodMonths by remember { mutableIntStateOf(6) }
    var passwordAccessMode by remember { mutableStateOf(AccessMode.INHERIT.value) }
    
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordChangedManually by remember { mutableStateOf(false) }
    var originalPassword by remember { mutableStateOf("") }

    var showGeneratorDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    //  Гарантированно получаем валидный profileId
    val currentProfileIdState by viewModel.currentProfileId.collectAsState()
    val targetProfileId = profileId ?: currentProfileIdState ?: 0

    //  Исправлено: загружаем данные только если это реальный ID (не null и не "new")
    val isEditMode = id != null && id != "new"
    
    LaunchedEffect(id) {
        if (isEditMode) {
            isLoading = true
            val entry = viewModel.findEntryById(id!!)
            if (entry != null) {
                service = entry.service
                username = entry.username
                password = entry.password
                originalPassword = password
                url = entry.url ?: ""
                notes = entry.notes ?: ""
                tagsCsv = entry.tagsCsv
                isFavorite = entry.isFavorite
                rotationEnabled = entry.rotationEnabled
                rotationPeriodMonths = entry.rotationPeriodMonths
                passwordAccessMode = entry.passwordAccessMode
            } else {
                errorMessage = "Запись не найдена"
            }
            isLoading = false
        } else {
            // Режим создания: поля уже пусты по умолчанию, isLoading = false
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Редактирование" else "Новая запись", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { 
                CircularProgressIndicator() 
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(value = service, onValueChange = { service = it }, label = { Text("Сервис *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Логин / Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        if (it != originalPassword) passwordChangedManually = true
                    },
                    label = { Text("Пароль *") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Показать/скрыть") }
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
                        errorMessage = null
                        if (service.isBlank() || password.isBlank()) {
                            errorMessage = "Сервис и пароль обязательны для заполнения"
                            return@Button
                        }
                        if (targetProfileId <= 0) {
                            errorMessage = "Ошибка: профиль не выбран. Вернитесь к списку профилей."
                            return@Button
                        }

                        if (passwordChangedManually) {
                            val uniqueCheck = PasswordValidator.validateUniqueCharacters(password)
                            if (!uniqueCheck.isValid) {
                                errorMessage = uniqueCheck.errorMessage
                                return@Button
                            }
                        }

                        val fingerprint = PasswordValidator.buildPasswordFingerprint(password, context)

                        if (!isEditMode) {
                            //  СОЗДАНИЕ НОВОЙ ЗАПИСИ
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
                            //  ОБНОВЛЕНИЕ СУЩЕСТВУЮЩЕЙ ЗАПИСИ
                            val oldEntry = viewModel.findEntryById(id!!) ?: return@Button
                            var finalHistoryJson = oldEntry.passwordHistoryJson
                            if (password != oldEntry.password) {
                                val updatedEntryWithHistory = oldEntry.addToPasswordHistory(
                                    oldPassword = oldEntry.password,
                                    generationType = if (passwordChangedManually) "manual" else oldEntry.generationType,
                                    oldPasswordFingerprint = oldEntry.passwordFingerprint ?: ""
                                )
                                finalHistoryJson = updatedEntryWithHistory.passwordHistoryJson
                            }

                            val updatedEntry = oldEntry.copy(
                                service = service, username = username,
                                encryptedPassword = CryptoUtils.encrypt(password),
                                url = url.ifBlank { null }, notes = notes.ifBlank { null },
                                tagsCsv = tagsCsv, isFavorite = isFavorite,
                                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationPeriodMonths,
                                passwordHistoryJson = finalHistoryJson,
                                generationType = if (passwordChangedManually) "manual" else oldEntry.generationType,
                                passwordFingerprint = fingerprint,
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
                passwordChangedManually = true
                showGeneratorDialog = false
            },
            initialServiceName = service
        )
    }
}
