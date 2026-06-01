@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    exportManager: ExportManager = ExportManager(LocalContext.current)
) {
    var showExportSuccess by remember { mutableStateOf(false) }
    var showExportError by remember { mutableStateOf<String?>(null) }
    var showImportSuccess by remember { mutableStateOf(false) }
    var showImportError by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Лаунчер для выбора файла экспорта
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri: Uri? ->
            uri?.let {
                isProcessing = true
                scope.launch {
                    try {
                        val entries = viewModel.entries.value
                        val success = exportManager.exportToCsv(entries, context.contentResolver.openOutputStream(it)!!)
                        if (success) {
                            showExportSuccess = true
                        } else {
                            showExportError = "Ошибка записи файла"
                        }
                    } catch (e: Exception) {
                        showExportError = "Ошибка: ${e.message}"
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    )
    
    // Лаунчер для выбора файла импорта
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                isProcessing = true
                scope.launch {
                    try {
                        val importedEntries = exportManager.importFromCsv(it)
                        if (importedEntries.isNotEmpty()) {
                            // Сохраняем импортированные записи
                            importedEntries.forEach { entry ->
                                viewModel.insert(entry)
                            }
                            showImportSuccess = true
                        } else {
                            showImportError = "Не найдено записей для импорта"
                        }
                    } catch (e: Exception) {
                        showImportError = "Ошибка: ${e.message}"
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Экспорт / Импорт", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 📤 ЭКСПОРТ
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Экспорт данных", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Сохраните все пароли в CSV-файл для резервного копирования",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val filename = exportManager.generateExportFilename()
                            exportLauncher.launch(filename)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Экспортировать в CSV")
                    }
                }
            }
            
            // 📥 ИМПОРТ
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Импорт данных", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Восстановите пароли из ранее сохранённого CSV-файла",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            importLauncher.launch(arrayOf("text/csv"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Импортировать из CSV")
                    }
                }
            }
            
            // Статус обработки
            if (isProcessing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Обработка...", fontSize = 14.sp)
                }
            }
        }
    }
    
    // ✅ Диалог успеха экспорта
    if (showExportSuccess) {
        AlertDialog(
            onDismissRequest = { showExportSuccess = false },
            title = { Text("Экспорт завершён") },
            text = { Text("Файл успешно сохранён. Храните его в безопасном месте!") },
            confirmButton = {
                TextButton(onClick = { showExportSuccess = false }) { Text("OK") }
            }
        )
    }
    
    // ✅ Диалог ошибки экспорта
    showExportError?.let { error ->
        AlertDialog(
            onDismissRequest = { showExportError = null },
            title = { Text("Ошибка экспорта") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { showExportError = null }) { Text("Закрыть") }
            }
        )
    }
    
    // ✅ Диалог успеха импорта
    if (showImportSuccess) {
        AlertDialog(
            onDismissRequest = { showImportSuccess = false },
            title = { Text("Импорт завершён") },
            text = { Text("Записи успешно добавлены в хранилище.") },
            confirmButton = {
                TextButton(onClick = { showImportSuccess = false }) { Text("OK") }
            }
        )
    }
    
    // ✅ Диалог ошибки импорта
    showImportError?.let { error ->
        AlertDialog(
            onDismissRequest = { showImportError = null },
            title = { Text("Ошибка импорта") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { showImportError = null }) { Text("Закрыть") }
            }
        )
    }
}
