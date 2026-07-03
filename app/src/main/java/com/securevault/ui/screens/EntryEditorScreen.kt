@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.security.MasterPasswordHasher
import com.securevault.utils.CryptoUtils
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator
import com.securevault.utils.PasswordValidator
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel

@Composable
fun EntryEditorScreen(
    id: String?,
    profileId: Int? = null,
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val isNewEntry = id == null || id == "new"
    val context = LocalContext.current

    val allEntries by viewModel.entries.collectAsState()
    val existingEntry = remember(id, allEntries) {
        if (isNewEntry) null else allEntries.find { e -> e.id == id }
    }

    val currentProfileId by viewModel.currentProfileId.collectAsState()
    val effectiveProfileId = profileId ?: currentProfileId

    val profiles by profileViewModel.profiles.collectAsState()
    val profileName = remember(effectiveProfileId, profiles) {
        val profile = profiles.find { it.id == effectiveProfileId }
        profile?.name ?: "Профиль #${effectiveProfileId ?: "?"}"
    }

    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var textHint by remember { mutableStateOf("") }
    var rotationEnabled by remember { mutableStateOf(false) }
    var rotationMonths by remember { mutableIntStateOf(6) }
    var isFavorite by remember { mutableStateOf(false) }
    var generationType by remember { mutableStateOf("random") }

    var passwordChanged by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPasswordDialog by remember { mutableStateOf(false) }

    var showGeneratorDialog by remember { mutableStateOf(false) }
    var showMnemonicDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(existingEntry) {
        existingEntry?.let { entry ->
            service = entry.service
            username = entry.username
            password = ""
            url = entry.url ?: ""
            notes = entry.notes ?: ""
            textHint = entry.textHint ?: ""
            rotationEnabled = entry.rotationEnabled
            rotationMonths = entry.rotationPeriodMonths
            isFavorite = entry.isFavorite
            generationType = entry.generationType
            passwordChanged = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNewEntry) "Новая запись" else "Редактировать",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = {
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                            contentDescription = null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        if (service.isBlank()) {
                            showError = "Заполните название сервиса"
                            return@IconButton
                        }
                        if (isNewEntry && password.isBlank()) {
                            showError = "Введите или сгенерируйте пароль"
                            return@IconButton
                        }
                        if (effectiveProfileId == null) {
                            showError = "Профиль не выбран"
                            return@IconButton
                        }

                        val now = System.currentTimeMillis()

                        val finalEntry = if (existingEntry != null) {
                            if (passwordChanged) {
                                val finalPassword = if (password.isBlank()) existingEntry.password else password

                                val validation = PasswordValidator.validateNewPasswordForEntry(
                                    entry = existingEntry,
                                    newPassword = finalPassword,
                                    context = context
                                )
                                if (!validation.isValid) {
                                    showError = validation.errorMessage
                                    return@IconButton
                                }

                                val encryptedPwd = CryptoUtils.encrypt(finalPassword)
                                val newFingerprint = PasswordValidator.buildPasswordFingerprint(finalPassword, context)
                                val oldFingerprint = PasswordValidator.buildPasswordFingerprint(existingEntry.password, context)
                                val newNextRotationDate = if (rotationEnabled) {
                                    val existingNextDate = existingEntry.nextRotationDate
                                    if (existingNextDate == null || existingEntry.rotationPeriodMonths != rotationMonths) {
                                        now + (rotationMonths * 30L * 24 * 60 * 60 * 1000)
                                    } else {
                                        existingNextDate
                                    }
                                } else {
                                    null
                                }

                                existingEntry.addToPasswordHistory(
                                    oldPassword = existingEntry.password,
                                    generationType = existingEntry.generationType,
                                    oldPasswordFingerprint = oldFingerprint
                                ).copy(
                                    service = service,
                                    username = username,
                                    encryptedPassword = encryptedPwd,
                                    url = url.ifBlank { null },
                                    notes = notes.ifBlank { null },
                                    textHint = textHint.ifBlank { null },
                                    rotationEnabled = rotationEnabled,
                                    rotationPeriodMonths = rotationMonths,
                                    nextRotationDate = newNextRotationDate,
                                    isFavorite = isFavorite,
                                    lastChanged = now,
                                    generationType = generationType,
                                    passwordFingerprint = newFingerprint
                                )
                            } else {
                                val newNextRotationDate = if (rotationEnabled) {
                                    val existingNextDate = existingEntry.nextRotationDate
                                    if (existingNextDate == null || existingEntry.rotationPeriodMonths != rotationMonths) {
                                        now + (rotationMonths * 30L * 24 * 60 * 60 * 1000)
                                    } else {
                                        existingNextDate
                                    }
                                } else {
                                    null
                                }

                                existingEntry.copy(
                                    service = service,
                                    username = username,
                                    url = url.ifBlank { null },
                                    notes = notes.ifBlank { null },
                                    textHint = textHint.ifBlank { null },
                                    rotationEnabled = rotationEnabled,
                                    rotationPeriodMonths = rotationMonths,
                                    nextRotationDate = newNextRotationDate,
                                    isFavorite = isFavorite,
                                    generationType = generationType
                                )
                            }
                        } else {
                            val uniqueCheck = PasswordValidator.validateUniqueCharacters(password)
                            if (!uniqueCheck.isValid) {
                                showError = uniqueCheck.errorMessage
                                return@IconButton
                            }

                            // ✅ HMAC fingerprint для новой записи
                            val fingerprint = PasswordValidator.buildPasswordFingerprint(password, context)

                            Entry.create(
                                service = service,
                                username = username,
                                password = password,
                                profileId = effectiveProfileId!!,
                                passwordFingerprint = fingerprint,
                                url = url.ifBlank { null },
                                notes = notes.ifBlank { null },
                                textHint = textHint.ifBlank { null },
                                rotationEnabled = rotationEnabled,
                                rotationPeriodMonths = rotationMonths,
                                isFavorite = isFavorite,
                                generationType = generationType
                            )
                        }

                        viewModel.insert(finalEntry)
                        showSuccess = true
                        onBack()
                    }) {
                        Icon(Icons.Default.Check, "Сохранить")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Сохранено!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            if (showError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(showError!!, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                    }
                }
                showError = null
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = profileName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            OutlinedTextField(
                value = service,
                onValueChange = { service = it },
                label = { Text("Сервис *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Логин / Email") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordChanged = true
                },
                label = {
                    Text(
                        if (!isNewEntry && !passwordChanged && password.isBlank())
                            "Пароль (скрыт, нажмите 👁️ для просмотра)"
                        else
                            "Пароль *"
                    )
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                placeholder = {
                    if (!isNewEntry && !passwordChanged && password.isBlank()) {
                        Text("••••••••••••")
                    }
                },
                trailingIcon = {
                    Row {
                        if (!isNewEntry && existingEntry != null && !passwordChanged) {
                            IconButton(onClick = { showConfirmPasswordDialog = true }) {
                                Icon(Icons.Default.Visibility, "Показать текущий пароль")
                            }
                        } else if (showPassword) {
                            IconButton(onClick = { showPassword = false }) {
                                Icon(Icons.Default.VisibilityOff, "Скрыть пароль")
                            }
                        }
                        IconButton(onClick = { showGeneratorDialog = true }) {
                            Icon(Icons.Default.Casino, "Обычный генератор")
                        }
                        IconButton(onClick = { showMnemonicDialog = true }) {
                            Icon(Icons.Default.Lightbulb, "Мнемонический генератор")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (!isNewEntry && passwordChanged) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Пароль будет изменён и добавлен в историю",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (generationType == "mnemonic")
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (generationType == "mnemonic") Icons.Default.Lightbulb else Icons.Default.Casino,
                        null,
                        tint = if (generationType == "mnemonic")
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            if (generationType == "mnemonic") "Мнемонический пароль (AMPG v1)" else "Случайный пароль",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (generationType == "mnemonic") "Запоминается по подсказке" else "Криптостойкий, не запоминается",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL (необязательно)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметки") },
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Напоминание о смене пароля", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Switch(checked = rotationEnabled, onCheckedChange = { rotationEnabled = it })
                    }
                    if (rotationEnabled) {
                        Spacer(Modifier.height(12.dp))
                        var expandedMonths by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedMonths,
                            onExpandedChange = { expandedMonths = !expandedMonths }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = "$rotationMonths мес.",
                                onValueChange = {},
                                label = { Text("Менять каждые") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonths) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedMonths,
                                onDismissRequest = { expandedMonths = false }
                            ) {
                                listOf(3, 6, 12).forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text("$m мес.") },
