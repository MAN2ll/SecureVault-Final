@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.securevault.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.BackupData
import com.securevault.data.EncryptedBackup
import com.securevault.security.MasterPasswordHasher
import com.securevault.utils.BackupManager
import com.securevault.utils.ExportManager
import com.securevault.utils.ImportMode
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportScreen(
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

    val context = LocalContext.current.applicationContext
    val exportManager = remember { ExportManager(context) }
    val scope = rememberCoroutineScope()

    val entries by vaultViewModel.entries.collectAsState()
    val profiles by profileViewModel.profiles.collectAsState()
    val currentProfileId by vaultViewModel.currentProfileId.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedEntryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importTargetProfileId by remember { mutableIntStateOf(profileId ?: currentProfileId ?: 0) }
    var expandedTargetProfile by remember { mutableStateOf(false) }

    //  Мастер-пароль перед backup/импортом
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var masterPasswordInput by remember { mutableStateOf("") }
    var masterPasswordError by remember { mutableStateOf<String?>(null) }
    var pendingMasterPasswordAction by remember { mutableStateOf<MasterPasswordAction?>(null) }

    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    //  Флаг success/error вместо строки
    var importResult by remember { mutableStateOf<OperationResult?>(null) }

    var showImportModeDialog by remember { mutableStateOf(false) }
    var pendingBackupData by remember { mutableStateOf<BackupData?>(null) }

    //  Сохраняем выбранный режим импорта
    var pendingImportMode by remember { mutableStateOf<ImportMode?>(null) }

    var showPinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    //Класс для результата операции
    data class OperationResult(val success: Boolean, val message: String)

    //  Действия, требующие мастер-пароль
    enum class MasterPasswordAction {
        CREATE_BACKUP,
        IMPORT_BACKUP
    }

    fun performImport(
        backupData: BackupData,
        mode: ImportMode,
        pin: String
    ) {
        scope.launch {
            isImporting = true
            try {
                val result = withContext(Dispatchers.IO) {
                    vaultViewModel.importBackup(backupData, mode, pin)
                }
                val message = buildString {
                    append("Импорт завершён\n")
                    append("Профилей: ${result.importedProfiles}\n")
                    append("Записей: ${result.importedEntries}")
                    if (result.errors.isNotEmpty()) {
                        append("\n\nОшибки:\n")
                        append(result.errors.take(3).joinToString("\n"))
                    }
                }
                importResult = OperationResult(success = result.success, message = message)
            } catch (e: Exception) {
                importResult = OperationResult(success = false, message = "❌ Ошибка: ${e.message}")
            } finally {
                isImporting = false
                pendingBackupData = null
                pendingImportMode = null
            }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            val entriesToExport = entries.filter { entry -> selectedEntryIds.contains(entry.id) }
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

    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val result = exportManager.importFromCsv(it, importTargetProfileId, generateNewIds = true)
            scope.launch {
                result.entries.forEach { entry -> vaultViewModel.insert(entry) }
                val message = buildString {
                    if (result.entries.isNotEmpty()) append("Импортировано: ${result.entries.size} записей")
                    if (result.hasKeystoreErrors) {
                        if (isNotEmpty()) append("\n")
                        append("${result.keystoreErrors} записей нельзя расшифровать")
                    }
                    if (result.errors.isNotEmpty() && result.entries.isEmpty()) {
                        append("Ошибка: ${result.errors.take(3).joinToString("; ")}")
                    }
                    if (isEmpty()) append("Файл не содержит данных")
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    //  Экспорт полного backup (после подтверждения мастер-пароля)
    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                isExporting = true
                try {
                    val backupData = withContext(Dispatchers.IO) {
                        vaultViewModel.exportAllProfiles()
                    }
                    val encrypted = withContext(Dispatchers.IO) {
                        BackupManager.encryptBackup(backupData, backupPassword)
                    }
                    val jsonString = encrypted.toJson()

                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                    }

                    val profileCount = backupData.profiles.size
                    val entryCount = backupData.profiles.sumOf { it.entries.size }
                    //  Используем OperationResult
                    importResult = OperationResult(
                        success = true,
                        message = "Backup создан\nПрофилей: $profileCount\nЗаписей: $entryCount"
                    )
                } catch (e: Exception) {
                    importResult = OperationResult(success = false, message = "Ошибка: ${e.message}")
                } finally {
                    isExporting = false
                    backupPassword = ""
                }
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                isImporting = true
                try {
                    val jsonString = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                            reader.readText()
                        } ?: throw Exception("Не удалось прочитать файл")
                    }
                    val encrypted = EncryptedBackup.fromJson(jsonString)
                    val backupData = withContext(Dispatchers.IO) {
                        BackupManager.decryptBackup(encrypted, importPassword)
                    }
                    pendingBackupData = backupData
                    isImporting = false
                    showImportModeDialog = true
                } catch (e: Exception) {
                    importResult = OperationResult(success = false, message = "Ошибка: ${e.message}")
                    isImporting = false
                    importPassword = ""
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (profileId != null) "Экспорт / Импорт профиля" else "Экспорт / Импорт",
                        fontWeight = FontWeight.Bold
                    )
                },
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
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("CSV экспорт записей", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Для совместимости с другими приложениями", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Выберите записи", fontWeight = FontWeight.Medium)
                                TextButton(onClick = {
                                    selectedEntryIds = if (selectedEntryIds.size == entries.size) emptySet() else entries.map { it.id }.toSet()
                                }) {
                                    Text(if (selectedEntryIds.size == entries.size) "Снять все" else "Выбрать все")
                                }
                            }

                            entries.forEach { entry ->
                                val isSelected = selectedEntryIds.contains(entry.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedEntryIds = if (isSelected) selectedEntryIds - entry.id else selectedEntryIds + entry.id
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = isSelected, onCheckedChange = null)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(entry.service, fontWeight = FontWeight.Medium)
                                        Text(entry.username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { csvExportLauncher.launch(exportManager.generateExportFilename()) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedEntryIds.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Upload, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Экспортировать CSV (${selectedEntryIds.size})")
                            }
                        }
                    }

                    HorizontalDivider()

                    //  Полный защищённый backup с мастер-паролем
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Полный защищённый backup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Экспортирует все профили и пароли в зашифрованном файле.\n" +
                                "Файл защищён паролем (AES-256-GCM).\n" +
                                "Можно переносить между устройствами.\n" +
                                "Требуется подтверждение мастер-паролем.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    //  Сначала мастер-пароль
                                    pendingMasterPasswordAction = MasterPasswordAction.CREATE_BACKUP
                                    showMasterPasswordDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = profiles.isNotEmpty() && !isExporting
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Создание backup...")
                                } else {
                                    Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Создать backup")
                                }
                            }
                        }
                    }

                    //  Отображение результата
                    if (importResult != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (importResult!!.success)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    if (importResult!!.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    null,
                                    tint = if (importResult!!.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    importResult!!.message,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("CSV импорт", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Целевой профиль:", fontWeight = FontWeight.Medium)
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
                            }

                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { csvImportLauncher.launch(arrayOf("text/csv", "*/*")) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = profiles.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Импортировать CSV")
                            }
                        }
                    }

                    HorizontalDivider()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Импорт полного backup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Восстанавливает все профили и пароли из зашифрованного файла.\n" +
                                "Профили создаются заново с новыми ID.\n" +
                                "Пароли заново шифруются на текущем устройстве.\n" +
                                "Требуется подтверждение мастер-паролем.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    //  Сначала мастер-пароль
                                    pendingMasterPasswordAction = MasterPasswordAction.IMPORT_BACKUP
                                    showMasterPasswordDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isImporting
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Импорт...")
                                } else {
                                    Icon(Icons.Default.LockOpen, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Импортировать backup")
                                }
                            }
                        }
                    }

                    //  Отображение результата
                    if (importResult != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (importResult!!.success)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    if (importResult!!.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    null,
                                    tint = if (importResult!!.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    importResult!!.message,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    //  Диалог мастер-пароля ПЕРЕД backup/импортом
    if (showMasterPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showMasterPasswordDialog = false
                masterPasswordInput = ""
                masterPasswordError = null
                pendingMasterPasswordAction = null
            },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Подтверждение мастер-паролем") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (pendingMasterPasswordAction) {
                            MasterPasswordAction.CREATE_BACKUP -> "Для создания полного backup введите мастер-пароль"
                            MasterPasswordAction.IMPORT_BACKUP -> "Для импорта полного backup введите мастер-пароль"
                            else -> "Введите мастер-пароль"
                        },
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = masterPasswordInput,
                        onValueChange = { masterPasswordInput = it; masterPasswordError = null },
                        label = { Text("Мастер-пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = masterPasswordError != null
                    )
                    if (masterPasswordError != null) {
                        Text(masterPasswordError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    val storedHash = prefs.getString("master_hash", null)
                    val storedSalt = prefs.getString("master_salt", null)
                    val iterations = prefs.getInt("master_iterations", 100_000)

                    if (storedHash != null && storedSalt != null &&
                        MasterPasswordHasher.verify(masterPasswordInput, storedHash, storedSalt, iterations)) {
                        showMasterPasswordDialog = false
                        masterPasswordInput = ""
                        masterPasswordError = null

                        // ✅ Открываем следующий диалог в зависимости от действия
                        when (pendingMasterPasswordAction) {
                            MasterPasswordAction.CREATE_BACKUP -> showBackupPasswordDialog = true
                            MasterPasswordAction.IMPORT_BACKUP -> showImportPasswordDialog = true
                            else -> {}
                        }
                        pendingMasterPasswordAction = null
                    } else {
                        masterPasswordError = "Неверный мастер-пароль"
                    }
                }) {
                    Text("Подтвердить")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMasterPasswordDialog = false
                    masterPasswordInput = ""
                    masterPasswordError = null
                    pendingMasterPasswordAction = null
                }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showBackupPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showBackupPasswordDialog = false },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Защитить backup паролем") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Введите пароль для шифрования backup-файла", fontSize = 13.sp)
                    Text("Запомните этот пароль — без него невозможно будет восстановить данные!", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("Пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (backupPassword.length >= 4) {
                            showBackupPasswordDialog = false
                            backupExportLauncher.launch("securevault_backup_${System.currentTimeMillis()}.json")
                        } else {
                            Toast.makeText(context, "Пароль должен быть минимум 4 символа", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = backupPassword.length >= 4
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupPasswordDialog = false; backupPassword = "" }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showImportPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showImportPasswordDialog = false },
            icon = { Icon(Icons.Default.LockOpen, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Введите пароль backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Введите пароль, который использовался при создании backup", fontSize = 13.sp)
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text("Пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importPassword.isNotEmpty()) {
                            showImportPasswordDialog = false
                            backupImportLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    },
                    enabled = importPassword.isNotEmpty()
                ) {
                    Text("Импортировать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportPasswordDialog = false; importPassword = "" }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showImportModeDialog && pendingBackupData != null) {
        AlertDialog(
            onDismissRequest = { showImportModeDialog = false; pendingBackupData = null },
            icon = { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Режим импорта") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Найдено профилей: ${pendingBackupData!!.profiles.size}", fontWeight = FontWeight.Medium)
                    Text("Всего записей: ${pendingBackupData!!.profiles.sumOf { it.entries.size }}", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Выберите режим:", fontWeight = FontWeight.Medium)

                    Spacer(Modifier.height(8.dp))

                    //  Сохраняем выбранный режим
                    Button(
                        onClick = {
                            pendingImportMode = ImportMode.ADD_AS_NEW
                            showImportModeDialog = false
                            showPinDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Добавить как новые профили")
                    }

                    OutlinedButton(
                        onClick = {
                            pendingImportMode = ImportMode.MERGE_IF_EXISTS
                            showImportModeDialog = false
                            showPinDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Объединить с существующими")
                    }

                    OutlinedButton(
                        onClick = {
                            pendingImportMode = ImportMode.SKIP_IF_EXISTS
                            showImportModeDialog = false
                            showPinDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Пропустить существующие")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImportModeDialog = false; pendingBackupData = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    //  Используем сохранённый режим
    if (showPinDialog && pendingBackupData != null) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false; pendingBackupData = null; newPin = ""; confirmPin = "" },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Задайте PIN для импортируемых профилей") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Все импортированные профили будут использовать этот PIN для входа.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it; pinError = null },
                        label = { Text("Новый PIN (4-8 символов)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = pinError != null
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { confirmPin = it; pinError = null },
                        label = { Text("Подтвердите PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = pinError != null
                    )
                    if (pinError != null) {
                        Text(pinError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            newPin.length < 4 -> pinError = "PIN слишком короткий (минимум 4 символа)"
                            newPin.length > 8 -> pinError = "PIN слишком длинный (максимум 8 символов)"
                            newPin != confirmPin -> pinError = "PIN не совпадают"
                            else -> {
                                showPinDialog = false
                                //  Используем сохранённый режим
                                val mode = pendingImportMode ?: ImportMode.ADD_AS_NEW
                                performImport(pendingBackupData!!, mode, newPin)
                                newPin = ""
                                confirmPin = ""
                            }
                        }
                    }
                ) {
                    Text("Импортировать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false; pendingBackupData = null; newPin = ""; confirmPin = "" }) {
                    Text("Отмена")
                }
            }
        )
    }
}
