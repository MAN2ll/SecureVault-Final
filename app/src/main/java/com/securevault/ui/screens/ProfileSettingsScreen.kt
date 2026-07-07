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
    vaultViewModel: VaultViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    LaunchedEffect(profileId) {
        if (profileId != null) {
            vaultViewModel.setCurrentProfile(profileId)
        }
    }

    val profiles by profileViewModel.profiles.collectAsState()
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
                    Column {
                        Text(profile?.name ?: "—", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("ID: ${profile?.id ?: "—"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider()
            
            // Действия профиля
            Text("Действия", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            
            Text(
                "Основные действия с паролями доступны из меню списка записей (⋮):",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Ротация паролей", fontSize = 13.sp)
                    Text("• Аудит безопасности", fontSize = 13.sp)
                    Text("• Экспорт / импорт записей", fontSize = 13.sp)
                    Text("• Мнемонический генератор", fontSize = 13.sp)
                    Text("• Сканировать QR", fontSize = 13.sp)
                    Text("• Массовое удаление", fontSize = 13.sp)
                }
            }
        }
    }
}
