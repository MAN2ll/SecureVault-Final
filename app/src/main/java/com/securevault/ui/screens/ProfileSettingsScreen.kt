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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки профиля", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
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
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile?.name ?: "—", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Записей: ${entries.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            //  Реальная настройка защиты просмотра паролей
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Защита просмотра паролей", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    
                    var expanded by remember { mutableStateOf(false) }
                    val currentMode = AccessMode.values().find { it.value == profile?.passwordAccessMode } ?: AccessMode.PIN_REQUIRED
                    
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            readOnly = true,
                            value = when (currentMode) {
                                AccessMode.NO_CONFIRMATION -> "Без подтверждения"
                                AccessMode.PIN_REQUIRED -> "PIN профиля"
                                AccessMode.BIOMETRIC_OR_PIN -> "Отпечаток или PIN"
                                AccessMode.PIN_ALWAYS -> "Только PIN"
                                else -> "PIN профиля"
                            },
                            onValueChange = {},
                            label = { Text("Режим защиты") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf(
                                AccessMode.NO_CONFIRMATION to "Без подтверждения",
                                AccessMode.PIN_REQUIRED to "PIN профиля",
                                AccessMode.BIOMETRIC_OR_PIN to "Отпечаток или PIN",
                                AccessMode.PIN_ALWAYS to "Только PIN"
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        // Реальное сохранение настройки
                                        profile?.let {
                                            profileViewModel.updateProfile(it.copy(passwordAccessMode = mode.value))
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        "Определяет, как запрашивать подтверждение при просмотре паролей в этом профиле.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
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

    if (showMasterPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showMasterPasswordDialog = false },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Подтверждение удаления") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Для удаления профиля введите мастер-пароль", fontSize = 13.sp)
                    MasterPasswordInput(
                        context = context,
                        onConfirmed = {
                            showMasterPasswordDialog = false
                            showDeleteProfileDialog = true
                        },
                        onError = { error -> operationError = error }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMasterPasswordDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showDeleteProfileDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProfileDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить профиль?") },
            text = {
                Text(
                    "Профиль \"${profile?.name ?: ""}\" и все его пароли будут удалены безвозвратно.\n\nЭто действие нельзя отменить.",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            },
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
            dismissButton = {
                TextButton(onClick = { showDeleteProfileDialog = false }) { Text("Отмена") }
            }
        )
    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MasterPasswordInput(
    context: Context,
    onConfirmed: () -> Unit,
    onError: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Button(
            onClick = {
                val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val storedHash = prefs.getString("master_hash", null)
                val storedSalt = prefs.getString("master_salt", null)
                val iterations = prefs.getInt("master_iterations", 100_000)

                if (storedHash != null && storedSalt != null &&
                    MasterPasswordHasher.verify(password, storedHash, storedSalt, iterations)) {
                    onConfirmed()
                } else {
                    error = "Неверный мастер-пароль"
                    onError("Неверный мастер-пароль")
                }
                password = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Подтвердить") }
    }
}
