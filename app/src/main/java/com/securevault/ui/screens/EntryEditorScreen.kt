@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.securevault.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import kotlin.random.Random

// ===== МЕТОДЫ ШИФРОВАНИЯ =====
enum class CipherMethod(val label: String, val icon: String, val description: String) {
    BASIC("Базовый", "🔤", "Согласные → английские буквы"),
    CAESAR("Сдвиг Цезаря", "🔄", "Каждая буква сдвигается на N позиций"),
    REVERSE("Реверс", "↔️", "Строка переворачивается задом наперёд"),
    MIRROR("Зеркало", "🪞", "Пароль + его зеркальное отражение"),
    CHESS("Шахматный", "♟️", "Чередование ЗАГЛАВНЫХ и строчных")
}

// ===== КЛАСС ДЛЯ ВИЗУАЛИЗАЦИИ ШАГОВ =====
data class TransformationStep(
    val label: String,
    val value: String,
    val color: Color = Color.Unspecified
)

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
                                service = service, username = username, encryptedPassword = encryptedPwd,
                                profile = profile, category = category,
                                url = url.ifBlank { null }, notes = notes.ifBlank { null },
                                emojiHint = emojiHint.ifBlank { null }, textHint = textHint.ifBlank { null },
                                quickTags = quickTags.ifBlank { null },
                                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationMonths,
                                isFavorite = isFavorite, lastChanged = System.currentTimeMillis()
                            )
                        } else {
                            Entry.create(
                                service = service, username = username, password = password,
                                profile = profile, category = category,
                                url = url.ifBlank { null }, notes = notes.ifBlank { null },
                                emojiHint = emojiHint.ifBlank { null }, textHint = textHint.ifBlank { null },
                                quickTags = quickTags.ifBlank { null },
                                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationMonths,
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
                    readOnly = true, value = profile.label, onValueChange = {}, 
                    label = { Text("Профиль") }, 
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedProfile) }, 
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expandedProfile, { expandedProfile = false }) { 
                    Profile.entries.forEach { p -> 
                        DropdownMenuItem(text = { Text(p.label) }, onClick = { profile = p; category = Categories.getFor(p).first() }) 
                    } 
                }
            }
            
            var expandedCategory by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expandedCategory, { expandedCategory = !expandedCategory }) {
                OutlinedTextField(
                    readOnly = true, value = category, onValueChange = {}, 
                    label = { Text("Категория") }, 
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedCategory) }, 
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expandedCategory, { expandedCategory = false }) { 
                    Categories.getFor(profile).forEach { c -> 
                        DropdownMenuItem(text = { Text(c) }, onClick = { category = c }) 
                    } 
                }
            }
            
            OutlinedTextField(service, { service = it }, label = { Text("Сервис *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("Логин / Email") }, modifier = Modifier.fillMaxWidth())
            
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Пароль *") }, 
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
                                readOnly = true, value = "$rotationMonths мес.", onValueChange = {}, 
                                label = { Text("Менять каждые") }, 
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedMonths) }, 
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expandedMonths, { expandedMonths = false }) { 
                                listOf(3, 6, 12).forEach { m -> 
                                    DropdownMenuItem(text = { Text("$m мес.") }, onClick = { rotationMonths = m }) 
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        AdvancedPasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false }, 
            onGenerated = { pwd -> 
                password = pwd
                showGeneratorDialog = false 
            }
        )
    }
}

// ===== ПРОДВИНУТЫЙ ГЕНЕРАТОР С ШИФРОВАНИЕМ =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedPasswordGeneratorDialog(
    onDismiss: () -> Unit, 
    onGenerated: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var length by remember { mutableIntStateOf(16) }
    var useUpper by remember { mutableStateOf(false) }
    var useDigits by remember { mutableStateOf(false) }
    var useSpecial by remember { mutableStateOf(false) }
    
    var generatedPwd by remember { mutableStateOf("") }
    var mnemonicPhrase by remember { mutableStateOf("") }
    var cipherMethod by remember { mutableStateOf(CipherMethod.BASIC) }
    var caesarShift by remember { mutableIntStateOf(2) }
    var showSteps by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf<List<TransformationStep>>(emptyList()) }
    
    // ===== ФУНКЦИИ ШИФРОВАНИЯ =====
    
    // Транслитерация русских согласных
    fun transliterateConsonants(text: String): String {
        val consonantMap = mapOf(
            'б' to 'b', 'в' to 'v', 'г' to 'g', 'д' to 'd', 'ж' to 'z',
            'з' to 'z', 'й' to 'y', 'к' to 'k', 'л' to 'l', 'м' to 'm',
            'н' to 'n', 'п' to 'p', 'р' to 'r', 'с' to 's', 'т' to 't',
            'ф' to 'f', 'х' to 'h', 'ц' to 'c', 'ч' to 'c', 'ш' to 's', 'щ' to 's',
            'Б' to 'B', 'В' to 'V', 'Г' to 'G', 'Д' to 'D', 'Ж' to 'Z',
            'З' to 'Z', 'Й' to 'Y', 'К' to 'K', 'Л' to 'L', 'М' to 'M',
            'Н' to 'N', 'П' to 'P', 'Р' to 'R', 'С' to 'S', 'Т' to 'T',
            'Ф' to 'F', 'Х' to 'H', 'Ц' to 'C', 'Ч' to 'C', 'Ш' to 'S', 'Щ' to 'S'
        )
        val result = StringBuilder()
        for (ch in text) {
            if (ch in consonantMap) {
                result.append(consonantMap[ch])
            } else if (ch.lowercaseChar() in "bcdfghjklmnpqrstvwxyz") {
                result.append(ch.lowercaseChar())
            }
        }
        return result.toString()
    }
    
    // Сдвиг Цезаря
    fun caesarShift(text: String, shift: Int): String {
        return text.map { ch ->
            when {
                ch in 'a'..'z' -> ('a' + (ch - 'a' + shift) % 26)
                ch in 'A'..'Z' -> ('A' + (ch - 'A' + shift) % 26)
                else -> ch
            }
        }.joinToString("")
    }
    
    // Реверс строки
    fun reverseString(text: String): String = text.reversed()
    
    // Зеркало: строка + реверс
    fun mirror(text: String): String = text + text.reversed()
    
    // Шахматный: чередование ЗАГЛАВНЫХ и строчных
    fun chessPattern(text: String): String {
        return text.mapIndexed { index, ch ->
            if (ch.isLetter()) {
                if (index % 2 == 0) ch.uppercaseChar() else ch.lowercaseChar()
            } else ch
        }.joinToString("")
    }
    
    // Применение фильтров
    fun applyFilters(text: String): String {
        val chars = text.toCharArray()
        if (useUpper) {
            for (i in chars.indices) {
                if (chars[i].isLetter() && Random.nextFloat() < 0.3f) {
                    chars[i] = chars[i].uppercaseChar()
                }
            }
        }
        if (useDigits) {
            val digitReplacements = mapOf('a' to '4', 'e' to '3', 'i' to '1', 'o' to '0', 's' to '5', 't' to '7', 'b' to '8')
            for (i in chars.indices) {
                val lower = chars[i].lowercaseChar()
                if (lower in digitReplacements && Random.nextFloat() < 0.25f) {
                    chars[i] = digitReplacements[lower]!!
                }
            }
        }
        if (useSpecial) {
            val specialReplacements = mapOf('a' to '@', 's' to '$', 'o' to '0', 'i' to '!', 'e' to '3')
            for (i in chars.indices) {
                val lower = chars[i].lowercaseChar()
                if (lower in specialReplacements && Random.nextFloat() < 0.2f) {
                    chars[i] = specialReplacements[lower]!!
                }
            }
        }
        return String(chars)
    }
    
    // Генерация с пошаговой визуализацией
    fun generateMnemonicWithSteps(phrase: String): String {
        if (phrase.isBlank()) return ""
        
        val newSteps = mutableListOf<TransformationStep>()
        
        // Шаг 1: Исходная фраза
        newSteps.add(TransformationStep("Исходная фраза", phrase))
        
        // Шаг 2: Извлечение согласных
        val consonants = transliterateConsonants(phrase)
        newSteps.add(TransformationStep("Согласные (транслит.)", consonants.ifEmpty { "—" }, MaterialTheme.colorScheme.primary))
        
        if (consonants.isEmpty()) {
            return PasswordGenerator.generate(length, useUpper, useDigits, useSpecial).password
        }
        
        // Берём нужное количество символов
        var result = consonants.take(length)
        while (result.length < length) {
            result += "bcdfghjklmnpqrstvwxyz".random()
        }
        result = result.take(length)
        
        // Шаг 3: Базовая трансформация
        newSteps.add(TransformationStep("Базовая форма ($length симв.)", result))
        
        // Шаг 4: Применение метода шифрования
        result = when (cipherMethod) {
            CipherMethod.BASIC -> result
            CipherMethod.CAESAR -> {
                val shifted = caesarShift(result, caesarShift)
                newSteps.add(TransformationStep("Сдвиг на $caesarShift", shifted, MaterialTheme.colorScheme.secondary))
                shifted
            }
            CipherMethod.REVERSE -> {
                val reversed = reverseString(result)
                newSteps.add(TransformationStep("Реверс", reversed, MaterialTheme.colorScheme.secondary))
                reversed
            }
            CipherMethod.MIRROR -> {
                val mirrored = mirror(result).take(length)
                newSteps.add(TransformationStep("Зеркало", mirrored, MaterialTheme.colorScheme.secondary))
                mirrored
            }
            CipherMethod.CHESS -> {
                val chess = chessPattern(result)
                newSteps.add(TransformationStep("Шахматный", chess, MaterialTheme.colorScheme.secondary))
                chess
            }
        }
        
        // Шаг 5: Применение фильтров
        val filtered = applyFilters(result)
        if (useUpper || useDigits || useSpecial) {
            newSteps.add(TransformationStep("5️ Фильтры применены", filtered, MaterialTheme.colorScheme.tertiary))
        }
        
        newSteps.add(TransformationStep(" ИТОГ", filtered, MaterialTheme.colorScheme.primary))
        
        steps = newSteps
        return filtered
    }
    
    fun generateSimplePassword(): String {
        return PasswordGenerator.generate(length, useUpper, useDigits, useSpecial).password
    }
    
    // Перегенерация
    LaunchedEffect(selectedTab, length, useUpper, useDigits, useSpecial, mnemonicPhrase, cipherMethod, caesarShift) {
        generatedPwd = if (selectedTab == 0) {
            generateSimplePassword()
        } else {
            generateMnemonicWithSteps(mnemonicPhrase)
        }
    }
    
    // Расчёт сложности
    val complexityScore = remember(generatedPwd) {
        var score = 0
        if (generatedPwd.length >= 12) score += 20
        if (generatedPwd.length >= 16) score += 10
        if (generatedPwd.any { it.isUpperCase() }) score += 15
        if (generatedPwd.any { it.isLowerCase() }) score += 15
        if (generatedPwd.any { it.isDigit() }) score += 20
        if (generatedPwd.any { !it.isLetterOrDigit() }) score += 20
        score.coerceAtMost(100)
    }
    
    val complexityColor = when {
        complexityScore >= 80 -> Color(0xFF4CAF50) // Зелёный
        complexityScore >= 50 -> Color(0xFFFFC107) // Жёлтый
        else -> Color(0xFFF44336) // Красный
    }
    
    val complexityLabel = when {
        complexityScore >= 80 -> "Очень надёжный"
        complexityScore >= 50 -> "Средний"
        else -> "Слабый"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Продвинутый генератор", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Вкладки
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; showSteps = false },
                        text = { Text(" Обычный") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(" Мнемонический") }
                    )
                }
                
                // ===== ВКЛАДКА МНЕМОНИЧЕСКИЙ =====
                if (selectedTab == 1) {
                    OutlinedTextField(
                        value = mnemonicPhrase,
                        onValueChange = { mnemonicPhrase = it },
                        label = { Text(" Подсказка-фраза") },
                        placeholder = { Text("например: Мой кот любит молоко") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Выбор метода шифрования
                    Text(" Метод шифрования:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CipherMethod.entries.forEach { method ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { cipherMethod = method },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (cipherMethod == method) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = cipherMethod == method,
                                        onClick = { cipherMethod = method }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(method.icon, fontSize = 18.sp)
                                            Spacer(Modifier.width(6.dp))
                                            Text(method.label, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text(method.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    
                    // Параметр для Сдвига Цезаря
                    if (cipherMethod == CipherMethod.CAESAR) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("⚙️ Сдвиг: $caesarShift", fontWeight = FontWeight.Medium)
                                Slider(
                                    value = caesarShift.toFloat(),
                                    onValueChange = { caesarShift = it.toInt() },
                                    valueRange = 1f..5f,
                                    steps = 3
                                )
                                Text("Каждая буква сдвинется на $caesarShift позиций в алфавите", fontSize = 11.sp)
                            }
                        }
                    }
                    
                    // Кнопка визуализации
                    if (mnemonicPhrase.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showSteps = !showSteps },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (showSteps) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null,
                                    Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (showSteps) "Скрыть шаги" else "Показать шаги")
                            }
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedPwd))
                                    android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Копировать")
                            }
                        }
                    }
                    
                    // Пошаговая визуализация
                    AnimatedVisibility(
                        visible = showSteps && steps.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🔍 Пошаговая трансформация:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                steps.forEach { step ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(step.label, fontSize = 11.sp, color = step.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                step.value,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = step.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // ===== РЕЗУЛЬТАТ =====
                if (generatedPwd.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(" Ваш пароль:", fontWeight = FontWeight.Bold)
                                if (selectedTab == 1) {
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(generatedPwd))
                                        android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = generatedPwd,
                                fontSize = 22.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(12.dp))
                            
                            // Индикатор сложности
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Сложность: ", fontSize = 12.sp)
                                Text(
                                    complexityLabel,
                                    fontWeight = FontWeight.Bold,
                                    color = complexityColor,
                                    fontSize = 12.sp
                                )
                                Text(" ($complexityScore%)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { complexityScore / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = complexityColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
                
                // ===== ОБЩИЕ НАСТРОЙКИ =====
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚙️ Параметры", fontWeight = FontWeight.Bold)
                        
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
                        
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            Checkbox(checked = useUpper, onCheckedChange = { useUpper = it })
                            Text("Заглавные (~30%)", Modifier.padding(start = 8.dp)) 
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            Checkbox(checked = useDigits, onCheckedChange = { useDigits = it })
                            Text("Цифры (a→4, e→3...)", Modifier.padding(start = 8.dp)) 
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it })
                            Text("Спецсимволы (a→@, s→$...)", Modifier.padding(start = 8.dp)) 
                        }
                    }
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
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Использовать") 
            } 
        },
        dismissButton = { 
            TextButton(onDismiss) { Text("Отмена") } 
        },
        modifier = Modifier.fillMaxWidth(0.95f)
    )
}
