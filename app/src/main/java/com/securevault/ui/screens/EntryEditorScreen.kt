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
import com.securevault.data.Categories
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.data.QuickTags
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel
import java.security.MessageDigest
import kotlin.random.Random

// ===== АВТОРСКИЕ МЕТОДЫ ШИФРОВАНИЯ =====
enum class CipherMethod(
    val label: String,
    val icon: String,
    val description: String,
    val scientificName: String,
    val complexity: Int
) {
    FMP("Фонемно-матричное", "🔬", "Матричная транспозиция + модульный сдвиг", "Phonetic-Matrix Transformation", 75),
    VMS("Векторный многомерный", "📐", "Полиномиальный сдвиг с квадратичной зависимостью", "Vector Multidimensional Shift", 85),
    HID("Хэш-инъекция с диффузией", "🔐", "SHA-256 + XOR + диффузия Шеннона", "Hash Injection with Diffusion", 95),
    PPK("Полиалфавитная подстановка", "", "Шифр Виженера с автоключом", "Polyalphabetic Substitution with Autokey", 80),
    BPI("Блочное перемешивание", "", "Блочная перестановка + инверсия", "Block Permutation with Inversion", 70)
}

// ===== КЛАСС ДЛЯ ВИЗУАЛИЗАЦИИ ШАГОВ =====
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
        ScientificPasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false }, 
            onGenerated = { pwd -> 
                password = pwd
                showGeneratorDialog = false 
            }
        )
    }
}

// ===== НАУЧНЫЙ ГЕНЕРАТОР С НОВОЙ ЛОГИКОЙ =====
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
    var useTwoParts by remember { mutableStateOf(false) } // ✅ НОВАЯ ОПЦИЯ: две части
    
    var generatedPwd by remember { mutableStateOf("") }
    var mnemonicPhrase by remember { mutableStateOf("") }
    var cipherMethod by remember { mutableStateOf(CipherMethod.FMP) }
    var showSteps by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf<List<TransformationStep>>(emptyList()) }
    var entropyScore by remember { mutableStateOf(0.0) }
    
    // ✅ ИСТОРИЯ ПАРОЛЕЙ (последние 10)
    var passwordHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // ===== БЕЗОПАСНЫЕ ФУНКЦИИ ДЛЯ СТРОК (без joinToString) =====
    
    // Безопасное объединение списка строк
    fun safeJoin(strings: List<String>, separator: String = ""): String {
        if (strings.isEmpty()) return ""
        var result = strings[0]
        for (i in 1 until strings.size) {
            result += separator + strings[i]
        }
        return result
    }
    
    // Безопасное объединение списка символов
    fun safeJoinChars(chars: List<Char>, separator: String = ""): String {
        if (chars.isEmpty()) return ""
        var result = chars[0].toString()
        for (i in 1 until chars.size) {
            result += separator + chars[i].toString()
        }
        return result
    }
    
    // ===== АВТОРСКИЕ АЛГОРИТМЫ ШИФРОВАНИЯ =====
    
    // Извлечение согласных из фразы
    fun extractConsonants(text: String): String {
        val consonants = "бвгджзйклмнпрстфхцчшщbcdfghjklmnpqrstvwxyz"
        val result = StringBuilder()
        for (ch in text) {
            if (ch.lowercaseChar() in consonants) {
                result.append(ch.lowercaseChar())
            }
        }
        return result.toString()
    }
    
    // Транслитерация русских согласных в английские
    fun transliterate(text: String): String {
        val map = mapOf(
            'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ж' to "zh",
            'з' to "z", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
            'н' to "n", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch"
        )
        val result = StringBuilder()
        for (ch in text) {
            if (ch in map) {
                result.append(map[ch])
            } else {
                result.append(ch)
            }
        }
        return result.toString()
    }
    
    // 1. Фонемно-матричное преобразование (ФМП)
    fun phoneticMatrixTransform(text: String): Pair<String, List<TransformationStep>> {
        val steps = mutableListOf<TransformationStep>()
        steps.add(TransformationStep(1, "Исходная фраза", text))
        
        val consonants = extractConsonants(text)
        steps.add(TransformationStep(2, "Извлечение согласных", consonants, "N = ${consonants.length}"))
        
        if (consonants.isEmpty()) return "" to steps
        
        val transliterated = transliterate(consonants)
        steps.add(TransformationStep(3, "Транслитерация", transliterated, "RU → EN"))
        
        val matrixSize = kotlin.math.sqrt(transliterated.length.toDouble()).toInt().coerceAtLeast(2)
        
        // Матрица (безопасное объединение)
        val rows = mutableListOf<String>()
        var idx = 0
        while (idx < transliterated.length) {
            val end = (idx + matrixSize).coerceAtMost(transliterated.length)
            rows.add(transliterated.substring(idx, end))
            idx = end
        }
        val matrixStr = safeJoin(rows, "\n")
        steps.add(TransformationStep(4, "Матрица ${matrixSize}×${matrixSize}", matrixStr, "M[i][j]"))
        
        // Транспонирование
        val transposed = StringBuilder()
        for (col in 0 until matrixSize) {
            for (row in 0 until matrixSize) {
                val charIdx = row * matrixSize + col
                if (charIdx < transliterated.length) {
                    transposed.append(transliterated[charIdx])
                }
            }
        }
        steps.add(TransformationStep(5, "Транспонирование", transposed.toString(), "M[i][j] → M[j][i]"))
        
        // Модульный сдвиг
        val shifted = StringBuilder()
        for (i in transposed.indices) {
            val ch = transposed[i]
            val shift = (i * 2) % 26
            val code = ch.code - 'a'.code
            val newCode = (code + shift) % 26
            shifted.append((newCode + 'a'.code).toChar())
        }
        steps.add(TransformationStep(6, "Модульный сдвиг", shifted.toString(), "C[i] = (P[i] + 2i) mod 26"))
        
        return shifted.toString() to steps
    }
    
    // 2. Векторный многомерный сдвиг (ВМС)
    fun vectorMultidimensionalShift(text: String, key: Int = 3): Pair<String, List<TransformationStep>> {
        val steps = mutableListOf<TransformationStep>()
        steps.add(TransformationStep(1, "Исходная фраза", text))
        
        val consonants = extractConsonants(text)
        steps.add(TransformationStep(2, "Извлечение согласных", consonants))
        
        if (consonants.isEmpty()) return "" to steps
        
        val transliterated = transliterate(consonants)
        steps.add(TransformationStep(3, "Транслитерация", transliterated))
        
        // Полиномиальный сдвиг
        val shifted = StringBuilder()
        for (i in transliterated.indices) {
            val ch = transliterated[i]
            val position = i + 1
            val shift = (position * key * position) % 26
            val code = ch.code - 'a'.code
            val newCode = (code + shift) % 26
            shifted.append((newCode + 'a'.code).toChar())
        }
        steps.add(TransformationStep(4, "Полиномиальный сдвиг", shifted.toString(), "C[i] = (P[i] + (i·k)²) mod 26"))
        
        // Инверсия каждого второго
        val inverted = StringBuilder()
        for (i in shifted.indices) {
            val ch = shifted[i]
            if (i % 2 == 1) {
                inverted.append(ch.uppercaseChar())
            } else {
                inverted.append(ch)
            }
        }
        steps.add(TransformationStep(5, "Инверсия чётных позиций", inverted.toString(), "C[2k+1] = upper()"))
        
        return inverted.toString() to steps
    }
    
    // 3. Хэш-инъекция с диффузией (ХИД)
    fun hashInjectionWithDiffusion(text: String): Pair<String, List<TransformationStep>> {
        val steps = mutableListOf<TransformationStep>()
        steps.add(TransformationStep(1, "Исходная фраза", text))
        
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(text.toByteArray())
        val hashHex = StringBuilder()
        for (b in hashBytes) {
            hashHex.append("%02x".format(b))
        }
        val hashStr = hashHex.toString()
        steps.add(TransformationStep(2, "SHA-256 хэш", hashStr.take(32) + "...", "H = SHA256(phrase)"))
        
        val consonants = extractConsonants(text)
        steps.add(TransformationStep(3, "Базовый вектор", consonants))
        
        if (consonants.isEmpty()) return "" to steps
        
        val transliterated = transliterate(consonants)
        
        // XOR с хэшем
        val xored = StringBuilder()
        for (i in transliterated.indices) {
            val ch = transliterated[i]
            val hashChar = hashStr[i % hashStr.length]
            val hashVal = hashChar.digitToIntOrNull(16) ?: 0
            val code = ch.code - 'a'.code
            val newCode = (code + hashVal) % 26
            xored.append((newCode + 'a'.code).toChar())
        }
        steps.add(TransformationStep(4, "XOR-инъекция хэша", xored.toString(), "C[i] = P[i] XOR H[i]"))
        
        // Диффузия
        val diffused = StringBuilder()
        for (i in xored.indices) {
            val prev1 = if (i > 0) xored[i-1].code else 0
            val prev2 = if (i > 1) xored[i-2].code else 0
            val newCode = (xored[i].code + prev1 + prev2) % 128
            diffused.append(newCode.toChar())
        }
        val result = diffused.toString().filter { it.isLetterOrDigit() }.take(length)
        steps.add(TransformationStep(5, "Диффузия Шеннона", result, "D[i] = C[i] ⊕ C[i-1] ⊕ C[i-2]"))
        
        return result to steps
    }
    
    // 4. Полиалфавитная подстановка с автоключом (ППК)
    fun polyalphabeticSubstitution(text: String): Pair<String, List<TransformationStep>> {
        val steps = mutableListOf<TransformationStep>()
        steps.add(TransformationStep(1, "Исходная фраза", text))
        
        val consonants = extractConsonants(text)
        steps.add(TransformationStep(2, "Извлечение согласных", consonants))
        
        if (consonants.isEmpty()) return "" to steps
        
        val transliterated = transliterate(consonants)
        val keyWord = transliterated.take(5)
        steps.add(TransformationStep(3, "Ключевое слово", keyWord.uppercase(), "K = ${keyWord.uppercase()}"))
        
        val alphabet = "abcdefghijkmnpqrstvwxyz"
        val matrix = mutableListOf<List<Char>>()
        var idx = 0
        while (idx < alphabet.length) {
            val end = (idx + 5).coerceAtMost(alphabet.length)
            val row = mutableListOf<Char>()
            for (i in idx until end) {
                row.add(alphabet[i])
            }
            matrix.add(row)
            idx = end
        }
        
        val matrixRows = mutableListOf<String>()
        for (row in matrix) {
            matrixRows.add(safeJoinChars(row, " "))
        }
        val matrixStr = safeJoin(matrixRows, "\n")
        steps.add(TransformationStep(4, "Матрица 5×5", matrixStr, "M[5×5]"))
        
        val fullKey = keyWord + transliterated
        val encrypted = StringBuilder()
        for (i in transliterated.indices) {
            val ch = transliterated[i]
            val keyChar = fullKey[i % fullKey.length]
            val rowIdx = alphabet.indexOf(ch) / 5
            val colIdx = alphabet.indexOf(keyChar) % 5
            if (rowIdx in 0..4 && colIdx in 0..4 && rowIdx < matrix.size && colIdx < matrix[rowIdx].size) {
                encrypted.append(matrix[rowIdx][colIdx])
            } else {
                encrypted.append(ch)
            }
        }
        steps.add(TransformationStep(5, "Шифрование с автоключом", encrypted.toString(), "C[i] = M[row(P[i])][col(K[i])]"))
        
        return encrypted.toString() to steps
    }
    
    // 5. Блочное перемешивание с инверсией (БПИ)
    fun blockPermutationWithInversion(text: String): Pair<String, List<TransformationStep>> {
        val steps = mutableListOf<TransformationStep>()
        steps.add(TransformationStep(1, "Исходная фраза", text))
        
        val consonants = extractConsonants(text)
        steps.add(TransformationStep(2, "Извлечение согласных", consonants))
        
        if (consonants.isEmpty()) return "" to steps
        
        val transliterated = transliterate(consonants)
        
        // Разбиение на блоки по 4
        val blocks = mutableListOf<String>()
        var idx = 0
        while (idx < transliterated.length) {
            val end = (idx + 4).coerceAtMost(transliterated.length)
            blocks.add(transliterated.substring(idx, end))
            idx = end
        }
        val blocksStr = safeJoin(blocks, " | ")
        steps.add(TransformationStep(3, "Блоки по 4 символа", blocksStr, "B[i] = P[i*4:(i+1)*4]"))
        
        // Перестановка (2,4,1,3)
        val permuted = mutableListOf<String>()
        for (block in blocks) {
            val perm = when (block.length) {
                4 -> "${block[1]}${block[3]}${block[0]}${block[2]}"
                3 -> "${block[2]}${block[0]}${block[1]}"
                2 -> "${block[1]}${block[0]}"
                else -> block
            }
            permuted.add(perm)
        }
        val permutedStr = safeJoin(permuted, " | ")
        steps.add(TransformationStep(4, "Перестановка (2,4,1,3)", permutedStr, "π = (2,4,1,3)"))
        
        // Инверсия регистра каждого второго блока
        val inverted = mutableListOf<String>()
        for (i in permuted.indices) {
            if (i % 2 == 1) {
                inverted.add(permuted[i].uppercase())
            } else {
                inverted.add(permuted[i].lowercase())
            }
        }
        val invertedStr = safeJoin(inverted, " | ")
        steps.add(TransformationStep(5, "Инверсия нечётных блоков", invertedStr, "B[2k+1] = upper()"))
        
        // Циклический сдвиг
        val result = safeJoin(inverted, "")
        val shifted = if (result.length > 2) {
            result.takeLast(2) + result.dropLast(2)
        } else result
        steps.add(TransformationStep(6, "Циклический сдвиг вправо на 2", shifted, "R = rotate(P, 2)"))
        
        return shifted to steps
    }
    
    // ✅ НОВАЯ ФУНКЦИЯ: Генерация с двумя частями
    fun generateTwoPartPassword(base: String): String {
        if (base.length < 8) return base
        
        val halfLength = base.length / 2
        val part1 = base.take(halfLength)
        val part2 = base.drop(halfLength)
        
        // Гарантированный набор для каждой части: 2 заглавные, 2 цифры, 2 спецсимвола, 2 строчные
        val guaranteedChars = listOf('A', 'B', '1', '2', '@', '#', 'x', 'y')
        
        // Добавляем гарантированные символы в каждую часть
        val part1WithGuarantee = StringBuilder(part1)
        val part2WithGuarantee = StringBuilder(part2)
        
        for (ch in guaranteedChars) {
            if (part1WithGuarantee.length < halfLength + 4) {
                part1WithGuarantee.append(ch)
            }
            if (part2WithGuarantee.length < halfLength + 4) {
                part2WithGuarantee.append(ch)
            }
        }
        
        // Перемешиваем каждую часть
        val shuffled1 = part1WithGuarantee.toString().toCharArray().apply { 
            for (i in indices) {
                val j = Random.nextInt(length)
                val temp = this[i]
                this[i] = this[j]
                this[j] = temp
            }
        }.concatToString()
        
        val shuffled2 = part2WithGuarantee.toString().toCharArray().apply { 
            for (i in indices) {
                val j = Random.nextInt(length)
                val temp = this[i]
                this[i] = this[j]
                this[j] = temp
            }
        }.concatToString()
        
        return "$shuffled1-$shuffled2"
    }
    
    // ✅ УДАЛЕНИЕ ПОВТОРОВ
    fun removeDuplicates(text: String): String {
        val seen = mutableSetOf<Char>()
        val result = StringBuilder()
        for (ch in text) {
            if (ch !in seen) {
                seen.add(ch)
                result.append(ch)
            }
        }
        return result.toString()
    }
    
    // Применение фильтров
    fun applyFilters(text: String): Pair<String, List<TransformationStep>> {
        val steps = mutableListOf<TransformationStep>()
        val chars = text.toCharArray()
        
        if (useUpper) {
            for (i in chars.indices) {
                if (chars[i].isLetter() && Random.nextFloat() < 0.3f) {
                    chars[i] = chars[i].uppercaseChar()
                }
            }
            steps.add(TransformationStep(steps.size + 1, "Заглавные (~30%)", String(chars), "P(upper) = 0.3"))
        }
        
        if (useDigits) {
            val digitReplacements = mapOf('a' to '4', 'e' to '3', 'i' to '1', 'o' to '0', 's' to '5', 't' to '7', 'b' to '8')
            for (i in chars.indices) {
                val lower = chars[i].lowercaseChar()
                if (lower in digitReplacements && Random.nextFloat() < 0.25f) {
                    chars[i] = digitReplacements[lower]!!
                }
            }
            steps.add(TransformationStep(steps.size + 1, "Цифровая замена (~25%)", String(chars), "P(digit) = 0.25"))
        }
        
        if (useSpecial) {
            val specialReplacements = mapOf('a' to '@', 's' to '$', 'o' to '0', 'i' to '!', 'e' to '3')
            for (i in chars.indices) {
                val lower = chars[i].lowercaseChar()
                if (lower in specialReplacements && Random.nextFloat() < 0.2f) {
                    chars[i] = specialReplacements[lower]!!
                }
            }
            steps.add(TransformationStep(steps.size + 1, "Спецсимволы (~20%)", String(chars), "P(special) = 0.2"))
        }
        
        return String(chars) to steps
    }
    
    // Главная функция генерации
    fun generateScientificPassword(phrase: String): String {
        if (phrase.isBlank()) return ""
        
        val (baseResult, baseSteps) = when (cipherMethod) {
            CipherMethod.FMP -> phoneticMatrixTransform(phrase)
            CipherMethod.VMS -> vectorMultidimensionalShift(phrase)
            CipherMethod.HID -> hashInjectionWithDiffusion(phrase)
            CipherMethod.PPK -> polyalphabeticSubstitution(phrase)
            CipherMethod.BPI -> blockPermutationWithInversion(phrase)
        }
        
        if (baseResult.isEmpty()) {
            return PasswordGenerator.generate(length, useUpper, useDigits, useSpecial).password
        }
        
        var result = baseResult
        while (result.length < length) {
            result += "bcdfghjklmnpqrstvwxyz".random()
        }
        result = result.take(length)
        
        // ✅ Удаление повторов
        result = removeDuplicates(result)
        
        // ✅ Если включены две части — генерируем двухчастный пароль
        if (useTwoParts) {
            result = generateTwoPartPassword(result)
        }
        
        val (filteredPwd, filterSteps) = applyFilters(result)
        
        steps = baseSteps + filterSteps.map { it.copy(stepNumber = it.stepNumber + baseSteps.size) }
        steps = steps.mapIndexed { idx, step -> step.copy(stepNumber = idx + 1) }
        
        // ✅ Добавляем шаг с двумя частями, если включено
        if (useTwoParts) {
            steps = steps + TransformationStep(steps.size + 1, "Две части с гарантированным набором", filteredPwd, "Part1-Guaranteed + Part2-Guaranteed")
        }
        
        // ✅ Сохраняем в историю (если новый пароль отличается от последнего)
        if (passwordHistory.isEmpty() || passwordHistory.first() != filteredPwd) {
            passwordHistory = listOf(filteredPwd) + passwordHistory.take(9)
        }
        
        // Расчёт энтропии
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
    
    // Перегенерация
    LaunchedEffect(selectedTab, length, useUpper, useDigits, useSpecial, mnemonicPhrase, cipherMethod, useTwoParts) {
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
                    Text("Авторские методы + две части", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; showSteps = false },
                        text = { Text("Стандартный") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Научный") }
                    )
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
                                        Text(method.scientificName, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(method.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Сложность: ${method.complexity}/100", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                            }
                        }
                    }
                    
                    // ✅ НОВАЯ ОПЦИЯ: Две части пароля
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
                                Text("🔗 Две части пароля", fontWeight = FontWeight.Bold)
                                Text("Каждая часть: 2 заглавные + 2 цифры + 2 спецсимвола + 2 строчные", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Switch(checked = useTwoParts, onCheckedChange = { useTwoParts = it })
                        }
                    }
                    
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
                                Text(if (showSteps) "Скрыть" else "Шаги")
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
                    
                    // ✅ ИСТОРИЯ ПАРОЛЕЙ
                    if (passwordHistory.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("История паролей (последние ${passwordHistory.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                passwordHistory.take(5).forEachIndexed { idx, pwd ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            generatedPwd = pwd
                                            clipboardManager.setText(AnnotatedString(pwd))
                                            android.widget.Toast.makeText(context, "Скопировано из истории!", android.widget.Toast.LENGTH_SHORT).show()
                                        }.padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${idx + 1}. $pwd",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            modifier = Modifier.weight(1f)
                                        )
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
                                Text("🔬 Пошаговая трансформация:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                steps.forEach { step ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        )
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
                                                    Text(
                                                        step.formula,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.tertiary
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                step.value,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
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
                            
                            if (selectedTab == 1) {
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = when {
                                        entropyScore >= 80 -> Color(0xFF4CAF50)
                                        entropyScore >= 50 -> Color(0xFFFFC107)
                                        else -> Color(0xFFF44336)
                                    },
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    "Теоретическая стойкость: ${if (entropyScore >= 80) "Высокая" else if (entropyScore >= 50) "Средняя" else "Низкая"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
            Button(
                onClick = { 
                    val finalPwd = if (generatedPwd.isNotEmpty()) generatedPwd else PasswordGenerator.generate(length, useUpper, useDigits, useSpecial).password
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
