@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Categories
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.data.QuickTags
import com.securevault.viewmodel.VaultViewModel

@Composable
fun MnemonicGeneratorScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    var phrase by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(Profile.PERSONAL) }
    var category by remember { mutableStateOf("Общее") }
    var length by remember { mutableIntStateOf(12) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSpecial by remember { mutableStateOf(true) }
    var generatedPassword by remember { mutableStateOf("") }
    var emojiHint by remember { mutableStateOf("") }
    var textHint by remember { mutableStateOf("") }
    var quickTags by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf<String?>(null) }

    fun generatePassword() {
        if (phrase.isBlank()) {
            generatedPassword = ""
            return
        }
        val words = phrase.trim().split(Regex("\\s+"))
        var base = words.joinToString("") { it.firstOrNull()?.uppercaseChar()?.toString() ?: "" }
        base = base.replace("А", "A").replace("В", "B").replace("С", "C").replace("Е", "E")
                   .replace("К", "K").replace("М", "M").replace("Н", "H").replace("О", "O")
                   .replace("Р", "P").replace("Т", "T").replace("Х", "X")
        if (!useUpper) base = base.lowercase()
        if (useDigits) base += (10..99).random().toString()
        if (useSpecial) base += listOf("!", "@", "#", "$").random()
        val safeLength = length.coerceIn(8, 20)
        generatedPassword = when {
            base.length > safeLength -> base.take(safeLength)
            base.length < safeLength -> {
                var result = base
                while (result.length < safeLength) result += "abcdefghijklmnopqrstuvwxyz".random()
                result
            }
            else -> base
        }
    }

    LaunchedEffect(phrase, length, useUpper, useDigits, useSpecial) {
        if (phrase.isNotBlank()) generatePassword()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Запоминающийся пароль", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Назад")
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
            // ✅ ПРАВИЛЬНЫЕ ВЫЗОВЫ OutlinedTextField
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                label = { Text("Ваша фраза") },
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

            // Профиль
            var expandedProfile by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedProfile,
                onExpandedChange = { expandedProfile = !expandedProfile }
            ) {
                OutlinedTextField(
                    value = profile.label,
                    onValueChange = {},
                    readOnly = true,
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
                                category = Categories.getFor(p).first()
                                expandedProfile = false
                            }
                        )
                    }
                }
            }

            // Категория
            var expandedCategory by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedCategory,
                onExpandedChange = { expandedCategory = !expandedCategory }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Категория") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false }
                ) {
                    Categories.getFor(profile).forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c) },
                            onClick = {
                                category = c
                                expandedCategory = false
                            }
                        )
                    }
                }
            }

            // Фильтры
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Фильтры", fontWeight = FontWeight.Bold)
                    
                    // ✅ ПРАВИЛЬНЫЙ ВЫЗОВ Slider
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
                        Checkbox(checked = useUpper, onCheckedChange = { useUpper = it })
                        Text("Заглавные", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useDigits, onCheckedChange = { useDigits = it })
                        Text("Цифры", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it })
                        Text("Спецсимволы", modifier = Modifier.padding(start = 8.dp))
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
                            text = generatedPassword,
                            fontSize = 20.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "💡 Первые буквы фразы + фильтры",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Подсказки", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textHint,
                        onValueChange = { textHint = it },
                        label = { Text("Текст") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emojiHint,
                        onValueChange = { emojiHint = it },
                        label = { Text("Эмодзи") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Теги:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickTags.TAGS.forEach { tag ->
                            FilterChip(
                                selected = quickTags.contains(tag),
                                onClick = {
                                    quickTags = if (quickTags.contains(tag))
                                        quickTags.replace(tag, "").trim()
                                    else
                                        "$quickTags $tag".trim()
                                },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }

            if (showError != null) {
                Text(
                    text = showError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = {
                    if (generatedPassword.isEmpty() || service.isBlank()) {
                        showError = "Заполните поля"
                        return@Button
                    }
                    viewModel.insert(
                        Entry.create(
                            service = service,
                            username = username,
                            password = generatedPassword,
                            profile = profile,
                            category = category,
                            emojiHint = emojiHint.ifBlank { null },
                            textHint = textHint.ifBlank { null },
                            quickTags = quickTags.ifBlank { null }
                        )
                    )
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
