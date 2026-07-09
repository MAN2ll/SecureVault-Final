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

    //  Состояния для удаления всех паролей
    var showDeleteAllDialog by remember { mutableStateOf(false) }
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

            HorizontalDivider()

            Text("Действия с паролями", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            SettingsActionCard(
                icon = Icons.Default.Schedule,
                title = "Ротация паролей",
                subtitle = "Обновление просроченных паролей",
                onClick = onNavigateToRotation
            )

            SettingsActionCard(
                icon = Icons.Default.History,
                title = "Журнал ротации",
                subtitle = "История всех изменений паролей",
                onClick = onNavigateToRotationJournal
            )

            SettingsActionCard(
                icon = Icons.Default.Security,
                title = "Аудит безопасности",
                subtitle = "Проверка качества паролей",
                onClick = onNavigateToAudit
            )

            SettingsActionCard(
                icon = Icons.Default.Upload,
                title = "Экспорт / импорт",
                subtitle = "Резервное копирование записей",
                onClick = onNavigateToExport
            )

            SettingsActionCard(
                icon = Icons.Default.QrCodeScanner,
                title = "Сканировать QR",
                subtitle = "Получить пароль по QR-коду",
                onClick = onNavigateToQrScanner
            )

            HorizontalDivider()

            //  Опасная зона с рабочей кнопкой
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
                            Text("Удалить все пароли", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Все записи профиля будут удалены безвозвратно. Профиль останется.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }

                    Button(
                        onClick = { showMasterPasswordDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = entries.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Удалить все пароли профиля (${entries.size})")
                    }
                }
            }
        }
    }

    // Диалог мастер-пароля
    if (showMasterPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showMasterPasswordDialog = false },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Подтверждение удаления") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Для удаления всех паролей введите мастер-пароль", fontSize = 13.sp)
                    MasterPasswordInput(
                        context = context,
                        onConfirmed = {
                            showMasterPasswordDialog = false
                            showDeleteAllDialog = true
                        },
                        onError = { error ->
                            operationError = error
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMasterPasswordDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    //  Подтверждение удаления
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить все пароли профиля?") },
            text = {
                Text(
                    "Все пароли профиля \"${profile?.name ?: ""}\" будут удалены безвозвратно.\n\n" +
                    "Это действие нельзя отменить.",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        val currentProfileId = vaultViewModel.currentProfileId.value
                        if (currentProfileId == null) {
                            operationError = "Профиль не выбран"
                            return@Button
                        }
                        vaultViewModel.deleteAllEntriesInProfile(currentProfileId) { result ->
                            when (result) {
                                is PasswordOperationResult.Success -> {
                                    operationSuccess = "Все пароли профиля удалены"
                                }
                                is PasswordOperationResult.Error -> {
                                    operationError = result.message
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить всё")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // : Диалог ошибок
    if (operationError != null) {
        AlertDialog(
            onDismissRequest = { operationError = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ошибка") },
            text = { Text(operationError ?: "") },
            confirmButton = {
                TextButton(onClick = { operationError = null }) {
                    Text("Понятно")
                }
            }
        )
    }

    // ✅ ИСПРАВЛЕНИЕ ПУНКТА 3: Диалог успеха
    if (operationSuccess != null) {
        AlertDialog(
            onDismissRequest = { operationSuccess = null },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Успешно") },
            text = { Text(operationSuccess ?: "") },
            confirmButton = {
                TextButton(onClick = { operationSuccess = null }) {
                    Text("OK")
                }
            }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
        ) {
            Text("Подтвердить")
        }
    }
}
