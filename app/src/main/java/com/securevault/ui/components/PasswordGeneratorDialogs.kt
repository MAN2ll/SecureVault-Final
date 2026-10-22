@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.securevault.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.securevault.data.Profile
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator
import org.json.JSONObject
import java.util.Calendar

@Composable
fun UnifiedPasswordGeneratorDialog(onDismiss: () -> Unit,
    onGenerated: (String, String, String?, String?, String?) -> Unit,
    initialServiceName: String = "", profile: Profile? = null, initialMode: Int = 0) {
    var mode by remember { mutableIntStateOf(initialMode.coerceIn(0, 3)) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Генератор паролей") }, text = {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(listOf(0 to "Случайный", 1 to "2 части"), listOf(2 to "Якорь", 3 to "AMPG")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (value, label) -> FilterChip(mode == value, { mode = value }, label = { Text(label) }) }
                }
            }
            when (mode) {
                0, 1 -> RandomContent(mode == 1) { pwd ->
                    onGenerated(pwd, if (mode == 1) "random_two_part" else "random", null, null, null)
                }
                2 -> AnchorContent(initialServiceName) { pwd, hint -> onGenerated(pwd, "random_anchor", hint, null, null) }
                3 -> AmpgContent(initialServiceName, profile?.id, onGenerated)
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
private fun AmpgContent(initialService: String, profileId: Int?,
    onGenerated: (String, String, String?, String?, String?) -> Unit) {
    var phrase by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var service by remember { mutableStateOf(initialService) }
    var two by remember { mutableStateOf(false) }
    var length by remember { mutableIntStateOf(16) }
    var addService by remember { mutableStateOf(false) }
    var addYear by remember { mutableStateOf(false) }
    val year = remember { Calendar.getInstance().get(Calendar.YEAR) }
    var variants by remember { mutableStateOf<List<MnemonicPasswordGenerator.GenerationResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val options = MnemonicPasswordGenerator.GenerationOptions(phrase = phrase,
        phrase2 = second.takeIf { two }, serviceName = service, profileId = profileId,
        targetLength = length, splitMode = if (two) MnemonicPasswordGenerator.SplitMode.TWO_USERS
            else MnemonicPasswordGenerator.SplitMode.SINGLE_USER,
        addServiceMarker = addService, addYearMarker = addYear, year = year)
    LaunchedEffect(options) { variants = emptyList(); error = null }
    OutlinedTextField(phrase, { phrase = it }, label = { Text("Мнемоническая фраза") }, modifier = Modifier.fillMaxWidth())
    Toggle("Два пользователя", two) { two = it; if (it && length !in listOf(16, 18, 20)) length = 16 }
    if (two) {
        OutlinedTextField(second, { second = it }, label = { Text("Фраза второго пользователя") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(16, 18, 20).forEach { n -> FilterChip(length == n, { length = n }, label = { Text("$n") }) }
        }
    } else {
        Text("Длина: $length")
        Slider(length.toFloat(), { length = it.toInt() }, valueRange = 12f..32f, steps = 19)
    }
    Toggle("Маркер сервиса", addService) { addService = it }
    if (addService) OutlinedTextField(service, { service = it }, label = { Text("Сервис") })
    Toggle("Маркер года ($year)", addYear) { addYear = it }
    Button(onClick = {
        variants = if (phrase.isBlank() || (two && second.isBlank())) emptyList()
            else MnemonicPasswordGenerator.generateVariants(options, 3)
        error = if (variants.isEmpty()) "Нет подходящих вариантов. Измените фразу, длину или маркеры." else null
    }) { Text("Сгенерировать варианты") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    variants.forEach { variant ->
        Card(Modifier.fillMaxWidth().clickable {
            val json = JSONObject().apply {
                put("phrase", phrase); put("phrase2", second.takeIf { two } ?: JSONObject.NULL)
                put("serviceName", service); put("username", options.username)
                put("profileId", profileId ?: JSONObject.NULL); put("targetLength", length)
                put("splitMode", options.splitMode.name); put("variantOffset", variant.variantOffset)
                put("addServiceMarker", addService); put("addYearMarker", addYear); put("year", year)
                put("rotationMonth", JSONObject.NULL); put("rotationYear", JSONObject.NULL)
            }.toString()
            onGenerated(variant.password, "ampg_v${variant.variantOffset}", variant.explanation,
                variant.mnemonicHint, json)
        }) {
            Column(Modifier.padding(12.dp)) {
                Text(variant.password, fontFamily = FontFamily.Monospace)
                Text(variant.variantName); Text(variant.explanation)
                Text("Выбрать этот вариант")
            }
        }
    }
}
