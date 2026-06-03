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
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel
import kotlin.random.Random

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
    
    // ✅ ИСПРАВЛЕНО: Фильтры по умолчанию ВЫКЛЮЧЕНЫ
    var useUpper by remember { mutableStateOf(false) }
    var useDigits by remember { mutableStateOf(false) }
    var useSpecial by remember { mutableStateOf(false) }
    
    var generatedPassword by remember { mutableStateOf("") }
    var emojiHint by remember { mutableStateOf("") }
    var textHint by remember { mutableStateOf("") }
    var quickTags by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf<String?>(null) }

    // ✅ ИСПРАВЛЕННЫЙ АЛГОРИТМ
    fun generateSmartPassword(hint: String, length: Int, useUpper: Boolean, useDigits: Boolean, useSpecial: Boolean): String {
        // Русские согласные → английские
        val consonantMap = mapOf(
            'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ж' to "zh",
            'з' to "z", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
            'н' to "n", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch",
            'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Ж' to "Zh",
            'З' to "Z", 'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M",
            'Н' to "N", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T",
            'Ф' to "F", 'Х' to "Kh", 'Ц' to "Ts", 'Ч' to "Ch", 'Ш' to "Sh", 'Щ' to "Sch"
        )
        
        // Английские согласные
        val enConsonants = "bcdfghjklmnpqrstvwxyz"
        
        // Извлекаем согласные из подсказки
        val consonants = StringBuilder()
        for (ch in hint) {
            if (ch in consonantMap) {
                consonants.append(consonantMap[ch])
            } else if (ch.lowercaseChar() in enConsonants) {
                consonants.append(ch.lowercaseChar())
            }
        }
        
        // Если согласных нет — используем все буквы
        val baseChars = if (consonants.isEmpty()) {
            hint.filter { it.isLetterOrDigit() }.take(length)
        } else {
            consonants.toString().take(length * 2)
        }
        
        if (baseChars.isEmpty()) return PasswordGenerator.generate(length, useUpper, useDigits, useSpecial).password
        
        // Формируем пароль длиной length
        var result = baseChars.take(length)
        
        // Дополняем, если не хватает
        while (result.length < length) {
            result += enConsonants.random()
        }
        result = result.take(length)
        
        // Применяем фильтры
        val resultChars = result.toCharArray()
        
        // Заглавные (если включено — ~30% букв)
        if (useUpper) {
            for (i in resultChars.indices) {
                if (resultChars[i].isLetter() && Random.nextFloat() < 0.3f) {
                    resultChars[i] = resultChars[i].uppercaseChar()
                }
            }
        }
        
        // Цифры (если включено — ~25% букв)
        if (useDigits) {
            val digitReplacements = mapOf('a' to '4', 'e' to '3', 'i' to '1', 'o' to '0', 's' to '5', 't' to '7', 'b' to '8')
            for (i in resultChars.indices) {
                val lower = resultChars[i].lowercaseChar()
                if (lower in digitReplacements && Random.nextFloat() < 0.25f) {
                    resultChars[i] = digitReplacements[lower]!!
                }
            }
        }
        
        // Спецсимволы (если включено — ~20% букв)
        if (useSpecial) {
            val specialReplacements = mapOf('a' to '@', 's' to '$', 'o' to '0', 'i' to '!', 'e' to '3')
            for (i in resultChars.indices) {
                val lower = resultChars[i].lowercaseChar()
                if (lower in specialReplacements && Random.nextFloat() < 0.2f) {
                    resultChars[i] = specialReplacements[lower]!!
                }
            }
        }
        
        return String(resultChars)
    }

    // ✅ ОБНОВЛЁННАЯ ФУНКЦИЯ
    fun generatePassword() {
        val safeLength = length.coerceIn(8, 20)
        
        if (phrase.isBlank()) {
            // Режим 1: Обычный рандомный генератор
            generatedPassword = PasswordGenerator.generate(safeLength, useUpper, useDigits, useSpecial).password
        } else {
            // Режим 2: Умная генерация из подсказки
            generatedPassword = generateSmartPassword(phrase, safeLength, useUpper, useDigits, useSpecial)
        }
    }

    // ✅ Автоматическая перегенерация
    LaunchedEffect(phrase, length, useUpper, useDigits, useSpecial) {
        generatePassword()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Генератор паролей", fontWeight = FontWeight.Bold) },
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
            // Индикатор режима
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (phrase.isBlank()) "🎲 Режим: Случайный пароль" else "🧠 Режим: Мнемо-пароль из подсказки",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (phrase.isBlank()) 
                            "Оставьте поле пустым для обычного генератора" 
                        else 
                            "Согласные буквы превратятся в английский пароль",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                label = { Text("Подсказка-фраза (необязательно)") },
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
                    Text("Параметры пароля", fontWeight = FontWeight.Bold)
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
                        Text("Заглавные буквы (~30%)", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useDigits, onCheckedChange = { useDigits = it })
                        Text("Цифры (~25%: a→4, e→3...)", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it })
                        Text("Спецсимволы (~20%: a→@, s→$...)", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // Результат
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
                            text = if (phrase.isBlank()) "💡 Случайный пароль" else "💡 Из подсказки: согласные + фильтры",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Подсказки
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Дополнительные подсказки", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textHint,
                        onValueChange = { textHint = it },
                        label = { Text("Текстовая подсказка") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emojiHint,
                        onValueChange = { emojiHint = it },
                        label = { Text("Эмодзи-подсказка") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Быстрые теги:", fontSize = 12.sp)
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
                        showError = "Заполните сервис"
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
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Сохранить")
            }
        }
    }
}
