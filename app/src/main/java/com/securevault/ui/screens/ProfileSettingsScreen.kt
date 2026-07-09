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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
    LaunchedEffect(profileId) {
        if (profileId != null) {
            vaultViewModel.setCurrentProfile(profileId)
        }
    }

    val profiles by profileViewModel.profiles.collectAsState()
    val entries by vaultViewModel.entries.collectAsState()
    val profile = remember(profileId, profiles) { profiles.find { it.id == profileId } }

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
            // Информация о профиле
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

            // Действия с паролями
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

            // Опасная зона
            Text("Опасная зона", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Удалить все пароли", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Все записи профиля будут удалены безвозвратно", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
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
