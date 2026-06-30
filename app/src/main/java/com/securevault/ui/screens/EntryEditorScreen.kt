@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.securevault.ui.screens

import androidx.compose.animation.*
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
import com.securevault.data.Entry
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel
import kotlin.random.Random

enum class CipherMethod(
    val label: String,
    val icon: String,
    val description: String,
    val scientificName: String,
    val complexity: Int
) {
    FMP("Фонемно-матричное", "M", "Матричная транспозиция", "Phonetic-Matrix Transformation", 75),
    VMS("Векторный многомерный", "V", "Инверсия чётных слов", "Vector Multidimensional Shift", 85),
    HID("Хэш-инъекция", "H", "Чередование регистра", "Hash Injection", 95),
    PPK("Полиалфавитная", "P", "Замена букв на цифры", "Polyalphabetic Substitution", 80),
    BPI("Блочное", "B", "Обратный порядок слов", "Block Permutation", 70),
    SOFT("Мягкий (читаемый)", "S", "Транслит + мягкие замены", "Soft Translit Plus", 60)
}

data class TransformationStep(
    val stepNumber: Int,
    val label: String,
    val value: String,
    val formula: String = "",
    val color: Color = Color.Unspecified
)

@Composable
fun EntryEditorScreen(
    id: String?,
    profileId: Int? = null,
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val isNewEntry = id == null || id == "new"
    
    val allEntries by viewModel.entries.collectAsState()
    val existingEntry = remember(id, allEntries) {
        if (isNewEntry) null else allEntries.find { e -> e.id == id }
    }
    
    val currentProfileId by viewModel.currentProfileId.collectAsState()
    val effectiveProfileId = profileId ?: currentProfileId
    
    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var textHint by remember { mutableStateOf("") }
    var rotationEnabled by remember { mutableStateOf(false) }
    var rotationMonths by remember { mutableIntStateOf(6) }
    var isFavorite by remember { mutableStateOf(false) }
    
    var showPassword by remember { mutableStateOf(false) }
    var showGeneratorDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(existingEntry) {
        existingEntry?.let { entry ->
            service = entry.service
            username = entry.username
            password = entry.password
            url = entry.url ?: ""
            notes = entry.notes ?: ""
            textHint = entry.textHint ?: ""
            rotationEnabled = entry.rotationEnabled
            rotationMonths = entry.rotationPeriodMonths
            isFavorite = entry.isFavorite
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isNewEntry) "Новая запись" else "Редактировать", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
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
                        if (effectiveProfileId == null) {
                            showError = "Профиль не выбран. Вернитесь назад и войдите в профиль."
                            return@IconButton
                        }
                        
                        val encryptedPwd = CryptoUtils.encrypt(password)
                        
                        val entry = if (existingEntry != null) {
                            existingEntry.copy(
                                service = service, username = username, encryptedPassword = encryptedPwd,
                                url = url.ifBlank { null }, notes = notes.ifBlank { null },
                                textHint = textHint.ifBlank { null },
                                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationMonths,
                                isFavorite = isFavorite, lastChanged = System.currentTimeMillis()
                            )
                        } else {
                            Entry.create(
                                service = service, username = username, password = password,
                                profileId = effectiveProfileId!!,
                                url = url.ifBlank { null }, notes = notes.ifBlank { null },
                                textHint = textHint.ifBlank { null },
                                rotationEnabled = rotationEnabled, rotationPeriodMonths = rotationMonths,
                                isFavorite = isFavorite
                            )
                        }
                        
                        viewModel.insert(entry)
                        showSuccess = true
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
            if (showSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Сохранено!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            
            if (showError != null) { 
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(showError!!, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                    }
                }
                showError = null 
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (effectiveProfileId != null) "Профиль ID: $effectiveProfileId" else "⚠️ Профиль не выбран",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            OutlinedTextField(
                value = service,
                onValueChange = { service = it },
                label = { Text("Сервис *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Логин / Email") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = password, 
                onValueChange = { password = it }, 
                label = { Text("Пароль *") }, 
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), 
                trailingIcon = { 
                    Row { 
                        IconButton(onClick = { showPassword = !showPassword }) { 
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) 
                        } 
                        IconButton(onClick = { showGeneratorDialog = true }) { 
                            Icon(Icons.Default.Casino, "Генератор") 
                        } 
                    } 
                }, 
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL (необязательно)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметки") },
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.SpaceBetween, 
                        verticalAlignment = Alignment.CenterVertically
                    ) { 
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Напоминание о смене пароля", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Switch(checked = rotationEnabled, onCheckedChange = { rotationEnabled = it }) 
                    }
                    if (rotationEnabled) {
                        Spacer(Modifier.height(12.dp))
                        var expandedMonths by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedMonths,
                            onExpandedChange = { expandedMonths = !expandedMonths }
                        ) {
                            OutlinedTextField(
                                readOnly = true, 
                                value = "$rotationMonths мес.", 
                                onValueChange = {}, 
                                label = { Text("Менять каждые") }, 
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonths) }, 
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedMonths,
                                onDismissRequest = { expandedMonths = false }
                            ) { 
                                listOf(3, 6, 12).forEach { m -> 
                                    DropdownMenuItem(
                                        text = { Text("$m мес.") }, 
                                        onClick = { 
                                            rotationMonths = m
                                            expandedMonths = false
                                        }
                                    ) 
                                } 
                            }
                        }
                    }
                }
            }
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Мнемоническая подсказка", fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textHint,
                        onValueChange = { textHint = it },
                        label = { Text("Текстовая подсказка") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
    
    if (showGeneratorDialog) {
        ScientificPasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false }, 
            onGenerated = { pwd -> 
                password = pwd
                showGeneratorDialog = false 
            }
        )
    }
}

// ===== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ =====

fun extractConsonants(text: String): String {
    val consonants = "бвгджзйклмнпрстфхцчшщbcdfghjklmnpqrstvwxyz"
    val result = StringBuilder()
    for (ch in text) if (ch.lowercaseChar() in consonants) result.append(ch.lowercaseChar())
    return result.toString()
}

fun transliterate(text: String): String {
    val map = mapOf(
        'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ж' to "zh",
        'з' to "z", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch"
    )
    val result = StringBuilder()
    for (ch in text) result.append(map[ch] ?: ch)
    return result.toString()
}

// ===== МЯГКИЕ АЛГОРИТМЫ =====

fun phoneticMatrixTransform(text: String): Pair<String, List<TransformationStep>> {
    val steps = mutableListOf<TransformationStep>()
    steps.add(TransformationStep(1, "Исходная фраза", text))
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    steps.add(TransformationStep(2, "Разбиение на слова", words.joinToString(" | "), "${words.size} слов"))
    if (words.isEmpty()) return "" to steps
    val transliteratedWords = words.map { word ->
        val consonants = extractConsonants(word)
        transliterate(consonants.ifEmpty { word.filter { it.isLetter() } })
    }
    steps.add(TransformationStep(3, "Транслитерация", transliteratedWords.joinToString(" "), "RU → EN"))
    val shortWords = transliteratedWords.map { it.take(4) }
    steps.add(TransformationStep(4, "Сокращение", shortWords.joinToString(" "), "max 4 буквы"))
    var result = shortWords.joinToString("_")
    result = result.split("_").joinToString("_") { word ->
        if (word.isNotEmpty()) word[0].uppercaseChar() + word.substring(1).lowercase() else word
    }
    steps.add(TransformationStep(5, "Заглавные первые буквы", result, "Capitalize"))
    return result to steps
}

fun vectorMultidimensionalShift(text: String): Pair<String, List<TransformationStep>> {
    val steps = mutableListOf<TransformationStep>()
    steps.add(TransformationStep(1, "Исходная фраза", text))
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    steps.add(TransformationStep(2, "Разбиение на слова", words.joinToString(" | "), "${words.size} слов"))
    if (words.isEmpty()) return "" to steps
    val transliteratedWords = words.map { word ->
        val consonants = extractConsonants(word)
        transliterate(consonants.ifEmpty { word.filter { it.isLetter() } })
    }
    steps.add(TransformationStep(3, "Транслитерация", transliteratedWords.joinToString(" "), "RU → EN"))
    val shortWords = transliteratedWords.map { it.take(4) }
    steps.add(TransformationStep(4, "Сокращение", shortWords.joinToString(" "), "max 4 буквы"))
    var result = shortWords.joinToString("_")
    val words_list = result.split("_")
    val inverted = words_list.mapIndexed { index, word ->
        if (index % 2 == 1) word.uppercase() else word.lowercase()
    }
    result = inverted.joinToString("_")
    steps.add(TransformationStep(5, "Инверсия чётных слов", result, "odd=lower, even=UPPER"))
    return result to steps
}

fun hashInjectionWithDiffusion(text: String): Pair<String, List<TransformationStep>> {
    val steps = mutableListOf<TransformationStep>()
    steps.add(TransformationStep(1, "Исходная фраза", text))
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    steps.add(TransformationStep(2, "Разбиение на слова", words.joinToString(" | "), "${words.size} слов"))
    if (words.isEmpty()) return "" to steps
    val transliteratedWords = words.map { word ->
        val consonants = extractConsonants(word)
        transliterate(consonants.ifEmpty { word.filter { it.isLetter() } })
    }
    steps.add(TransformationStep(3, "Транслитерация", transliteratedWords.joinToString(" "), "RU → EN"))
    val shortWords = transliteratedWords.map { it.take(4) }
    steps.add(TransformationStep(4, "Сокращение", shortWords.joinToString(" "), "max 4 буквы"))
    var result = shortWords.joinToString("_")
    val chars = result.toCharArray()
    for (i in chars.indices) {
        if (i % 3 == 0) chars[i] = chars[i].uppercaseChar()
    }
    result = String(chars)
    steps.add(TransformationStep(5, "Чередование регистра", result, "every 3rd char = UPPER"))
    return result to steps
}

fun polyalphabeticSubstitution(text: String): Pair<String, List<TransformationStep>> {
    val steps = mutableListOf<TransformationStep>()
    steps.add(TransformationStep(1, "Исходная фраза", text))
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    steps.add(TransformationStep(2, "Разбиение на слова", words.joinToString(" | "), "${words.size} слов"))
    if (words.isEmpty()) return "" to steps
    val transliteratedWords = words.map { word ->
        val consonants = extractConsonants(word)
        transliterate(consonants.ifEmpty { word.filter { it.isLetter() } })
    }
    steps.add(TransformationStep(3, "Транслитерация", transliteratedWords.joinToString(" "), "RU → EN"))
    val shortWords = transliteratedWords.map { it.take(4) }
    steps.add(TransformationStep(4, "Сокращение", shortWords.joinToString(" "), "max 4 буквы"))
    var result = shortWords.joinToString("_")
    val replacements = mapOf('o' to '0', 'e' to '3', 'a' to '4', 'i' to '1')
    val chars = result.toCharArray()
    var count = 0
    for (i in chars.indices) {
        if (chars[i].lowercaseChar() in replacements && count < 3) {
            chars[i] = replacements[chars[i].lowercaseChar()]!!
            count++
        }
    }
    result = String(chars)
    steps.add(TransformationStep(5, "Замена букв на цифры", result, "o→0, e→3, a→4, i→1"))
    return result to steps
}

fun blockPermutationWithInversion(text: String): Pair<String, List<TransformationStep>> {
    val steps = mutableListOf<TransformationStep>()
    steps.add(TransformationStep(1, "Исходная фраза", text))
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    steps.add(TransformationStep(2, "Разбиение на слова", words.joinToString(" | "), "${words.size} слов"))
    if (words.isEmpty()) return "" to steps
    val transliteratedWords = words.map { word ->
        val consonants = extractConsonants(word)
        transliterate(consonants.ifEmpty { word.filter { it.isLetter() } })
    }
    steps.add(TransformationStep(3, "Транслитерация", transliteratedWords.joinToString(" "), "RU → EN"))
    val shortWords = transliteratedWords.map { it.take(4) }
    steps.add(TransformationStep(4, "Сокращение", shortWords.joinToString(" "), "max 4 буквы"))
    val reversed = shortWords.reversed()
    var result = reversed.joinToString("_")
    result = result.split("_").joinToString("_") { word ->
        if (word.isNotEmpty()) word[0].uppercaseChar() + word.substring(1).lowercase() else word
    }
    steps.add(TransformationStep(5, "Обратный порядок слов", result, "reverse order"))
    return result to steps
}

fun softTranslitTransform(text: String): Pair<String, List<TransformationStep>> {
    val steps = mutableListOf<TransformationStep>()
    steps.add(TransformationStep(1, "Исходная фраза", text))
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    steps.add(TransformationStep(2, "Разбиение на слова", words.joinToString(" | "), "${words.size} слов"))
    if (words.isEmpty()) return "" to steps
    val transliteratedWords = words.map { word ->
        val consonants = extractConsonants(word)
        transliterate(consonants.ifEmpty { word.filter { it.isLetter() } })
    }
    steps.add(TransformationStep(3, "Транслитерация", transliteratedWords.joinToString(" "), "RU → EN"))
    val shortWords = transliteratedWords.map { it.take(4) }
    steps.add(TransformationStep(4, "Сокращение", shortWords.joinToString(" "), "max 4 буквы"))
    var result = shortWords.joinToString("_")
    result = result.split("_").joinToString("_") { word ->
        if (word.isNotEmpty()) word[0].uppercaseChar() + word.substring(1).lowercase() else word
    }
    steps.add(TransformationStep(5, "Заглавные первые буквы", result, "Capitalize"))
    val softReplacements = mapOf('o' to '0', 'e' to '3', 'a' to '4', 'i' to '1', 's' to '5', 't' to '7')
    val chars = result.toCharArray()
    var replacedCount = 0
    val maxReplacements = (chars.count { it.isLetter() } * 0.3).toInt()
    for (i in chars.indices) {
        if (chars[i].isLetter() && chars[i].lowercaseChar() in softReplacements && replacedCount < maxReplacements && Random.nextFloat() < 0.3f) {
            chars[i] = softReplacements[chars[i].lowercaseChar()]!!
            replacedCount++
        }
    }
    result = String(chars)
    steps.add(TransformationStep(6, "Мягкие замены (30%)", result, "o→0, e→3, a→4..."))
    val yearSuffix = (20..24).random().toString()
    val specialSuffix = listOf("!", "@", "#", "$").random()
    result = "$result$specialSuffix$yearSuffix"
    steps.add(TransformationStep(7, "Суффикс", result, "+ спецсимвол + год"))
    return result to steps
}

fun applyMemorableTransformations(baseResult: String, steps: MutableList<TransformationStep>): String {
    var result = baseResult
    val softReplacements = mapOf('o' to '0', 'e' to '3', 'a' to '4', 'i' to '1', 's' to '5', 't' to '7')
    val chars = result.toCharArray()
    var replacedCount = 0
    val maxReplacements = (chars.count { it.isLetter() } * 0.3).toInt()
    for (i in chars.indices) {
        if (chars[i].isLetter() && chars[i].lowercaseChar() in softReplacements && replacedCount < maxReplacements && Random.nextFloat() < 0.3f) {
            chars[i] = softReplacements[chars[i].lowercaseChar()]!!
            replacedCount++
        }
    }
    result = String(chars)
    steps.add(TransformationStep(steps.size + 1, "Мягкие замены (30%)", result, "o→0, e→3, a→4..."))
    val yearSuffix = (20..24).random().toString()
    val specialSuffix = listOf("!", "@", "#", "$").random()
    result = "$result$specialSuffix$yearSuffix"
    steps.add(TransformationStep(steps.size + 1, "Суффикс безопасности", result, "+ спецсимвол + год"))
    return result
}

fun removeDuplicates(text: String): String {
    val seen = mutableSetOf<Char>()
    val result = StringBuilder()
    for (ch in text) if (ch !in seen) { seen.add(ch); result.append(ch) }
    return result.toString()
}

fun generateTwoPartPassword(base: String): String {
    if (base.length < 4) return base
    val halfLength = (base.length / 2).coerceAtLeast(2)
    val part1 = base.take(halfLength)
    val part2 = if (base.length > halfLength) base.drop(halfLength) else base.takeLast(halfLength)
    val guaranteedPart1 = listOf('A', '1', '@', 'x')
    val guaranteedPart2 = listOf('B', '2', '#', 'y')
    val part1WithGuarantee = StringBuilder()
    val part2WithGuarantee = StringBuilder()
    for (ch in part1) if (part1WithGuarantee.length < 6) part1WithGuarantee.append(ch)
    for (ch in part2) if (part2WithGuarantee.length < 6) part2WithGuarantee.append(ch)
    for (ch in guaranteedPart1) if (!part1WithGuarantee.contains(ch.toString())) part1WithGuarantee.append(ch)
    for (ch in guaranteedPart2) if (!part2WithGuarantee.contains(ch.toString())) part2WithGuarantee.append(ch)
    val shuffled1 = part1WithGuarantee.toString().toCharArray().apply { 
        for (i in indices) { val j = Random.nextInt(size); val temp = this[i]; this[i] = this[j]; this[j] = temp }
    }.concatToString()
    val shuffled2 = part2WithGuarantee.toString().toCharArray().apply { 
        for (i in indices) { val j = Random.nextInt(size); val temp = this[i]; this[i] = this[j]; this[j] = temp }
    }.concatToString()
    return "$shuffled1-$shuffled2"
}

// ===== НАУЧНЫЙ ГЕНЕРАТОР =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScientificPasswordGeneratorDialog(
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
    var useTwoParts by remember { mutableStateOf(false) }
    
    var generatedPwd by remember { mutableStateOf("") }
    var mnemonicPhrase by remember { mutableStateOf("") }
    var cipherMethod by remember { mutableStateOf(CipherMethod.SOFT) }
    var showSteps by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf<List<TransformationStep>>(emptyList()) }
    var entropyScore by remember { mutableStateOf(0.0) }
    var passwordHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var regenerationCounter by remember { mutableIntStateOf(0) }
    
    fun applyFilters(text: String): Pair<String, List<TransformationStep>> {
        val steps = mutableListOf<TransformationStep>()
        val chars = text.toCharArray()
        if (useUpper) {
            for (i in chars.indices) if (chars[i].isLetter() && Random.nextFloat() < 0.3f) chars[i] = chars[i].uppercaseChar()
            steps.add(TransformationStep(steps.size + 1, "Заглавные (~30%)", String(chars), "P(upper) = 0.3"))
        }
        if (useDigits) {
            val digitReplacements = mapOf('a' to '4', 'e' to '3', 'i' to '1', 'o' to '0', 's' to '5', 't' to '7', 'b' to '8')
            for (i in chars.indices) {
                val lower = chars[i].lowercaseChar()
                if (lower in digitReplacements && Random.nextFloat() < 0.25f) chars[i] = digitReplacements[lower]!!
            }
            steps.add(TransformationStep(steps.size + 1, "Цифровая замена (~25%)", String(chars), "P(digit) = 0.25"))
        }
        if (useSpecial) {
            val specialReplacements = mapOf('a' to '@', 's' to '$', 'o' to '0', 'i' to '!', 'e' to '3')
            for (i in chars.indices) {
                val lower = chars[i].lowercaseChar()
                if (lower in specialReplacements && Random.nextFloat() < 0.2f) chars[i] = specialReplacements[lower]!!
            }
            steps.add(TransformationStep(steps.size + 1, "Спецсимволы (~20%)", String(chars), "P(special) = 0.2"))
        }
        return String(chars) to steps
    }
    
    fun generateScientificPassword(phrase: String): String {
        if (phrase.isBlank()) return ""
        
        val (baseResult, baseSteps) = when (cipherMethod) {
            CipherMethod.SOFT -> softTranslitTransform(phrase)
            CipherMethod.FMP -> phoneticMatrixTransform(phrase)
            CipherMethod.VMS -> vectorMultidimensionalShift(phrase)
            CipherMethod.HID -> hashInjectionWithDiffusion(phrase)
            CipherMethod.PPK -> polyalphabeticSubstitution(phrase)
            CipherMethod.BPI -> blockPermutationWithInversion(phrase)
        }
        
        if (baseResult.isEmpty()) return PasswordGenerator.generate(length, useUpper, useDigits, useSpecial).password
        
        val allSteps = baseSteps.toMutableList()
        var result = applyMemorableTransformations(baseResult, allSteps)
        
        result = removeDuplicates(result)
        
        if (useTwoParts) result = generateTwoPartPassword(result)
        
        val (filteredPwd, filterSteps) = applyFilters(result)
        
        allSteps.addAll(filterSteps.map { it.copy(stepNumber = it.stepNumber + allSteps.size) })
        
        if (useTwoParts) {
            allSteps.add(TransformationStep(allSteps.size + 1, "Две части с гарантированным набором", filteredPwd, "Part1 + Part2"))
        }
        
        steps = allSteps
        
        if (passwordHistory.isEmpty() || passwordHistory.first() != filteredPwd) {
            passwordHistory = listOf(filteredPwd) + passwordHistory.take(9)
        }
        
        val charSetSize = when {
            filteredPwd.any { it.isUpperCase() } && filteredPwd.any { it.isLowerCase() } && 
            filteredPwd.any { it.isDigit() } && filteredPwd.any { !it.isLetterOrDigit() } -> 94.0
            filteredPwd.any { it.isUpperCase() } && filteredPwd.any { it.isLowerCase() } && 
            filteredPwd.any { it.isDigit() } -> 62.0
            filteredPwd.any { it.isUpperCase() } || filteredPwd.any { it.isLowerCase() } -> 26.0
            else -> 10.0
        }
        entropyScore = filteredPwd.length * kotlin.math.log2(charSetSize)
        
        return filteredPwd
    }
    
    LaunchedEffect(selectedTab, length, useUpper, useDigits, useSpecial, mnemonicPhrase, cipherMethod, useTwoParts, regenerationCounter) {
        generatedPwd = if (selectedTab == 0) {
            PasswordGenerator.generate(length, useUpper, useDigits, useSpecial).password
        } else {
            generateScientificPassword(mnemonicPhrase)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Научный генератор", fontWeight = FontWeight.Bold)
                    Text("Запоминаемые пароли", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; showSteps = false }, text = { Text("Стандартный") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Научный") })
                }
                
                if (selectedTab == 1) {
                    OutlinedTextField(
                        value = mnemonicPhrase,
                        onValueChange = { mnemonicPhrase = it },
                        label = { Text("Мнемоническая фраза") },
                        placeholder = { Text("например: Мой кот любит молоко") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("Авторский метод шифрования:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CipherMethod.entries.forEach { method ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { cipherMethod = method },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (cipherMethod == method) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = cipherMethod == method, onClick = { cipherMethod = method })
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(method.icon, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(method.label, fontWeight = FontWeight.SemiBold)
                                        Text(method.scientificName, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(method.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Link, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Две части пароля", fontWeight = FontWeight.Bold)
                                }
                                Text("Разделение на 2 части", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Switch(checked = useTwoParts, onCheckedChange = { useTwoParts = it })
                        }
                    }
                    
                    if (mnemonicPhrase.isNotBlank() || selectedTab == 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { regenerationCounter++; android.widget.Toast.makeText(context, "Новый вариант!", android.widget.Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Ещё раз", fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedPwd))
                                    android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Копировать", fontWeight = FontWeight.Medium)
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showSteps = !showSteps },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(
                                    if (showSteps) Icons.Default.VisibilityOff else Icons.Default.Visibility, 
                                    null, 
                                    Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (showSteps) "Скрыть шаги" else "Показать шаги", fontWeight = FontWeight.Medium)
                            }
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Close, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Закрыть", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    
                    if (passwordHistory.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Пароли на выбор (${passwordHistory.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                passwordHistory.take(5).forEachIndexed { idx, pwd ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            generatedPwd = pwd
                                            clipboardManager.setText(AnnotatedString(pwd))
                                            android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                        }.padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${idx + 1}. $pwd", fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                    
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
                                Text("Пошаговая трансформация:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                steps.forEach { step ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "Шаг ${step.stepNumber}: ${step.label}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = step.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary
                                                )
                                                if (step.formula.isNotEmpty()) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(step.formula, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.tertiary)
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(step.value, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
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
                                Text("Сгенерированный пароль:", fontWeight = FontWeight.Bold)
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedPwd))
                                    android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, null, Modifier.size(20.dp))
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Энтропия Шеннона: ", fontSize = 12.sp)
                                Text(
                                    String.format("%.1f бит", entropyScore),
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        entropyScore >= 80 -> Color(0xFF4CAF50)
                                        entropyScore >= 50 -> Color(0xFFFFC107)
                                        else -> Color(0xFFF44336)
                                    },
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = (entropyScore / 128.0).coerceIn(0.0, 1.0).toFloat(),
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = when {
                                    entropyScore >= 80 -> Color(0xFF4CAF50)
                                    entropyScore >= 50 -> Color(0xFFFFC107)
                                    else -> Color(0xFFF44336)
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Параметры генерации", fontWeight = FontWeight.Bold)
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
                            Text("Заглавные буквы (~30%)", Modifier.padding(start = 8.dp)) 
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            Checkbox(checked = useDigits, onCheckedChange = { useDigits = it })
                            Text("Цифровая замена (~25%)", Modifier.padding(start = 8.dp)) 
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it })
                            Text("Спецсимволы (~20%)", Modifier.padding(start = 8.dp)) 
                        }
                    }
                }
            }
        },
        confirmButton = { 
            Button(onClick = { 
                val finalPwd = if (generatedPwd.isNotEmpty()) generatedPwd else PasswordGenerator.generate(length, useUpper, useDigits, useSpecial).password
                onGenerated(finalPwd) 
            }) { 
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Использовать") 
            } 
        },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } },
        modifier = Modifier.fillMaxWidth(0.95f)
    )
}
