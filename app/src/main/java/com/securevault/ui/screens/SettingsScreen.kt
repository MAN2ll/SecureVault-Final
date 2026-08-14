@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.securevault.ui.components.LockActionButton
import com.securevault.utils.RotationNotificationWorker
import com.securevault.viewmodel.AuthViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onLock: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE) }
    
    var isBiometricEnabled by remember { mutableStateOf(viewModel.isBiometricLoginEnabled()) }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notifications_rotation_enabled", false)) }
    
    val timeoutOptions = listOf(0 to "Сразу", 1 to "Через 1 минуту", 5 to "Через 5 минут", 15 to "Через 15 минут", 30 to "Через 30 минут")
    var currentTimeout by remember { mutableStateOf(prefs.getInt("auto_lock_timeout_minutes", 5)) }
    val currentTimeoutLabel = timeoutOptions.find { it.first == currentTimeout }?.second ?: "Через 5 минут"
    var expandedAutoLock by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            notificationsEnabled = true
            prefs.edit().putBoolean("notifications_rotation_enabled", true).apply()
            scheduleNotifications(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = { LockActionButton(onLock = onLock) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Безопасность", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Автоблокировка", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(expanded = expandedAutoLock, onExpandedChange = { expandedAutoLock = !expandedAutoLock }) {
                        OutlinedTextField(
                            readOnly = true, value = currentTimeoutLabel, onValueChange = {},
                            label = { Text("Время блокировки") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedAutoLock) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expandedAutoLock, onDismissRequest = { expandedAutoLock = false }) {
                            timeoutOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        currentTimeout = value
                                        prefs.edit().putInt("auto_lock_timeout_minutes", value).apply()
                                        expandedAutoLock = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Вход по отпечатку пальца", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text("Используйте биометрию для быстрого входа", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { newValue ->
                            viewModel.setBiometricLoginEnabled(newValue)
                            isBiometricEnabled = newValue
                        }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Уведомления о ротации", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text("Напоминать о просроченных паролях", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                        notificationsEnabled = true
                                        prefs.edit().putBoolean("notifications_rotation_enabled", true).apply()
                                        scheduleNotifications(context)
                                    } else {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    notificationsEnabled = true
                                    prefs.edit().putBoolean("notifications_rotation_enabled", true).apply()
                                    scheduleNotifications(context)
                                }
                            } else {
                                notificationsEnabled = false
                                prefs.edit().putBoolean("notifications_rotation_enabled", false).apply()
                                cancelNotifications(context)
                            }
                        }
                    )
                }
            }

            HorizontalDivider()
            Text("Действия", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            SettingsActionCard(icon = Icons.Default.Lock, title = "Сменить мастер-пароль", subtitle = "Обновите пароль для входа в приложение", onClick = onNavigateToChangePassword)
            SettingsActionCard(icon = Icons.Default.Upload, title = "Экспорт / Импорт", subtitle = "Резервное копирование и перенос данных", onClick = onNavigateToExport)
        }
    }
}

private fun scheduleNotifications(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<RotationNotificationWorker>(1, TimeUnit.DAYS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("securevault_rotation_notifications", ExistingPeriodicWorkPolicy.UPDATE, workRequest)
}

private fun cancelNotifications(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork("securevault_rotation_notifications")
}

@Composable
private fun SettingsActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
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
