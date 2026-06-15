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
import com.securevault.data.Profile
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicGeneratorScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    var phrase by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(Profile.PERSONAL) }
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

        val base = StringBuilder()
        for (ch in extracted.toString()) {
            if (ch in map) {
                base.append(map[ch])
            } else {
                base.append(ch)
            }
        }

        var result = base.toString()
        if (!useUpper) result = result.lowercase()
        if (useDigits) result += (10..99).random().toString()
        if (useSpecial) result += listOf("!", "@", "#", "$").random()

        val safeLength = length.coerceIn(8, 20)
        generatedPassword = when {
            result.length > safeLength -> result.take(safeLength)
            result.length < safeLength -> {
                var padded = result
                while (padded.length < safeLength) {
                    padded += "abcdefghijklmnopqrstuvwxyz".random()
                }
                padded
            }
            else -> result
        }
    }

    LaunchedEffect(phrase, length, useUpper, useDigits, useSpecial) {
        if (phrase.isNotBlank()) generatePassword()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Генератор из фразы", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
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
            // Поле ввода фразы
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                label = { Text("Ваша фраза") },
                placeholder = { Text("например: Мой кот любит молоко") },
                modifier = Modifier.fillMaxWidth()
            )

            // Поле сервиса
            OutlinedTextField(
                value = service,
                onValueChange = { service = it },
                label = { Text("Сервис") },
                modifier = Modifier.fillMaxWidth()
            )

            // Поле логина
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Логин") },
                modifier = Modifier.fillMaxWidth()
            )

            // Выбор профиля
            var expandedProfile by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedProfile,
                onExpandedChange = { expandedProfile = !expandedProfile }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = profile.label,
                    onValueChange = {},
                    label = { Text("Профиль") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProfile) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedProfile,
                    onDismissRequest = { expandedProfile = false }
                ) {
                    Profile.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.label) },
                            onClick = {
                                profile = p
                                expandedProfile = false
                            }
                        )
                    }
                }
            }

            // Параметры генерации
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Параметры", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Длина: $length",
                            modifier = Modifier.fillMaxWidth(0.3f)
                        )
                        Slider(
                            value = length.toFloat(),
                            onValueChange = { length = it.toInt() },
                            valueRange = 8f..20f,
                            steps = 12,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useUpper,
                            onCheckedChange = { useUpper = it }
                        )
                        Text("Заглавные", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useDigits,
                            onCheckedChange = { useDigits = it }
                        )
                        Text("Цифры", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useSpecial,
                            onCheckedChange = { useSpecial = it }
                        )
                        Text("Спецсимволы", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // Результат генерации
            if (generatedPassword.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = generatedPassword,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Из первых согласных фразы",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Подсказка
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Подсказка", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textHint,
                        onValueChange = { textHint = it },
                        label = { Text("Текстовая подсказка") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Ошибка
            if (showError != null) {
                Text(
                    text = showError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            // Кнопка сохранения
            Button(
                onClick = {
                    if (generatedPassword.isEmpty() || service.isBlank()) {
                        showError = "Заполните фразу и сервис"
                        return@Button
                    }
                    val entry = Entry.create(
                        service = service,
                        username = username,
                        password = generatedPassword,
                        profile = profile,
                        textHint = textHint.ifBlank { null }
                    )
                    viewModel.insert(entry)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = generatedPassword.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("Сохранить")
            }
        }
    }
}
