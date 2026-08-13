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
import com.securevault.ui.components.LockActionButton
import com.securevault.viewmodel.AuthViewModel

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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            notificationsEnabled = true
            prefs.edit().putBoolean("notifications_rotation_enabled", true).apply()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = {
                    LockActionButton(onLock = onLock)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Безопасность", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Вход по отпечатку пальца", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text("Используйте биометрию для быстрого входа", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                // Здесь можно добавить логику проверки доступности биометрии перед включением
                                viewModel.setBiometricLoginEnabled(true)
                                isBiometricEnabled = true
                            } else {
                                viewModel.setBiometricLoginEnabled(false)
                                isBiometricEnabled = false
                            }
                        }
                    )
                }
            }

            // Переключатель уведомлений о ротации
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                    } else {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    notificationsEnabled = true
                                    prefs.edit().putBoolean("notifications_rotation_enabled", true).apply()
                                }
                            } else {
                                notificationsEnabled = false
                                prefs.edit().putBoolean("notifications_rotation_enabled", false).apply()
                            }
                        }
                    )
                }
            }

            HorizontalDivider()

            Text("Действия", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            SettingsActionCard(
                icon = Icons.Default.Lock,
                title = "Сменить мастер-пароль",
                subtitle = "Обновите пароль для входа в приложение",
                onClick = onNavigateToChangePassword
            )
            
            SettingsActionCard(
                icon = Icons.Default.Upload,
                title = "Экспорт / Импорт",
                subtitle = "Резервное копирование и перенос данных",
                onClick = onNavigateToExport
            )
        }
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
