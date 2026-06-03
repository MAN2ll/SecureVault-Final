@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Categories
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.data.QuickTags
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun EntryEditorScreen(
    id: String?,
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val existingEntry = remember { id?.let { viewModel.entries.value.find { e -> e.id == it } } }
    
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "Новая запись" else "Редактировать", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = {
                    IconButton(onClick = { isFavorite = !isFavorite }) { 
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                            contentDescription = null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    }
                    IconButton(onClick = {
                        if (service.isBlank() || password.isBlank()) {
                            showError = "Заполните обязательные поля"
                            return@IconButton
                        }
                        
                        val encryptedPwd = CryptoUtils.encrypt(password)
                        
                        val entry = if (existingEntry != null) {
                            existingEntry.copy(
                                service = service,
                                username = username,
                                encryptedPassword = encryptedPwd,
                                profile = profile,
                                category = category,
                                url = url.ifBlank { null },
                                notes = notes.ifBlank { null },
                                emojiHint = emojiHint.ifBlank { null },
                                textHint = textHint.ifBlank { null },
                                quickTags = quickTags.ifBlank { null },
                                rotationEnabled = rotationEnabled,
                                rotationPeriodMonths = rotationMonths,
                                isFavorite = isFavorite,
                                lastChanged = System.currentTimeMillis()
                            )
                        } else {
                            Entry.create(
                                service = service,
                                username = username,
                                password = password,
                                profile = profile,
                                category = category,
                                url = url.ifBlank { null },
                                notes = notes.ifBlank { null },
                                emojiHint = emojiHint.ifBlank { null },
                                textHint = textHint.ifBlank { null },
                                quickTags = quickTags.ifBlank { null },
                                rotationEnabled = rotationEnabled,
                                rotationPeriodMonths = rotationMonths,
                                isFavorite = isFavorite
                            )
                        }
                        
                        viewModel.insert(entry)
                        onBack()
                    }) { 
                        Icon(Icons.Default.Check, "Сохранить") 
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
            if (showError != null) { 
                Text(showError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                showError = null 
            }
            
            var expandedProfile by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expandedProfile, { expandedProfile = !expandedProfile }) {
                OutlinedTextField(
                    readOnly = true, 
                    value = profile.label, 
                    onValueChange = {}, 
                    label = { Text("Профиль") }, 
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedProfile) }, 
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expandedProfile, { expandedProfile = false }) { 
                    Profile.entries.forEach { p -> 
                        DropdownMenuItem(
                            text = { Text(p.label) }, 
                            onClick = { profile = p; category = Categories.getFor(p).first() }
                        ) 
                    } 
                }
            }
            
            var expandedCategory by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expandedCategory, { expandedCategory = !expandedCategory }) {
                OutlinedTextField(
                    readOnly = true, 
                    value = category, 
                    onValueChange = {}, 
                    label = { Text("Категория") }, 
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedCategory) }, 
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expandedCategory, { expandedCategory = false }) { 
                    Categories.getFor(profile).forEach { c -> 
                        DropdownMenuItem(
                            text = { Text(c) }, 
                            onClick = { category = c }
                        ) 
                    } 
                }
            }
            
            OutlinedTextField(service, { service = it }, label = { Text("Сервис *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("Логин / Email") }, modifier = Modifier.fillMaxWidth())
            
            OutlinedTextField(
                value = password, 
                onValueChange = { password = it }, 
                label = { Text("Пароль *") }, 
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), 
                trailingIcon = { 
                    Row { 
                        IconButton({ showPassword = !showPassword }) { 
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) 
                        } 
                        IconButton({ showGeneratorDialog = true }) { 
                            Icon(Icons.Default.Casino, "Генератор") 
                        } 
                    } 
                }, 
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(url, { url = it }, label = { Text("URL (необязательно)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Заметки") }, modifier = Modifier.fillMaxWidth().height(100.dp))
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                        Text("Напоминание о смене пароля", fontWeight = FontWeight.Medium)
                        Switch(rotationEnabled, { rotationEnabled = it }) 
                    }
                    if (rotationEnabled) {
                        Spacer(Modifier.height(12.dp))
                        var expandedMonths by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expandedMonths, { expandedMonths = !expandedMonths }) {
                            OutlinedTextField(
                                readOnly = true, 
                                value = "$rotationMonths мес.", 
                                onValueChange = {}, 
                                label = { Text("Менять каждые") }, 
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedMonths) }, 
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expandedMonths, { expandedMonths = false }) { 
                                listOf(3, 6, 12).forEach { m -> 
                                    DropdownMenuItem(
                                        text = { Text("$m мес.") }, 
                                        onClick = { rotationMonths = m }
                                    ) 
                                } 
                            }
                        }
                    }
                }
            }
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Мнемонические подсказки", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(textHint, { textHint = it }, label = { Text("Текстовая подсказка") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(emojiHint, { emojiHint = it }, label = { Text("Ключевые слова → эмодзи") }, modifier = Modifier.fillMaxWidth())
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
                                onClick = { quickTags = if (quickTags.contains(tag)) quickTags.replace(tag, "").trim() else "$quickTags $tag".trim() }, 
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    if (showGeneratorDialog) {
        PasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false }, 
            onGenerated = { pwd -> 
                password = pwd
                showGeneratorDialog = false 
            }
        )
    }
}

@Composable
private fun PasswordGeneratorDialog(
    onDismiss: () -> Unit, 
    onGenerated: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var length by remember { mutableIntStateOf(16) }
    
    var useUpper by remember { mutableStateOf(false) }
    var useDigits by remember { mutableStateOf(false) }
    var useSpecial by remember { mutableStateOf(false) }
    
    var generatedPwd by remember { mutableStateOf("") }
    var mnemonicPhrase by remember { mutableStateOf("") }
    
    fun generateSimplePassword(): String {
        return PasswordGenerator.generate(length, useUpper, useDigits, useSpecial).password
    }
    
    fun generateMnemonicPassword(phrase: String): String {
        if (phrase.isBlank()) return ""
        
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
        
        val enConsonants = "bcdfghjklmnpqrstvwxyz"
        val consonants = StringBuilder()
        for (ch in phrase) {
            if (ch in consonantMap) {
                consonants.append(consonantMap[ch])
            } else if (ch.lowercaseChar() in enConsonants) {
                consonants.append(ch.lowercaseChar())
            }
        }
        
        val baseChars = if (consonants.isEmpty()) {
            phrase.filter { it.isLetterOrDigit() }.take(length)
        } else {
            consonants.toString().take(length * 2)
        }
        
        if (baseChars.isEmpty()) return generateSimplePassword()
        
        var result = baseChars.take(length)
        while (result.length < length) {
            result += enConsonants.random()
        }
        result = result.take(length)
        
        val resultChars = result.toCharArray()
        
        if (useUpper) {
            for (i in resultChars.indices) {
                if (resultChars[i].isLetter() && Random.nextFloat() < 0.3f) {
                    resultChars[i] = resultChars[i].uppercaseChar()
                }
            }
        }
        
        if (useDigits) {
            val digitReplacements = mapOf('a' to '4', 'e' to '3', 'i' to '1', 'o' to '0', 's' to '5', 't' to '7', 'b' to '8')
            for (i in resultChars.indices) {
                val lower = resultChars[i].lowercaseChar()
                if (lower in digitReplacements && Random.nextFloat() < 0.25f) {
                    resultChars[i] = digitReplacements[lower]!!
                }
            }
        }
        
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
    
    LaunchedEffect(selectedTab, length, useUpper, useDigits, useSpecial, mnemonicPhrase) {
        generatedPwd = if (selectedTab == 0) {
            generateSimplePassword()
        } else {
            generateMnemonicPassword(mnemonicPhrase)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text("Генератор паролей") }, 
        text = { 
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Обычный") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Мнемонический") }
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                if (selectedTab == 1) {
                    OutlinedTextField(
                        value = mnemonicPhrase,
                        onValueChange = { mnemonicPhrase = it },
                        label = { Text("Подсказка-фраза") },
                        placeholder = { Text("например: Мой кот любит молоко") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
                
                if (generatedPwd.isNotEmpty()) {
                    Text(
                        text = generatedPwd,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Длина: $length", modifier = Modifier.weight(1f))
                    Slider(
                        value = length.toFloat(),
                        onValueChange = { length = it.toInt() },
                        valueRange = 8f..20f,
                        steps = 12,
                        modifier = Modifier.weight(2f)
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    Checkbox(checked = useUpper, onCheckedChange = { useUpper = it })
                    Text("Заглавные (~30%)", Modifier.padding(start = 8.dp)) 
                }
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    Checkbox(checked = useDigits, onCheckedChange = { useDigits = it })
                    Text("Цифры (~25%)", Modifier.padding(start = 8.dp)) 
                }
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it })
                    Text("Спецсимволы (~20%)", Modifier.padding(start = 8.dp)) 
                } 
            } 
        }, 
        confirmButton = { 
            Button(
                onClick = { 
                    val finalPwd = if (generatedPwd.isNotEmpty()) generatedPwd else generateSimplePassword()
                    onGenerated(finalPwd) 
                }
            ) { 
                Text("Использовать") 
            } 
        }, 
        dismissButton = { 
            TextButton(onDismiss) { Text("Отмена") } 
        }, 
        modifier = Modifier.fillMaxWidth(0.9f)
    )
}
