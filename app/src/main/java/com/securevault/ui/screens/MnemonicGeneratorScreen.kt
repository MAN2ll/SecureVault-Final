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
fun MnemonicGeneratorScreen(onBack: () -> Unit, viewModel: VaultViewModel = hiltViewModel()) {
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
        if (phrase.isBlank()) { generatedPassword = ""; return }
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

    Scaffold(topBar = { TopAppBar(title = { Text("Запоминающийся пароль", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(phrase, { phrase = it }, label = { Text("Ваша фраза") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(service, { service = it }, label = { Text("Сервис") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("Логин") }, modifier = Modifier.fillMaxWidth())

            var expandedProfile by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expandedProfile, { expandedProfile = !expandedProfile }) {
                OutlinedTextField(true, profile.label, {}, label = { Text("Профиль") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedProfile) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expandedProfile, { expandedProfile = false }) {
                    Profile.entries.forEach { p -> DropdownMenuItem({ Text(p.label) }, { profile = p; category = Categories.getFor(p).first() }) }
                }
            }

            var expandedCategory by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expandedCategory, { expandedCategory = !expandedCategory }) {
                OutlinedTextField(true, category, {}, label = { Text("Категория") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedCategory) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expandedCategory, { expandedCategory = false }) {
                    Categories.getFor(profile).forEach { c -> DropdownMenuItem({ Text(c) }, { category = c }) }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Фильтры", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Длина: $length", Modifier.fillMaxWidth(0.3f)); Slider(length.toFloat(), { length = it.toInt() }, 8f..20f, steps = 12, Modifier.fillMaxWidth(0.7f)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useUpper, { useUpper = it }); Text("Заглавные", Modifier.padding(start = 8.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useDigits, { useDigits = it }); Text("Цифры", Modifier.padding(start = 8.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useSpecial, { useSpecial = it }); Text("Спецсимволы", Modifier.padding(start = 8.dp)) }
                }
            }

            if (generatedPassword.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(generatedPassword, fontSize = 20.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("💡 Первые буквы фразы + фильтры", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Подсказки", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(textHint, { textHint = it }, label = { Text("Текст") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(emojiHint, { emojiHint = it }, label = { Text("Эмодзи") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text("Теги:", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickTags.TAGS.forEach { tag ->
                            FilterChip(quickTags.contains(tag), { quickTags = if (quickTags.contains(tag)) quickTags.replace(tag, "").trim() else "$quickTags $tag".trim() }, label = { Text(tag) })
                        }
                    }
                }
            }

            if (showError != null) Text(showError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)

            Button(
                onClick = {
                    if (generatedPassword.isEmpty() || service.isBlank()) { showError = "Заполните поля"; return@Button }
                    viewModel.insert(Entry.create(service, username, generatedPassword, profile, category, emojiHint.ifBlank { null }, textHint.ifBlank { null }, quickTags.ifBlank { null }))
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = generatedPassword.isNotEmpty()
            ) { Icon(Icons.Default.Save, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text("Сохранить") }
        }
    }
}
