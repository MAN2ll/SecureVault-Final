@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.viewmodel.VaultViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicGeneratorScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val currentProfileId by viewModel.currentProfileId.collectAsState()
    
    var phrase by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    var targetLength by remember { mutableIntStateOf(16) }
    var includeLeet by remember { mutableStateOf(true) }
    var includeServiceCode by remember { mutableStateOf(true) }
    var includeRotationCode by remember { mutableStateOf(true) }
    
    var generationResult by remember { mutableStateOf<MnemonicPasswordGenerator.GenerationResult?>(null) }
    var showError by remember { mutableStateOf<String?>(null) }

    fun generatePassword() {
        if (phrase.isBlank()) {
            showError = "Введите фразу"
            generationResult = null
            return
        }
        if (serviceName.isBlank()) {
            showError = "Введите название сервиса"
            generationResult = null
            return
        }
        
        val params = MnemonicPasswordGenerator.GenerationParams(
            phrase = phrase,
            serviceName = serviceName,
            targetLength = targetLength,
            includeLeet = includeLeet,
            includeServiceCode = includeServiceCode,
            includeRotationCode = includeRotationCode
        )
        
        generationResult = MnemonicPasswordGenerator.generate(params)
        showError = null
    }

    LaunchedEffect(phrase, serviceName, targetLength, includeLeet, includeServiceCode, includeRotationCode) {
        if (phrase.isNotBlank() && serviceName.isNotBlank()) {
            generatePassword()
        }
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
            // Информация об алгоритме
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
                label = { Text("Мнемоническая фраза") },
                placeholder = { Text("например: моя кошка любит рыбу") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = serviceName,
                onValueChange = { serviceName = it },
                label = { Text("Название сервиса") },
                placeholder = { Text("например: Gmail") },
                modifier = Modifier.fillMaxWidth()
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Параметры", fontWeight = FontWeight.Bold)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Длина: $targetLength", modifier = Modifier.fillMaxWidth(0.3f))
                        Slider(
                            value = targetLength.toFloat(),
                            onValueChange = { targetLength = it.toInt() },
                            valueRange = 12f..24f,
                            steps = 12,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                    
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

            // Результат
            generationResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Сгенерированный пароль:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            result.password,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        
                        Text("Подсказка:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        Text(result.mnemonicHint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Сложность: ", fontSize = 12.sp)
                            Text(
                                result.strength.name,
                                fontWeight = FontWeight.Bold,
                                color = when (result.strength) {
                                    MnemonicPasswordGenerator.Strength.VERY_STRONG -> MaterialTheme.colorScheme.primary
                                    MnemonicPasswordGenerator.Strength.STRONG -> MaterialTheme.colorScheme.tertiary
                                    MnemonicPasswordGenerator.Strength.MEDIUM -> MaterialTheme.colorScheme.error
                                    MnemonicPasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }

                // Шаги преобразования
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Шаги преобразования:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        result.steps.forEach { step ->
                            Text("• $step", fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }

            if (showError != null) {
                Text(showError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    val result = generationResult
                    if (result == null || serviceName.isBlank()) {
                        showError = "Сначала сгенерируйте пароль"
                        return@Button
                    }
                    if (currentProfileId == null) {
                        showError = "Профиль не выбран"
                        return@Button
                    }
                    
                    val entry = Entry.create(
                        service = serviceName,
                        username = "",
                        password = result.password,
                        profileId = currentProfileId!!,
                        textHint = phrase,
                        generationType = "mnemonic"
                    )
                    viewModel.insert(entry)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = generationResult != null
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Сохранить")
            }
        }
    }
}
