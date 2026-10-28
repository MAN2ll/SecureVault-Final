@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.securevault.ui.components

import com.securevault.security.SensitiveContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.securevault.data.Profile
import com.securevault.utils.MnemonicHint
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator
import org.json.JSONObject
import java.util.Calendar

@Composable
fun UnifiedPasswordGeneratorDialog(onDismiss: () -> Unit,
    onGenerated: (String, String, String?, String?, String?) -> Unit,
    initialServiceName: String = "", profile: Profile? = null, initialMode: Int = 0,
    ampgOnly: Boolean = false, initialYear: Int? = null) {
    var mode by remember { mutableIntStateOf(initialMode.coerceIn(0, 3)) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Генератор паролей") }, text = {
        SensitiveContent()
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (if (ampgOnly) emptyList() else listOf(listOf(0 to "Случайный", 1 to "2 части"), listOf(2 to "Якорь", 3 to "AMPG"))).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (value, label) -> FilterChip(mode == value, { mode = value }, label = { Text(label) }) }
                }
            }
            when (if (ampgOnly) 3 else mode) {
                0, 1 -> RandomContent(mode == 1) { pwd ->
                    onGenerated(pwd, if (mode == 1) "random_two_part" else "random", null, null, null)
                }
                2 -> AnchorContent(initialServiceName) { pwd, hint -> onGenerated(pwd, "random_anchor", hint, null, null) }
                3 -> AmpgGeneratorContent(initialServiceName, profile?.id, initialYear, onGenerated)
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } })
}
@Composable
private fun RandomContent(twoParts: Boolean, onGenerated: (String) -> Unit) {
    var length by remember(twoParts) { mutableIntStateOf(16) }
    var lower by remember { mutableStateOf(true) }
    var upper by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var special by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(twoParts, length, lower, upper, digits, special, refresh) {
        val result = if (twoParts) PasswordGenerator.generateTwoPart(length, true, true, true, true)
            else PasswordGenerator.generate(length, lower, upper, digits, special)
        password = result.getOrNull()?.password.orEmpty(); error = result.exceptionOrNull()?.message
    }
    if (twoParts) {
        Text("В каждой части минимум по две строчные, заглавные, цифры и спецсимвола.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(16, 18, 20).forEach { n -> FilterChip(length == n, { length = n }, label = { Text("$n") }) }
        }
    } else {
        Text("Длина: $length")
        Slider(length.toFloat(), { length = it.toInt() }, valueRange = 8f..32f, steps = 23)
        Toggle("Строчные a–z", lower) { lower = it }
        Toggle("Заглавные A–Z", upper) { upper = it }
        Toggle("Цифры", digits) { digits = it }
        Toggle("Спецсимволы", special) { special = it }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (password.isNotEmpty()) Text(if (twoParts) password.chunked(password.length / 2).joinToString(" / ")
        else password, fontFamily = FontFamily.Monospace)
    OutlinedButton(onClick = { refresh++ }) { Text("Сгенерировать ещё") }
    Button(onClick = { onGenerated(password) }, enabled = password.isNotEmpty()) { Text("Выбрать") }
}
@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChange); Text(label) }
}
@Composable
private fun AnchorContent(initialService: String, onGenerated: (String, String) -> Unit) {
    var anchor by remember { mutableStateOf("") }
    var service by remember { mutableStateOf(initialService) }
    var length by remember { mutableIntStateOf(16) }
    var addService by remember { mutableStateOf(false) }
    var addYear by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var password by remember { mutableStateOf("") }
    var explanation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(anchor, service, length, addService, addYear, refresh) {
        password = ""; explanation = ""; error = null
        if (anchor.isNotBlank()) {
            val result = PasswordGenerator.generateWithAnchor(anchor, length, true, true, true, true,
                addService, service, addYear)
            password = result.getOrNull()?.password.orEmpty()
            explanation = result.getOrNull()?.explanation.orEmpty()
            error = result.exceptionOrNull()?.message
        }
    }
    OutlinedTextField(anchor, { anchor = it }, label = { Text("Якорное слово") }, modifier = Modifier.fillMaxWidth())
    Text("Длина: $length")
    Slider(length.toFloat(), { length = it.toInt() }, valueRange = 12f..32f, steps = 19)
    Toggle("Добавить маркер сервиса", addService) { addService = it }
    if (addService) OutlinedTextField(service, { service = it }, label = { Text("Сервис") })
    Toggle("Добавить текущий год", addYear) { addYear = it }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text(password, fontFamily = FontFamily.Monospace)
    Text(explanation)
    OutlinedButton(onClick = { refresh++ }) { Text("Сгенерировать ещё") }
    Button(onClick = { onGenerated(password, explanation) }, enabled = password.isNotEmpty()) { Text("Выбрать") }
}
@Composable
internal fun AmpgGeneratorContent(initialService: String, profileId: Int?, initialYear: Int?,
    onGenerated: (String, String, String?, String?, String?) -> Unit, actionLabel: String = "Выбрать") {
    SensitiveContent()
    var helpVariant by remember { mutableStateOf<MnemonicPasswordGenerator.GenerationResult?>(null) }
    var phrase by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var service by remember { mutableStateOf(initialService) }
    var two by remember { mutableStateOf(false) }
    var length by remember { mutableIntStateOf(16) }
    var addService by remember { mutableStateOf(false) }
    var addYear by remember { mutableStateOf(false) }
    var yearText by remember { mutableStateOf((initialYear ?: Calendar.getInstance().get(Calendar.YEAR)).toString()) }
    var request by remember { mutableIntStateOf(0) }
    var generating by remember { mutableStateOf(false) }
    var generatedFor by remember { mutableStateOf<MnemonicPasswordGenerator.GenerationOptions?>(null) }
    var variants by remember { mutableStateOf<List<MnemonicPasswordGenerator.GenerationResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val options = MnemonicPasswordGenerator.GenerationOptions(phrase = phrase,
        phrase2 = second.takeIf { two }, serviceName = service, profileId = profileId,
        targetLength = length, splitMode = if (two) MnemonicPasswordGenerator.SplitMode.TWO_USERS
            else MnemonicPasswordGenerator.SplitMode.SINGLE_USER,
        addServiceMarker = addService, addYearMarker = addYear, year = yearText.toIntOrNull())
    LaunchedEffect(options, yearText, request) {
        variants = emptyList(); error = null; generatedFor = null; generating = false; helpVariant = null
        if (request == 0) return@LaunchedEffect
        if (addYear && (yearText.length != 4 || options.year !in 1000..9999)) {
            error = "Укажите год четырьмя цифрами"; return@LaunchedEffect
        }
        generating = true
        try {
            val result = withContext(Dispatchers.Default) {
                MnemonicPasswordGenerator.generateVariantsResult(options, 3)
            }
            variants = result.getOrDefault(emptyList())
            error = result.exceptionOrNull()?.message
            generatedFor = options
        } finally { generating = false }
    }
    val phraseKeyboard = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrect = false)
    OutlinedTextField(phrase, { phrase = it; request = 0 }, label = { Text("Мнемоническая фраза") },
        keyboardOptions = phraseKeyboard, modifier = Modifier.fillMaxWidth())
    Toggle("Две части", two) { two = it; request = 0; if (it) length = length.coerceAtLeast(16).let { n -> n + n % 2 } }
    if (two) OutlinedTextField(second, { second = it; request = 0 }, label = { Text("Фраза второй части") },
        keyboardOptions = phraseKeyboard, modifier = Modifier.fillMaxWidth())
    var cardHint by remember(phrase, second, two) {
        mutableStateOf(MnemonicHint.fromPhrases(phrase, second.takeIf { two }))
    }
    OutlinedTextField(cardHint, { cardHint = it }, label = { Text("Подсказка на карточке") },
        supportingText = { Text("Видна без PIN. Можно заменить фразу коротким напоминанием или очистить поле.") },
        modifier = Modifier.fillMaxWidth())
    val minimum = if (two) 16 else 12
    val step = if (two) 2 else 1
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { length = (length - step).coerceAtLeast(minimum); request = 0 }, enabled = length > minimum) { Text("−") }
        Text(if (two) "$length всего / ${length / 2} в части" else "Длина: $length")
        OutlinedButton(onClick = { length = (length + step).coerceAtMost(56); request = 0 }, enabled = length < 56) { Text("+") }
    }
    Slider(length.toFloat(), { value ->
        length = MnemonicPasswordGenerator.normalizeLength(value, options.splitMode)
        request = 0
    }, valueRange = minimum.toFloat()..56f, steps = if (two) 19 else 43)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (if (two) listOf(16, 20, 24, 32) else listOf(12, 16, 20, 24, 32)).forEach { n ->
            FilterChip(length == n, { length = n; request = 0 }, label = { Text("$n") })
        }
    }
    val previews = remember(phrase, second, two) {
        (listOf(phrase) + if (two) listOf(second) else emptyList()).map { MnemonicPasswordGenerator.preparePhrase(it).getOrNull() }
    }
    previews.forEachIndexed { index, preview ->
        preview?.let {
            Text((if (two) "${index + 1}: " else "Латиницей: ") + it.transliteratedWords.joinToString(" "),
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    Toggle("Маркер сервиса", addService) { addService = it; request = 0 }
    if (addService) OutlinedTextField(service, { service = it; request = 0 }, label = { Text("Сервис") },
        modifier = Modifier.fillMaxWidth())
    Toggle("Маркер года", addYear) { addYear = it; request = 0 }
    if (addYear) OutlinedTextField(yearText, { yearText = it.filter(Char::isDigit).take(4); request = 0 },
        label = { Text("Год") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
    Button(onClick = { request++ }, enabled = !generating) { Text(if (generating) "Подбираю…" else "Сгенерировать варианты") }
    if (generating) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (request > 0) error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    fun selectVariant(variant: MnemonicPasswordGenerator.GenerationResult) {
            val json = JSONObject().apply {
                put("algorithmVersion", MnemonicPasswordGenerator.ALGORITHM_VERSION)
                put("phraseStored", true)
                put("phrase", phrase.trim())
                if (two) put("phrase2", second.trim())
                put("cardHintStored", MnemonicHint.normalize(cardHint) != null)
                put("cardHintVersion", 1)
                put("stopWordsVersion", 1)
                put("serviceName", service); put("profileId", profileId ?: JSONObject.NULL)
                put("targetLength", length); put("splitMode", options.splitMode.name)
                put("variantOffset", variant.variantOffset)
                put("addServiceMarker", addService); put("addYearMarker", addYear)
                put("year", options.year ?: JSONObject.NULL)
            }.toString()
            val hint = "AMPG ${MnemonicPasswordGenerator.ALGORITHM_VERSION}; длина $length; вариант ${variant.variantOffset + 1}; " +
                "знаков из основы: ${variant.prefixLengths.joinToString(" / ")}"
            onGenerated(variant.password, "ampg_v${MnemonicPasswordGenerator.ALGORITHM_VERSION}_${variant.variantOffset}", hint, MnemonicHint.normalize(cardHint), json)
    }
    if (generatedFor == options && request > 0) variants.forEach { variant ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Вариант ${variant.variantOffset + 1}", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { helpVariant = variant }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Info, contentDescription = "Справка о пароле", modifier = Modifier.size(18.dp))
                    }
                }
                Text(if (two) "${variant.part1} / ${variant.part2}" else variant.password,
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { selectVariant(variant) }) { Text(actionLabel) }
            }
        }
    }
    helpVariant?.let { variant ->
        AlertDialog(onDismissRequest = { helpVariant = null },
            title = { Text("Справка о пароле") },
            text = {
                SensitiveContent()
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Длина: ${variant.password.length}. Точных повторов нет.")
                    val parts = if (variant.part1 != null && variant.part2 != null)
                        listOf(variant.part1, variant.part2) else listOf(variant.password)
                    parts.forEachIndexed { index, part ->
                        if (parts.size > 1) Text("Часть ${index + 1}: ${part.length} знаков", style = MaterialTheme.typography.labelLarge)
                        Text("Строчных: ${part.count(Char::isLowerCase)}; заглавных: ${part.count(Char::isUpperCase)}; " +
                            "цифр: ${part.count(Char::isDigit)}; спецсимволов: ${part.count { !it.isLetterOrDigit() }}.")
                    }
                    HorizontalDivider()
                    Text("Замены в этом пароле", style = MaterialTheme.typography.titleSmall)
                    val details = variant.explanation.lineSequence().filterNot {
                        it.startsWith("AMPG ") || it.startsWith("Известная фраза") ||
                            it.startsWith("Для повторов") || it.startsWith("Расширенные замены") ||
                            it.startsWith("Оставшиеся повторы")
                    }.joinToString("\n")
                    Text(details, style = MaterialTheme.typography.bodySmall)
                    Text("Для повторного получения: та же фраза и настройки, вариант ${variant.variantOffset + 1}.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { helpVariant = null }) { Text("Понятно") } })
    }
}
