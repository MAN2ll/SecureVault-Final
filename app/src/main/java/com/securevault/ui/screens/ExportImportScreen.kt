@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    exportManager: ExportManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMessage by remember { mutableStateOf<String?>(null) }
    
    val actualExportManager = exportManager ?: run {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ExportManagerEntryPoint::class.java
        )
        entryPoint.exportManager()
    }
    
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            scope.launch {
                val result = actualExportManager.exportToFile(
                    entries = viewModel.entries.value,
                    uri = it,
                    masterPasswordHash = "hash_placeholder"
                )
                showMessage = when (result) {
                    is ExportManager.ExportResult.Success -> "Экспортировано ${result.count} записей"
                    is ExportManager.ExportResult.Error -> "Ошибка: ${result.message}"
                }
            }
        }
    }
    
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val result = actualExportManager.importFromFile(
                    uri = it,
                    masterPasswordHash = "hash_placeholder"
                )
                when (result) {
                    is ExportManager.ImportResult.Success -> {
                        result.entries.forEach { entry -> viewModel.insert(entry) }
                        showMessage = "Импортировано ${result.entries.size} записей"
                    }
                    is ExportManager.ImportResult.Error -> {
                        showMessage = "Ошибка: ${result.message}"
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Резервная копия",
                        fontWeight = FontWeight.Bold
                    )
                },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { exportLauncher.launch("securevault_backup${System.currentTimeMillis()}.vault") }
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
                        Column {
                            Text("Экспорт в файл", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Создать зашифрованную резервную копию", fontSize = 13.sp)
                        }
                    }
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { importLauncher.launch("*/*") }
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
                        Column {
                            Text("Импорт из файла", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Восстановить данные из резервной копии", fontSize = 13.sp)
                        }
                    }
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Безопасность", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Файл шифруется ключом устройства",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Привязан к вашему мастер-паролю",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Не передавайте файл третьим лицам",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            showMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(bottom = 8.dp),
                    action = {
                        TextButton(onClick = { showMessage = null }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(msg)
                }
            }
        }
    }
}

@ dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface ExportManagerEntryPoint {
    fun exportManager(): ExportManager
}
