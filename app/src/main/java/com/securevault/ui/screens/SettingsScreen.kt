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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLockConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Безопасность", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = "Сменить мастер-пароль",
                        subtitle = "Рекомендуется менять регулярно",
                        onClick = onNavigateToChangePassword
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsItem(
                        icon = Icons.Default.LockReset,
                        title = "Заблокировать приложение",
                        subtitle = "Потребуется мастер-пароль для входа",
                        onClick = { showLockConfirmDialog = true }
                    )
                }
            }

            Text("Данные", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    SettingsItem(
                        icon = Icons.Default.Upload,
                        title = "Экспорт / Импорт",
                        subtitle = "Резервное копирование (CSV с шифрованием)",
                        onClick = onNavigateToExport
                    )
                }
            }

            Text("О приложении", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "SecureVault v1.0",
                        subtitle = "Менеджер паролей с AMPG v1",
                        onClick = { showAboutDialog = true }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Технологии защиты", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    TechnologyItem("🔐 AES-256-GCM", "Шифрование паролей через Android Keystore")
                    TechnologyItem("🔑 PBKDF2", "Хеширование мастер-пароля (100,000 итераций)")
                    TechnologyItem("🧠 AMPG v1", "Авторская мнемоническая генерация")
                    TechnologyItem("🔄 Управляемая ротация", "Автоматическое напоминание о смене")
                    TechnologyItem("🛡️ HMAC-SHA256", "Защищённые fingerprint паролей")
                    TechnologyItem("🚫 BruteForceGuard", "Защита от подбора мастер-пароля")
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("О приложении") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SecureVault v1.0", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Менеджер паролей с авторским алгоритмом мнемонической генерации AMPG v1.")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Разработано в рамках магистерской диссертации.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    if (showLockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLockConfirmDialog = false },
            title = { Text("Заблокировать приложение?") },
            text = { Text("Для разблокировки потребуется ввести мастер-пароль.") },
            confirmButton = {
                Button(onClick = {
                    authViewModel.lock()
                    showLockConfirmDialog = false
                }) {
                    Text("Заблокировать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun TechnologyItem(title: String, description: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            title,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            modifier = Modifier.width(140.dp)
        )
        Text(
            description,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
