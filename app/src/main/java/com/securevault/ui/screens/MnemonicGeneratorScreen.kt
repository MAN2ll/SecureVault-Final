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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator
import com.securevault.utils.PasswordValidator
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicGeneratorScreen(
    profileId: Int?, //  принимаем profileId из маршрута
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    //  Принудительно устанавливаем профиль
    LaunchedEffect(profileId) {
        if (profileId != null) {
            viewModel.setCurrentProfile(profileId)
        }
    }

    val currentProfileId by viewModel.currentProfileId.collectAsState()
    val effectiveProfileId = profileId ?: currentProfileId

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var phrase by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    var includeLeet by remember { mutableStateOf(true) }
    var includeServiceCode by remember { mutableStateOf(true) }
    var includeRotationCode by remember { mutableStateOf(true) }
    var variantOffset by remember { mutableIntStateOf(0) }

    var variants by remember { mutableStateOf<List<MnemonicPasswordGenerator.GenerationResult>>(emptyList()) }
    var selectedVariantIndex by remember { mutableIntStateOf(-1) }
    var showError by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    
    // ✅ НОВОЕ: AlertDialog для ошибок сохранения
    var showSaveErrorDialog by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun generateVariants() {
        validationError = null

        if (phrase.isBlank()) {
            variants = emptyList()
            validationError = "Введите мнемоническую фразу"
            return
        }

        if (includeServiceCode && serviceName.isBlank()) {
            variants = emptyList()
            validationError = "Введите название сервиса для кода сервиса"
            return
        }

        val options = MnemonicPasswordGenerator.GenerationOptions(
            phrase = phrase,
            serviceName = serviceName,
            targetLength = 16,
            includeLeet = includeLeet,
            includeServiceCode = includeServiceCode,
            includeRotationCode = includeRotationCode,
            variantOffset = variantOffset
        )

        variants = MnemonicPasswordGenerator.generateVariants(options, count = 5)
        selectedVariantIndex = -1
        
        if (variants.isEmpty()) {
            validationError = "Не удалось сгенерировать варианты без повторов. Попробуйте другую фразу."
        }
    }

    LaunchedEffect(phrase, serviceName, includeLeet, includeServiceCode, includeRotationCode) {
        variantOffset = 0
        generateVariants()
    }

    LaunchedEffect(variantOffset) {
        generateVariants()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AMPG v2 Генератор", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("AMPG v2 — Unique Mnemonic Flow", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Гарантированно без повторяющихся символов", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                label = { Text("Мнемоническая фраза *") },
                placeholder = { Text("например: моя кошка любит рыбу") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = serviceName,
                onValueChange = { serviceName = it },
                label = {
                    Text(if (includeServiceCode) "Название сервиса *" else "Название сервиса (необяз.)")
                },
                placeholder = { Text("например: Gmail") },
                modifier = Modifier.fillMaxWidth()
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Параметры", fontWeight = FontWeight.Bold)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeLeet, onCheckedChange = { includeLeet = it })
                        Text("Leet-замены (a→@, o→0, e→3...)", Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeServiceCode, onCheckedChange = { includeServiceCode = it })
                        Text("Код сервиса", Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeRotationCode, onCheckedChange = { includeRotationCode = it })
                        Text("Код ротации (MMYY)", Modifier.padding(start = 8.dp))
                    }
                }
            }

            if (validationError != null) {
                Text(validationError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            if (variants.isNotEmpty()) {
                Text(
                    "Текущий набор: №${variantOffset + 1}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            OutlinedButton(
                onClick = { variantOffset++ },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = variants.isNotEmpty() || (phrase.isNotBlank() && (!includeServiceCode || serviceName.isNotBlank()))
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ещё варианты", fontWeight = FontWeight.Medium)
            }

            if (variants.isNotEmpty()) {
                Text("Выберите вариант:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                variants.forEachIndexed { index, result ->
                    val isSelected = selectedVariantIndex == index
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        result.variantName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        result.password,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Сложность: ", fontSize = 10.sp)
                                        Text(
                                            result.strength.name,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (result.strength) {
                                                PasswordGenerator.Strength.VERY_STRONG -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                                PasswordGenerator.Strength.STRONG -> MaterialTheme.colorScheme.primary
                                                PasswordGenerator.Strength.MEDIUM -> MaterialTheme.colorScheme.tertiary
                                                PasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error
                                            }
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        result.mnemonicHint,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column {
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(result.password))
                                        android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp))
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedVariantIndex = index }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showError != null) {
                Text(showError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    if (selectedVariantIndex < 0 || selectedVariantIndex >= variants.size) {
                        showError = "Выберите вариант из списка"
                        return@Button
                    }
                    if (serviceName.isBlank()) {
                        saveErrorMessage = "Введите название сервиса для записи"
                        showSaveErrorDialog = true
                        return@Button
                    }
                    
                    val finalProfileId = effectiveProfileId
                    if (finalProfileId == null) {
                        saveErrorMessage = "Профиль не выбран. Вернитесь в список профилей."
                        showSaveErrorDialog = true
                        return@Button
                    }

                    val selected = variants[selectedVariantIndex]

                    if (PasswordValidator.hasDuplicateCharacters(selected.password)) {
                        saveErrorMessage = "Выбранный пароль содержит повторяющиеся символы. Выберите другой вариант."
                        showSaveErrorDialog = true
                        return@Button
                    }

                    val fingerprint = PasswordValidator.buildPasswordFingerprint(selected.password, context)

                    val entry = Entry.create(
                        service = serviceName,
                        username = "",
                        password = selected.password,
                        profileId = finalProfileId,
                        passwordFingerprint = fingerprint,
                        textHint = selected.mnemonicHint,
                        generationType = "mnemonic",
                        mnemonicPhraseHint = phrase,
                        mnemonicOptionsJson = null
                    )
                    
                    //  Ждём завершения сохранения
                    isSaving = true
                    viewModel.insertEntry(entry) { result ->
                        isSaving = false
                        when (result) {
                            is PasswordOperationResult.Success -> {
                                onBack()
                            }
                            is PasswordOperationResult.Error -> {
                                saveErrorMessage = result.message
                                showSaveErrorDialog = true
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = variants.isNotEmpty() && selectedVariantIndex >= 0 && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("Сохранение...")
                } else {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Сохранить")
                }
            }
        }
    }

    //  AlertDialog для ошибок сохранения
    if (showSaveErrorDialog) {
        AlertDialog(
            onDismissRequest = { showSaveErrorDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ошибка сохранения") },
            text = { Text(saveErrorMessage ?: "Неизвестная ошибка") },
            confirmButton = {
                TextButton(onClick = { showSaveErrorDialog = false }) {
                    Text("Понятно")
                }
            }
        )
    }
}
