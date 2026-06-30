@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.securevault.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    // ✅ Создаём ExportManager через context, а не через hiltViewModel
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

    // ✅ Лаунчер для экспорта
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

    // ✅ Лаунчер для импорта
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val imported = exportManager.importFromCsv(it, importTargetProfileId)
            scope.launch {
                imported.forEach { entry -> vaultViewModel.insert(entry) }
                Toast.makeText(
                    context,
                    "Импортировано: ${imported.size} записей",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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
            // Вкладки
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Экспорт") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Импорт") }
                )
            }

            if (selectedTab == 0) {
                // ===== ЭКСПОРТ =====
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Заголовок с кнопкой "Выбрать все"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Выберите записи для экспорта", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        TextButton(onClick = {
                            selectedEntryIds = if (selectedEntryIds.size == entries.size) {
                                emptySet()
                            } else {
                                entries.map { it.id }.toSet()
                            }
                        }) {
                            Text(if (selectedEntryIds.size == entries.size) "Снять все" else "Выбрать все")
                        }
                    }

                    // Фильтр по профилям
                    if (profiles.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Фильтр по профилям:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                androidx.compose.foundation.layout.FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    profiles.forEach { profile ->
                                        val isSelected = selectedProfileIds.contains(profile.id)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedProfileIds = if (isSelected) {
                                                    selectedProfileIds - profile.id
                                                } else {
                                                    selectedProfileIds + profile.id
                                                }
                                            },
                                            label = { Text(profile.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Список записей с галочками
                    val filteredEntries = if (selectedProfileIds.isEmpty()) {
                        entries
                    } else {
                        entries.filter { it.profileId in selectedProfileIds }
                    }

                    if (filteredEntries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Inbox, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp))
                                Text("Нет записей для экспорта", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        filteredEntries.forEach { entry ->
                            val isSelected = selectedEntryIds.contains(entry.id)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedEntryIds = if (isSelected) {
                                            selectedEntryIds - entry.id
                                        } else {
                                            selectedEntryIds + entry.id
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedEntryIds = if (checked) {
                                                selectedEntryIds + entry.id
                                            } else {
                                                selectedEntryIds - entry.id
                                            }
                                        }
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.service, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            entry.username,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Кнопка экспорта
                    Button(
                        onClick = {
                            if (selectedEntryIds.isEmpty()) {
                                Toast.makeText(context, "Выберите хотя бы одну запись", Toast.LENGTH_SHORT).show()
                            } else {
                                exportLauncher.launch(exportManager.generateExportFilename())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedEntryIds.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Upload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Экспортировать (${selectedEntryIds.size})")
                    }
                }
            } else {
                // ===== ИМПОРТ =====
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FileOpen, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Импорт из файла", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            
                            Text(
                                "Поддерживаемые форматы: CSV",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            
                            // Выбор целевого профиля
                            Text("Целевой профиль:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            
                            if (profiles.isNotEmpty()) {
                                ExposedDropdownMenuBox(
                                    expanded = expandedTargetProfile,
                                    onExpandedChange = { expandedTargetProfile = !expandedTargetProfile }
                                ) {
                                    val targetProfile = profiles.find { it.id == importTargetProfileId }
                                    OutlinedTextField(
                                        readOnly = true,
                                        value = targetProfile?.name ?: "Выберите профиль",
                                        onValueChange = {},
                                        label = { Text("Профиль для импорта") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedTargetProfile) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expandedTargetProfile,
                                        onDismissRequest = { expandedTargetProfile = false }
                                    ) {
                                        profiles.forEach { profile ->
                                            DropdownMenuItem(
                                                text = { Text(profile.name) },
                                                onClick = {
                                                    importTargetProfileId = profile.id
                                                    expandedTargetProfile = false
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Сначала создайте профиль",
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = profiles.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Выбрать файл")
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(8.dp))
                                Text("Информация", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "• Файл должен быть в формате CSV, экспортированным из SecureVault\n" +
                                "• Пароли будут импортированы в зашифрованном виде\n" +
                                "• Все записи будут добавлены в выбранный профиль",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
