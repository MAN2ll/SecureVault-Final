@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
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
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicGeneratorScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val currentProfileId by viewModel.currentProfileId.collectAsState()
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
    }

    LaunchedEffect(phrase, serviceName, includeLeet, includeServiceCode, includeRotationCode, variantOffset) {
        generateVariants()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AMPG Генератор", fontWeight = FontWeight.Bold) },
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
                    Text("AMPG v1 — Адаптивная мнемоническая генерация", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Детерминированный алгоритм: одинаковые входные данные = одинаковый пароль", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                        Text("Leet-замены (a→@, o→0...)", Modifier.padding(start = 8.dp))
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

            OutlinedButton(
                onClick = { variantOffset++ },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = variants.isNotEmpty() || (phrase.isNotBlank() && (!includeServiceCode || serviceName.isNotBlank()))
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ещё варианты (набор #$variantOffset)", fontWeight = FontWeight.Medium)
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
                        showError = "Введите название сервиса для записи"
                        return@Button
                    }
                    if (currentProfileId == null) {
                        showError = "Профиль не выбран"
                        return@Button
                    }
                    
                    val selected = variants[selectedVariantIndex]
                    
                    val entry = Entry.create(
                        service = serviceName,
                        username = "",
                        password = selected.password,
                        profileId = currentProfileId!!,
                        textHint = selected.mnemonicHint,
                        generationType = "mnemonic"
                    )
                    viewModel.insert(entry)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = variants.isNotEmpty() && selectedVariantIndex >= 0
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Сохранить")
            }
        }
    }
}
