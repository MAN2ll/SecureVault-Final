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

    // ✅ АВТОПЕРЕГЕНЕРАЦИЯ ПРИ ИЗМЕНЕНИИ ПАРАМЕТРОВ
    LaunchedEffect(phrase, length, useUpper, useDigits, useSpecial) {
        if (phrase.isNotBlank()) {
            generateMnemonicPassword()
        }
    }

    fun generateMnemonicPassword() {
        if (phrase.isBlank()) {
            generatedPassword = ""
            return
        }
        
        val words = phrase.trim().split(Regex("\\s+"))
        var base = words.joinToString("") { word -> 
            word.firstOrNull()?.uppercaseChar()?.toString() ?: "" 
        }
        
        // Транслитерация только для русских букв
        base = base.replace("А", "A").replace("В", "B").replace("С", "C").replace("Е", "E")
                   .replace("К", "K").replace("М", "M").replace("Н", "H").replace("О", "O")
                   .replace("Р", "P").replace("Т", "T").replace("Х", "X")
        
        // ✅ ПРИМЕНЕНИЕ ФИЛЬТРОВ
        if (!useUpper) {
            base = base.lowercase()
        }
        
        // Если цифры включены - добавляем, иначе нет
        if (useDigits) {
            base += (10..99).random().toString()
        }
        
        // Если спецсимволы включены - добавляем, иначе нет
        if (useSpecial) {
            base += listOf("!", "@", "#", "$").random()
        }
        
        val safeLength = length.coerceIn(8, 20)
        
        // Подгонка под длину
        generatedPassword = if (base.length > safeLength) {
            base.take(safeLength)
        } else if (base.length < safeLength) {
            // Дополняем случайными строчными буквами
            val chars = "abcdefghijklmnopqrstuvwxyz"
            var result = base
            while (result.length < safeLength) {
                result += chars.random()
            }
            result
        } else {
            base
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Запоминающийся пароль", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = phrase, 
                onValueChange = { phrase = it }, 
                label = { Text("Ваша фраза (рус/англ)") }, 
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
            ExposedDropdownMenuBox(expanded = expandedProfile, onExpandedChange = { expandedProfile = !expandedProfile }) {
                OutlinedTextField(
                    readOnly = true,
                    value = profile.label,
                    onValueChange = {},
                    label = { Text("Профиль") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProfile) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expandedProfile, onDismissRequest = { expandedProfile = false }) {
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
            ExposedDropdownMenuBox(expanded = expandedCategory, onExpandedChange = { expandedCategory = !expandedCategory }) {
                OutlinedTextField(
                    readOnly = true,
                    value = category,
                    onValueChange = {},
                    label = { Text("Категория") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expandedCategory, onDismissRequest = { expandedCategory = false }) {
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Фильтры генерации", fontWeight = FontWeight.Bold)
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
                        Checkbox(useUpper, { useUpper = it })
                        Text("Заглавные буквы", Modifier.padding(start = 8.dp)) 
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { 
                        Checkbox(useDigits, { useDigits = it })
                        Text("Цифры", Modifier.padding(start = 8.dp)) 
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { 
                        Checkbox(useSpecial, { useSpecial = it })
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
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, 
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "💡 Основа: первые буквы фразы + фильтры", 
                            fontSize = 12.sp, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant, 
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Мнемонические подсказки", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textHint, 
                        onValueChange = { textHint = it }, 
                        label = { Text("Текстовая подсказка") }, 
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emojiHint, 
                        onValueChange = { emojiHint = it }, 
                        label = { Text("Ключевые слова → эмодзи") }, 
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Быстрые теги:", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
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
                Text(showError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

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
                        category = category, 
                        emojiHint = emojiHint.ifBlank { null }, 
                        textHint = textHint.ifBlank { null }, 
                        quickTags = quickTags.ifBlank { null }
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
