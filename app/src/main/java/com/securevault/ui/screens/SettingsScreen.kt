@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.securevault.utils.AutoLockManager
import com.securevault.utils.NotificationHelper
import com.securevault.utils.ReminderScheduler
import com.securevault.utils.ThemeManager
import com.securevault.utils.ThemeManager.AppTheme
import com.securevault.viewmodel.AuthViewModel
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTheme by remember { mutableStateOf(AppTheme.SYSTEM) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    
    // ✅ ВЫНЕСЕНО: переменные для переключателей на верхний уровень
    var autoLockEnabled by remember { mutableStateOf(AutoLockManager.isEnabled()) }
    var notificationsEnabled by remember { mutableStateOf(NotificationHelper.isEnabled(context)) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
                    writer.write("Service,Username,Password,ProfileId\n")
                    viewModel.entries.value.forEach { entry ->
                        writer.write("${entry.service},${entry.username},${entry.password},${entry.profileId}\n")
                    }
                    writer.flush()
                    Toast.makeText(context, "Экспорт успешен!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка экспорта: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        ThemeManager.getThemeFlow(context).collect { currentTheme = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Оформление
            Text("ОФОРМЛЕНИЕ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), onClick = { showThemeDialog = true }) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Тема оформления", fontWeight = FontWeight.Medium)
                            Text(
                                when (currentTheme) {
                                    AppTheme.SYSTEM -> "Системная"
                                    AppTheme.LIGHT -> "Светлая"
                                    AppTheme.DARK -> "Тёмная"
                                    else -> "Системная"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(">", fontSize = 16.sp)
                }
            }

            // Безопасность
            Text("БЕЗОПАСНОСТЬ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), onClick = { showChangePasswordDialog = true }) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Изменить мастер-пароль", fontWeight = FontWeight.Medium)
                            Text("Смена пароля для входа", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(">", fontSize = 16.sp)
                }
            }

            // ✅ АВТОБЛОКИРОВКА (исправлено)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Автоблокировка", fontWeight = FontWeight.Medium)
                                Text("Блокировать при неактивности", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = autoLockEnabled,
                            onCheckedChange = {
                                autoLockEnabled = it
                                AutoLockManager.setEnabled(it)
                            }
                        )
                    }

                    if (autoLockEnabled) {
                        Spacer(Modifier.height(16.dp))

                        var timeoutMinutes by remember { mutableIntStateOf(AutoLockManager.getTimeoutMinutes()) }
                        var expanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = "$timeoutMinutes мин.",
                                onValueChange = {},
                                label = { Text("Время неактивности") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                listOf(1, 2, 5, 10, 15, 30).forEach { minutes ->
                                    DropdownMenuItem(
                                        text = { Text("$minutes мин.") },
                                        onClick = {
                                            timeoutMinutes = minutes
                                            AutoLockManager.setTimeoutMinutes(minutes)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Приложение заблокируется после $timeoutMinutes мин. неактивности",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ✅ УВЕДОМЛЕНИЯ (исправлено)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Уведомления", fontWeight = FontWeight.Medium)
                                Text("Напоминания об истечении паролей", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = {
                                notificationsEnabled = it
                                NotificationHelper.setEnabled(context, it)
                                if (it) {
                                    ReminderScheduler.scheduleReminder(context)
                                } else {
                                    ReminderScheduler.cancelReminder(context)
                                }
                            }
                        )
                    }

                    if (notificationsEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "• За 7 дней до истечения — обычное уведомление\n• За 1 день — критическое уведомление\n• В день истечения — срочное уведомление",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Данные
            Text("ДАННЫЕ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), onClick = { onNavigate("export") }) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Экспорт / Импорт", fontWeight = FontWeight.Medium)
                            Text("Расширенные настройки экспорта", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(">", fontSize = 16.sp)
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), onClick = { exportLauncher.launch("securevault_backup.csv") }) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Быстрый экспорт (CSV)", fontWeight = FontWeight.Medium)
                            Text("Все пароли одним файлом", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(">", fontSize = 16.sp)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showResetDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Сбросить хранилище", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                            Text("Удалить все пароли", fontSize = 12.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                        }
                    }
                    Text(">", fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            // О приложении
            Text("О ПРИЛОЖЕНИИ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Безопасность", fontWeight = FontWeight.Medium)
                            Text("AES-256-GCM · SHA-256", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Версия", fontWeight = FontWeight.Medium)
                            Text("SecureVault 1.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Авторские методы", fontWeight = FontWeight.Medium)
                            Text("6 алгоритмов шифрования", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // Диалог темы
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Выберите тему") },
            text = {
                Column {
                    AppTheme.entries.forEach { theme ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == theme,
                                onClick = {
                                    currentTheme = theme
                                    scope.launch { ThemeManager.saveTheme(context, theme) }
                                    showThemeDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (theme) {
                                    AppTheme.SYSTEM -> "Системная"
                                    AppTheme.LIGHT -> "Светлая"
                                    AppTheme.DARK -> "Тёмная"
                                    else -> "Системная"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Закрыть") } }
        )
    }

    // Диалог смены пароля
    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            authViewModel = authViewModel,
            onSuccess = {
                showChangePasswordDialog = false
                Toast.makeText(context, "Мастер-пароль изменён!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Диалог сброса
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Подтверждение") },
            text = { Text("Все пароли будут удалены безвозвратно!") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAll()
                        showResetDialog = false
                        Toast.makeText(context, "Очищено", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить всё") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Отмена") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    authViewModel: AuthViewModel,
    onSuccess: () -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Смена мастер-пароля") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it; error = null },
                    label = { Text("Текущий пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = { Text("Новый пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Подтвердите новый пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                error = null
                when {
                    oldPassword.isBlank() -> error = "Введите текущий пароль"
                    newPassword.length < 4 -> error = "Новый пароль слишком короткий"
                    newPassword != confirmPassword -> error = "Пароли не совпадают"
                    oldPassword == newPassword -> error = "Новый пароль должен отличаться"
                    else -> {
                        val success = authViewModel.changeMasterPassword(oldPassword, newPassword)
                        if (success) onSuccess() else error = "Неверный текущий пароль"
                    }
                }
            }) { Text("Сменить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
