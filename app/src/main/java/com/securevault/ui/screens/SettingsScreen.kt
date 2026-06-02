@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.widget.Toast
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
import com.securevault.utils.ThemeManager
import com.securevault.utils.ThemeManager.AppTheme
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTheme by remember { mutableStateOf(AppTheme.SYSTEM) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ThemeManager.getThemeFlow(context).collect { currentTheme = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("ОФОРМЛЕНИЕ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), onClick = { showThemeDialog = true }) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Тема оформления", fontWeight = FontWeight.Medium)
                        Text(currentTheme.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(">", fontSize = 16.sp)
                }
            }

            Text("БЕЗОПАСНОСТЬ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Изменить мастер-пароль", fontWeight = FontWeight.Medium)
                        Text("Смена пароля для входа", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(">", fontSize = 16.sp)
                }
            }

            Text("РЕЗЕРВНАЯ КОПИЯ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth(), onClick = { showExportDialog = true }) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Upload, null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Экспорт данных", fontWeight = FontWeight.Medium)
                        Text("CSV или TXT", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(">", fontSize = 16.sp)
                }
            }

            Text("ДАННЫЕ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showResetDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Сбросить хранилище", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                        Text("Удалить все пароли", fontSize = 12.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    }
                    Text(">", fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            Text("О ПРИЛОЖЕНИИ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Безопасность", fontWeight = FontWeight.Medium)
                            Text("AES-256-GCM · PBKDF2-SHA256", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Версия", fontWeight = FontWeight.Medium)
                            Text("Secure Vault 1.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // ✅ Диалог выбора темы с ПРАВИЛЬНЫМ синтаксисом when
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Выберите тему") },
            text = {
                Column {
                    AppTheme.entries.forEach { theme ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = currentTheme == theme,
                                onClick = {
                                    currentTheme = theme
                                    scope.launch { ThemeManager.saveTheme(context, theme) }
                                    showThemeDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            
                            // ✅ ИСПРАВЛЕНО: многострочный when (Kotlin требует переносов строк)
                            val themeName = when (theme) {
                                AppTheme.SYSTEM -> "Системная"
                                AppTheme.LIGHT -> "Светлая"
                                AppTheme.DARK -> "Тёмная"
                            }
                            Text(themeName)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showThemeDialog = false }) { Text("Закрыть") } }
        )
    }

    // ✅ Диалог сброса данных
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
            dismissButton = { TextButton({ showResetDialog = false }) { Text("Отмена") } }
        )
    }

    // ✅ Диалог экспорта
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false }, 
            title = { Text("Экспорт") }, 
            text = { Text("Выберите формат.") }, 
            confirmButton = { Button({ showExportDialog = false }) { Text("CSV") } }, 
            dismissButton = { 
                Column { 
                    TextButton({ showExportDialog = false }) { Text("TXT") }
                    TextButton({ showExportDialog = false }) { Text("Отмена") } 
                } 
            }
        )
    }
}
