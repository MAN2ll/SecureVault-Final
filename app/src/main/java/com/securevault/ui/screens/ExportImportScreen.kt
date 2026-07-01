@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.securevault.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import com.securevault.utils.ExportManager
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportScreen(
    onBack: () -> Unit,
    vaultViewModel: VaultViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current.applicationContext
    val exportManager = remember { ExportManager(context) }
    
    val scope = rememberCoroutineScope()
    
    val entries by vaultViewModel.entries.collectAsState()
    val profiles by profileViewModel.profiles.collectAsState()
    val currentProfileId by vaultViewModel.currentProfileId.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedProfileIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectedEntryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importTargetProfileId by remember { mutableIntStateOf(currentProfileId ?: 0) }
    var expandedTargetProfile by remember { mutableStateOf(false) }
    var showExportWarning by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            val entriesToExport = entries.filter { entry -> 
                selectedEntryIds.contains(entry.id)
            }
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                val success = exportManager.exportToCsv(entriesToExport, outputStream)
                Toast.makeText(
                    context,
                    if (success) "Экспортировано: ${entriesToExport.size} записей" else "Ошибка экспорта",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val imported = exportManager.importFromCsv(it, importTargetProfileId)
            scope.launch {
                imported.forEach { entry -> vaultViewModel.insert(entry) }
                Toast.makeText(
                    context,
                    if (imported.isNotEmpty()) "Импортировано: ${imported.size} записей" else "Не удалось импортировать файл",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    //  ДИАЛОГ ПРЕДУПРЕЖДЕНИЯ ПЕРЕД ЭКСПОРТОМ
    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Внимание!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        " Файл экспорта содержит чувствительные данные!",
                        fontWeight = FontWeight.Bold
                    )
                    Text("• Пароли зашифрованы, но файл не защищён паролем")
                    Text("• Храните его только в безопасном месте")
                    Text("• Не передавайте третьим лицам")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        " Перенос между устройствами:",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Из-за Android Keystore зашифрованные пароли можно восстановить ТОЛЬКО на том же устройстве. " +
                        "Для переноса на другое устройство нужен защищённый экспорт с отдельным паролем (будет в будущих версиях).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportWarning = false
                        exportLauncher.launch(exportManager.generateExportFilename())
                    }
                ) {
                    Text("Понимаю, экспортировать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarning = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Экспорт / Импорт", fontWeight = FontWeight.Bold) },
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
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Экспорт") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Импорт") })
            }

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    //  ПРЕДУПРЕЖДЕНИЕ О ЧУВСТВИТЕЛЬНЫХ ДАННЫХ
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    " CSV содержит чувствительные данные!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Файл не зашифрован. Храните его в безопасном месте и не передавайте третьим лицам.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                    
                    //  ПРЕДУПРЕЖДЕНИЕ О ПЕРЕНОСЕ МЕЖДУ УСТРОЙСТВАМИ
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    )
