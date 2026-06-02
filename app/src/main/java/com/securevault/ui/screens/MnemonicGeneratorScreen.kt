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

    fun generateMnemonicPassword() {
        if (phrase.isBlank() || service.isBlank()) {
            showError = "Заполните фразу и сервис"
            return
        }
        
        // ✅ ИСПРАВЛЕНО: Реальная генерация на основе фразы
        val words = phrase.trim().split(Regex("\\s+"))
        var base = words.joinToString("") { word -> 
            word.firstOrNull()?.uppercaseChar()?.toString() ?: "" 
        }
        
        // Транслитерация русских букв
        base = base.replace("А", "A").replace("В", "B").replace("С", "C").replace("Е", "E")
                   .replace("К", "K").replace("М", "M").replace("Н", "H").replace("О", "O")
                   .replace("Р", "P").replace("Т", "T").replace("Х", "X").ifEmpty { "Pwd" }
        
        if (!useUpper) base = base.lowercase()
        if (useDigits) base += (10..99).random().toString()
        if (useSpecial) base += listOf("!", "@", "#", "$").random()
        
        val safeLength = length.coerceIn(8, 20)
        generatedPassword = if (base.length > safeLength) base.take(safeLength) else base.padEnd(safeLength, 'x')
        showError = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Запоминающийся пароль", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { androidx.compose.material3.Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(phrase, { phrase = it }, label = { Text("Ваша фраза (рус/англ)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(service, { service = it }, label = { Text("Сервис") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("Логин") }, modifier = Modifier.fillMaxWidth())

            var expandedProfile by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expandedProfile, { expandedProfile = !expandedProfile }) {
                OutlinedTextField(readOnly = true, value = profile.label, onValueChange = {}, label = { Text("Профиль") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedProfile) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expandedProfile, { expandedProfile = false }) { Profile.entries.forEach { p -> androidx.compose.material3.DropdownMenuItem({ Text(p.label) }, { profile = p; category = Categories.getFor(p).first() }) } }
            }

            var expandedCategory by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expandedCategory, { expandedCategory = !expandedCategory }) {
                OutlinedTextField(readOnly = true, value = category, onValueChange = {}, label = { Text("Категория") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedCategory) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expandedCategory, { expandedCategory = false }) { Categories.getFor(profile).forEach { c -> androidx.compose.material3.DropdownMenuItem({ Text(c) }, { category = c }) } }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Фильтры генерации", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Длина: $length", modifier = Modifier.fillMaxWidth(0.3f)); Slider(value = length.toFloat(), onValueChange = { length = it.toInt() }, valueRange = 8f..20f, steps = 12, modifier = Modifier.fillMaxWidth(0.7f)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useUpper, { useUpper = it }); Text("Заглавные буквы", Modifier.padding(start = 8.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useDigits, { useDigits = it }); Text("Цифры", Modifier.padding(start = 8.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useSpecial, { useSpecial = it }); Text
