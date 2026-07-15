@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.security.MasterPasswordHasher
import com.securevault.utils.AccessMode
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    profileId: Int?,
    onBack: () -> Unit,
    onNavigateToRotation: () -> Unit,
    onNavigateToRotationJournal: () -> Unit,
    onNavigateToAudit: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToQrScanner: () -> Unit,
    vaultViewModel: VaultViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(profileId) {
        if (profileId != null) {
            vaultViewModel.setCurrentProfile(profileId)
        }
    }

    val profiles by profileViewModel.profiles.collectAsState()
    val entries by vaultViewModel.entries.collectAsState()
    val profile = remember(profileId, profiles) { profiles.find { it.id == profileId } }

    var showDeleteProfileDialog by remember { mutableStateOf(false) }
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var operationSuccess by remember { mutableStateOf<String?>(null) }

    //  Убран лишний '0'
    var masterPasswordError by remember { mutableStateOf<String?>(null) }
    var masterPasswordInput by remember { mutableStateOf("") }

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showRemovePinDialog by remember { mutableStateOf(false) }
    var showMasterPasswordForRemoveDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var masterPasswordForRemove by remember { mutableStateOf("") }
    var showSetPinPrompt by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки профиля", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Профиль", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile?.name ?: "—", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Записей: ${entries.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            //  УПРАВЛЕНИЕ PIN ПРОФИЛЯ
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PIN профиля", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    val hasPin = !profile?.passwordHash.isNullOrBlank()
                    Text(if (hasPin) "Задан" else "Не задан", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { newPin = ""; confirmNewPin = ""; pinError = null; showSetPinDialog = true },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (hasPin) "Изменить PIN" else "Задать PIN") }
                        if (hasPin) {
                            OutlinedButton(
                                onClick = { masterPasswordForRemove = ""; showMasterPasswordForRemoveDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Удалить PIN") }
                        }
                    }
                }
            }

            //  НАСТРОЙКА ЗАЩИТЫ ПРОСМОТРА
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Защита просмотра паролей", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    
                    var expanded by remember { mutableStateOf(false) }
                    val currentMode = AccessMode.values().find { it.value == profile?.passwordAccessMode } ?: AccessMode.PIN_REQUIRED
                    val hasPin = !profile?.passwordHash.isNullOrBlank()
                    
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            readOnly = true,
                            //  Убран дубликат, оставлен только один "Только PIN профиля"
                            value = when (currentMode) {
                                AccessMode.NO_CONFIRMATION -> "Без подтверждения"
                                AccessMode.PIN_REQUIRED -> "Только PIN профиля"
                                AccessMode.BIOMETRIC_OR_PIN -> "Отпечаток или PIN профиля"
                                else -> "Только PIN профиля"
                            },
                            onValueChange = {},
                            label = { Text("Режим защиты") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf(
                                AccessMode.NO_CONFIRMATION to "Без подтверждения",
                                AccessMode.BIOMETRIC_OR_PIN to "Отпечаток или PIN профиля",
                                AccessMode.PIN_REQUIRED to "Только PIN профиля" //  Убран PIN_ALWAYS из UI
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        if ((mode == AccessMode.PIN_REQUIRED || mode == AccessMode.BIOMETRIC_OR_PIN) && !hasPin) {
                                            showSetPinPrompt = true
                                        } else {
                                            profile?.let {
                                                profileViewModel.updateProfile(it.copy(passwordAccessMode = mode.value))
                                            }
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    if (!hasPin && (currentMode == AccessMode.PIN_REQUIRED || currentMode == AccessMode.BIOMETRIC_OR_PIN)) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Сначала задайте PIN профиля", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { showSetPinDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Задать PIN профиля")
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Действия с паролями", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            SettingsActionCard(icon = Icons.Default.Schedule, title = "Ротация паролей", subtitle = "Обновление просроченных паролей", onClick = onNavigateToRotation)
            SettingsActionCard(icon = Icons.Default.History, title = "Журнал ротации", subtitle = "История всех изменений паролей", onClick = onNavigateToRotationJournal)
            SettingsActionCard(icon = Icons.Default.Security, title = "Аудит безопасности", subtitle = "Проверка качества паролей", onClick = onNavigateToAudit)
            SettingsActionCard(icon = Icons.Default.Upload, title = "Экспорт / импорт", subtitle = "Резервное копирование записей", onClick = onNavigateToExport)
            SettingsActionCard(icon = Icons.Default.QrCodeScanner, title = "Сканировать QR", subtitle = "Получить пароль по QR-коду", onClick = onNavigateToQrScanner)

            HorizontalDivider()
            Text("Опасная зона", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Удалить профиль", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Профиль и все его пароли будут удалены безвозвратно.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                    Button(
                        onClick = { showMasterPasswordDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Удалить профиль")
                    }
                }
            }
        }
    }

    // Реальные диалоги

    // 1. Диалог удаления профиля (требует мастер-пароль)
    if (showMasterPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showMasterPasswordDialog = false },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Подтверждение удаления") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Для удаления профиля введите мастер-пароль", fontSize = 13.sp)
                    OutlinedTextField(
                        value = masterPasswordInput,
                        onValueChange = { masterPasswordInput = it; masterPasswordError = null },
                        label = { Text("Мастер-пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = masterPasswordError != null
                    )
                    if (masterPasswordError != null) Text(masterPasswordError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    val storedHash = prefs.getString("master_hash", null)
                    val storedSalt = prefs.getString("master_salt", null)
                    val iterations = prefs.getInt("master_iterations", 100_000)

                    if (storedHash != null && storedSalt != null &&
                        MasterPasswordHasher.verify(masterPasswordInput, storedHash, storedSalt, iterations)) {
                        showMasterPasswordDialog = false
                        masterPasswordInput = ""
                        showDeleteProfileDialog = true
                    } else {
                        masterPasswordError = "Неверный мастер-пароль"
                    }
                }) { Text("Подтвердить") }
            },
            dismissButton = { TextButton(onClick = { showMasterPasswordDialog = false }) { Text("Отмена") } }
        )
    }

    if (showDeleteProfileDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProfileDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить профиль?") },
            text = { Text("Профиль \"${profile?.name ?: ""}\" и все его пароли будут удалены безвозвратно.", color = MaterialTheme.colorScheme.onErrorContainer) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteProfileDialog = false
                        if (profileId != null) {
                            profileViewModel.deleteProfile(profileId) { result ->
                                when (result) {
                                    is PasswordOperationResult.Success -> {
                                        operationSuccess = "Профиль удалён"
                                        onBack()
                                    }
                                    is PasswordOperationResult.Error -> operationError = result.message
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showDeleteProfileDialog = false }) { Text("Отмена") } }
        )
    }

    // 2. Диалог задания/изменения PIN профиля
    if (showSetPinDialog && profile != null) {
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text(if (!profile.passwordHash.isNullOrBlank()) "Изменить PIN профиля" else "Задать PIN профиля") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it; pinError = null },
                        label = { Text("Новый PIN профиля (4-8 цифр)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = pinError != null
                    )
                    OutlinedTextField(
                        value = confirmNewPin,
                        onValueChange = { confirmNewPin = it; pinError = null },
                        label = { Text("Подтвердите PIN профиля") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = pinError != null
                    )
                    if (pinError != null) Text(pinError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPin.length !in 4..8) {
                        pinError = "PIN профиля должен содержать от 4 до 8 цифр"
                        return@Button
                    }
                    if (newPin != confirmNewPin) {
                        pinError = "PIN профиля не совпадает"
                        return@Button
                    }
                    profileViewModel.setProfilePin(profile, newPin) { result ->
                        if (result is PasswordOperationResult.Success) {
                            showSetPinDialog = false
                            operationSuccess = "PIN профиля успешно обновлён"
                        } else {
                            pinError = (result as PasswordOperationResult.Error).message
                        }
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showSetPinDialog = false }) { Text("Отмена") } }
        )
    }

    // 3. Диалог подтверждения мастер-паролем для удаления PIN
    if (showMasterPasswordForRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showMasterPasswordForRemoveDialog = false },
            title = { Text("Подтверждение") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Для удаления PIN профиля введите мастер-пароль", fontSize = 13.sp)
                    OutlinedTextField(
                        value = masterPasswordForRemove,
                        onValueChange = { masterPasswordForRemove = it; masterPasswordError = null },
                        label = { Text("Мастер-пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = masterPasswordError != null
                    )
                    if (masterPasswordError != null) Text(masterPasswordError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    val storedHash = prefs.getString("master_hash", null)
                    val storedSalt = prefs.getString("master_salt", null)
                    val iterations = prefs.getInt("master_iterations", 100_000)

                    if (storedHash != null && storedSalt != null &&
                        MasterPasswordHasher.verify(masterPasswordForRemove, storedHash, storedSalt, iterations)) {
                        showMasterPasswordForRemoveDialog = false
                        if (profile != null) {
                            profileViewModel.removeProfilePin(profile) { result ->
                                if (result is PasswordOperationResult.Success) {
                                    operationSuccess = "PIN профиля удалён"
                                } else {
                                    operationError = (result as PasswordOperationResult.Error).message
                                }
                            }
                        }
                    } else {
                        masterPasswordError = "Неверный мастер-пароль"
                    }
                }) { Text("Подтвердить") }
            },
            dismissButton = { TextButton(onClick = { showMasterPasswordForRemoveDialog = false }) { Text("Отмена") } }
        )
    }

    // 4. Диалог предупреждения о необходимости задать PIN
    if (showSetPinPrompt) {
        AlertDialog(
            onDismissRequest = { showSetPinPrompt = false },
            title = { Text("PIN профиля не задан") },
            text = { Text("Для использования этого режима защиты сначала необходимо задать PIN профиля.") },
            confirmButton = {
                Button(onClick = { 
                    showSetPinPrompt = false
                    showSetPinDialog = true
                }) { Text("Задать PIN") }
            },
            dismissButton = { TextButton(onClick = { showSetPinPrompt = false }) { Text("Отмена") } }
        )
    }

    // 5. Диалоги ошибок и успеха
    if (operationError != null) {
        AlertDialog(
            onDismissRequest = { operationError = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ошибка") },
            text = { Text(operationError ?: "") },
            confirmButton = { TextButton(onClick = { operationError = null }) { Text("Понятно") } }
        )
    }

    if (operationSuccess != null) {
        AlertDialog(
            onDismissRequest = { operationSuccess = null },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Успешно") },
            text = { Text(operationSuccess ?: "") },
            confirmButton = { TextButton(onClick = { operationSuccess = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun SettingsActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
