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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
    id: String?,
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val existingEntry = remember { id?.let { viewModel.entries.value.find { e -> e.id == id } } }
    
    var service by remember { mutableStateOf(existingEntry?.service ?: "") }
    var username by remember { mutableStateOf(existingEntry?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(existingEntry?.profile ?: Profile.PERSONAL) }
    var category by remember { mutableStateOf(existingEntry?.category ?: "Общее") }
    var url by remember { mutableStateOf(existingEntry?.url ?: "") }
    var notes by remember { mutableStateOf(existingEntry?.notes ?: "") }
    var emojiHint by remember { mutableStateOf(existingEntry?.emojiHint ?: "") }
    var textHint by remember { mutableStateOf(existingEntry?.textHint ?: "") }
    var quickTags by remember { mutableStateOf(existingEntry?.quickTags ?: "") }
    var rotationEnabled by remember { mutableStateOf(existingEntry?.rotationEnabled ?: false) }
    var rotationMonths by remember { mutableIntStateOf(existingEntry?.rotationPeriodMonths ?: 6) }
    var isFavorite by remember { mutableStateOf(existingEntry?.isFavorite ?: false) }
    
    var showPassword by remember { mutableStateOf(false) }
    var showGeneratorDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "Новая запись" else "Редактировать", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = {
                    IconButton({ isFavorite = !isFavorite }) { Icon(if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star, null, tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton({
                        if (service.isBlank() || password.isBlank()) {
                            showError = "Заполните обязательные поля"
                            return@IconButton
                        }
                        val entry = if (existingEntry != null) {
                            existingEntry.copy(service = service, username = username, encryptedPassword = com.securevault.utils.CryptoUtils.encrypt(password), profile = profile, category = category, url = url.ifBlank { null }, notes = notes.ifBlank { null }, emojiHint = emojiHint.ifBlank { null }, textHint = textHint.ifBlank { null }, quickTags = quickTags.ifBlank { null }, rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationMonths, isFavorite = isFavorite, lastChanged = System.currentTimeMillis())
                        } else {
                            Entry.create(service, username, password, profile, category, url.ifBlank { null }, notes.ifBlank { null }, emojiHint.ifBlank { null }, textHint.ifBlank { null }, quickTags.ifBlank { null }, rotationEnabled, rotationMonths, isFavorite)
                        }
                        viewModel.insert(entry)
                        onBack()
                    }) { Icon(Icons.Default.Check, "Сохранить") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showError != null) { Text(showError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp); showError = null }
            
            // Профиль
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                OutlinedTextField(readOnly = true, value = profile.label, onValueChange = {}, label = { Text("Профиль") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded, { expanded = false }) { Profile.entries.forEach { p -> DropdownMenuItem({ Text(p.label) }, { profile = p; category = Categories.getFor(p).first() }) } }
            }
            
            // Категория
            ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                OutlinedTextField(readOnly = true, value = category, onValueChange = {}, label = { Text("Категория") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded, { expanded = false }) { Categories.getFor(profile).forEach { c -> DropdownMenuItem({ Text(c) }, { category = c }) } }
            }
            
            OutlinedTextField(service, { service = it }, label = { Text("Сервис *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("Логин / Email") }, modifier = Modifier.fillMaxWidth())
            
            // Пароль с кнопками
            OutlinedTextField(password, { password = it }, label = { Text("Пароль *") }, visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(), trailingIcon = { Row { IconButton({ showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } IconButton({ showGeneratorDialog = true }) { Icon(Icons.Default.Casino, "Генератор") } } }, modifier = Modifier.fillMaxWidth())
            
            OutlinedTextField(url, { url = it }, label = { Text("URL (необязательно)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Заметки") }, modifier = Modifier.fillMaxWidth().height(100.dp))
            
            // Ротация
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Напоминание о смене пароля", fontWeight = FontWeight.Medium); Switch(rotationEnabled, { rotationEnabled = it }) }
                    if (rotationEnabled) {
                        Spacer(Modifier.height(12.dp))
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                            OutlinedTextField(readOnly = true, value = "$rotationMonths мес.", onValueChange = {}, label = { Text("Менять каждые") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded, { expanded = false }) { listOf(3, 6, 12).forEach { m -> DropdownMenuItem({ Text("$m мес.") }, { rotationMonths = m }) } }
                        }
                    }
                }
            }
            
            // Мнемоподсказки
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Мнемонические подсказки", fontWeight = FontWeight.Medium); Spacer(Modifier.height(8.dp))
                    OutlinedTextField(textHint, { textHint = it }, label = { Text("Текстовая подсказка") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(emojiHint, { emojiHint = it }, label = { Text("Ключевые слова → эмодзи") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text("Быстрые теги:", fontSize = 12.sp); Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickTags.TAGS.forEach { tag ->
                            FilterChip(quickTags.contains(tag), { quickTags = if (quickTags.contains(tag)) quickTags.replace(tag, "").trim() else "$quickTags $tag".trim() }, label = { Text(tag) })
                        }
                    }
                }
            }
        }
    }
    
    // Диалог генератора
    if (showGeneratorDialog) {
        PasswordGeneratorDialog(onDismiss = { showGeneratorDialog = false }, onGenerated = { pwd -> password = pwd; showGeneratorDialog = false })
    }
}

@Composable
private fun PasswordGeneratorDialog(onDismiss: () -> Unit, onGenerated: (String) -> Unit) {
    var length by remember { mutableIntStateOf(16) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSpecial by remember { mutableStateOf(true) }
    var generatedPwd by remember { mutableStateOf("") }
    
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Генератор паролей") }, text = { Column { if (generatedPwd.isNotEmpty()) { Text(generatedPwd, fontSize = 18.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(vertical = 12.dp)) } Slider(length.toFloat(), { length = it.toInt() }, 8f..20f, steps = 12); Text("Длина: $length"); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useUpper, { useUpper = it }); Text("Заглавные (A-Z)", Modifier.padding(start = 8.dp)) }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useDigits, { useDigits = it }); Text("Цифры (0-9)", Modifier.padding(start = 8.dp)) }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useSpecial, { useSpecial = it }); Text("Символы (!@#...)", Modifier.padding(start = 8.dp)) } } }, confirmButton = { Button({ onGenerated(generatedPwd.takeIf { it.isNotEmpty() } ?: PasswordGenerator.generate(length, useUpper, useDigits, useSpecial).password) }) { Text("Использовать") } }, dismissButton = { TextButton(onDismiss) { Text("Отмена") } }, modifier = Modifier.fillMaxWidth(0.9f))
}
