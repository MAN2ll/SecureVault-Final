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
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordValidator
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    id: String? = null, // Изменено с entryId на id для совместимости с NavHost
    profileId: Int?, //  Изменено на Int? для совместимости с NavHost
    onBack: () -> Unit,
    onLock: () -> Unit = {}, //  Добавлено для совместимости с NavHost
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
    
    var passwordChangedManually by remember { mutableStateOf(false) }
    var originalPassword by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(id != null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    //  Безопасное получение Int, если profileId вдруг null
    val currentProfileId = profileId ?: 0

    LaunchedEffect(id) {
        if (id != null) {
            val entry = viewModel.findEntryById(id)
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
            }
            isLoading = false
        } else {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "Новая запись" else "Редактирование", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                    value = password,
                    onValueChange = { 
                        password = it
                        if (it != originalPassword) {
                            passwordChangedManually = true
                        }
                    },
                    label = { Text("Пароль *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    "Пароль можно ввести вручную или поправить после генерации",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp)
                )

                OutlinedTextField(
                    value = tagsCsv,
                    onValueChange = { tagsCsv = it },
                    label = { Text("Теги") },
                    supportingText = {
                        Text("Через запятую, например: работа, почта, финансы")
                    },
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
                    modifier = Modifier.fillMaxWidth().height(120.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Период ротации (месяцев):", modifier = Modifier.weight(1f))
                        Text("$rotationPeriodMonths мес.", fontWeight = FontWeight.Bold)
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = {
                        if (service.isBlank() || password.isBlank()) {
                            errorMessage = "Сервис и пароль обязательны для заполнения"
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

                        if (id == null) {
                            val newEntry = Entry.create(
                                service = service,
                                username = username,
                                password = password,
                                profileId = currentProfileId,
                                passwordFingerprint = fingerprint,
                                url = url.ifBlank { null },
                                notes = notes.ifBlank { null },
                                rotationEnabled = rotationEnabled,
                                rotationPeriodMonths = rotationPeriodMonths,
                                isFavorite = isFavorite,
                                generationType = if (passwordChangedManually) "manual" else "random",
                                tagsCsv = tagsCsv
                            )
                            viewModel.insert(newEntry) { result ->
                                //  Используем when для корректного smart-cast и доступа к .message
                                when (result) {
                                    is PasswordOperationResult.Success -> onBack()
                                    is PasswordOperationResult.Error -> errorMessage = result.message
                                }
                            }
                        } else {
                            val oldEntry = viewModel.findEntryById(id) ?: return@Button
                            
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
                                service = service,
                                username = username,
                                encryptedPassword = CryptoUtils.encrypt(password),
                                url = url.ifBlank { null },
                                notes = notes.ifBlank { null },
                                tagsCsv = tagsCsv,
                                isFavorite = isFavorite,
                                rotationEnabled = rotationEnabled,
                                rotationPeriodMonths = rotationPeriodMonths,
                                passwordHistoryJson = finalHistoryJson,
                                generationType = if (passwordChangedManually) "manual" else oldEntry.generationType,
                                passwordFingerprint = fingerprint,
                                lastChanged = System.currentTimeMillis()
                            )
                            
                            viewModel.updateEntry(updatedEntry) { result ->
                                //  Используем when для корректного smart-cast и доступа к .message
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
}
