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
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicGeneratorScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val currentProfileId by viewModel.currentProfileId.collectAsState()
    var phrase by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var length by remember { mutableIntStateOf(12) }
    var useUpper by remember { mutableStateOf(false) }
    var useDigits by remember { mutableStateOf(false) }
    var useSpecial by remember { mutableStateOf(false) }
    var generatedPassword by remember { mutableStateOf("") }
    var textHint by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf<String?>(null) }

    fun generatePassword() {
        if (phrase.isBlank()) {
            generatedPassword = ""
            return
        }
        
        val consonants = "бвгджзйклмнпрстфхцчшщbcdfghjklmnpqrstvwxyz"
        val extracted = StringBuilder()
        for (ch in phrase) {
            if (ch.lowercaseChar().toString() in consonants) {
                extracted.append(ch.lowercaseChar())
            }
        }
        
        val map = mapOf(
            'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ж' to "zh",
            'з' to "z", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
            'н' to "n", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch"
        )
        
        var base = StringBuilder()
        for (ch in extracted.toString()) {
            if (ch in map) base.append(map[ch]) else base.append(ch)
        }
        
        if (!useUpper) base = StringBuilder(base.toString().lowercase())
        if (useDigits) base.append((10..99).random().toString())
        if (useSpecial) base.append(listOf("!", "@", "#", "$").random())
        
        val safeLength = length.coerceIn(8, 20)
        generatedPassword = when {
            base.length > safeLength -> base.toString().take(safeLength)
            base.length < safeLength -> {
                var result = base.toString()
                while (result.length < safeLength) result += "abcdefghijklmnopqrstuvwxyz".random()
                result
            }
            else -> base.toString()
        }
    }

    LaunchedEffect(phrase, length, useUpper, useDigits, useSpecial) {
        if (phrase.isNotBlank()) generatePassword()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Генератор из фразы", fontWeight = FontWeight.Bold) },
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
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                label = { Text("Ваша фраза") },
                placeholder = { Text("например: Мой кот любит молоко") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = service,
                onValueChange = { service = it },
                label = { Text("Сервис") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Логин") },
                modifier = Modifier.fillMaxWidth()
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Параметры", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Длина: $length", modifier = Modifier.fillMaxWidth(0.3f))
                        Slider(
                            value = length.toFloat(),
                            onValueChange = { length = it.toInt() },
                            valueRange = 8f..20f,
                            steps = 12,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useUpper, onCheckedChange = { useUpper = it })
                        Text("Заглавные", Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useDigits, onCheckedChange = { useDigits = it })
                        Text("Цифры", Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it })
                        Text("Спецсимволы", Modifier.padding(start = 8.dp))
                    }
                }
            }

            if (generatedPassword.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            generatedPassword,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Из первых согласных фразы",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Подсказка", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textHint,
                        onValueChange = { textHint = it },
                        label = { Text("Текстовая подсказка") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (showError != null) {
                Text(showError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    if (generatedPassword.isEmpty() || service.isBlank()) {
                        showError = "Заполните фразу и сервис"
                        return@Button
                    }
                    if (currentProfileId == null) {
                        showError = "Профиль не выбран"
                        return@Button
                    }
                    val entry = Entry.create(
                        service = service,
                        username = username,
                        password = generatedPassword,
                        profileId = currentProfileId!!,
                        textHint = textHint.ifBlank { null }
                    )
                    viewModel.insert(entry)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = generatedPassword.isNotEmpty()
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Сохранить")
            }
        }
    }
}
