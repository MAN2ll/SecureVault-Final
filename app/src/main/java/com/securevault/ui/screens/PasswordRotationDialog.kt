@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.data.Entry
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel

enum class RotationMode { RANDOM, MNEMONIC, MANUAL, FROM_EXISTING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordRotationDialog(
    currentEntryId: String,
    serviceName: String,
    currentHint: String?,
    generationType: String,
    rotationMonth: Int?,
    rotationYear: Int?,
    allProfileEntries: List<Entry>,
    onDismiss: () -> Unit,
    onPasswordReplaced: (
        newPassword: String,
        newHint: String?,
        newGenerationType: String,
        mnemonicPhrase: String?,
        mnemonicOptions: String?
    ) -> Unit,
    viewModel: VaultViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var selectedMode by remember { mutableStateOf(RotationMode.RANDOM) }
    var manualPassword by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf<String?>(null) }

    var phrase by remember { mutableStateOf(currentHint ?: "") }

    var splitMode by remember { mutableStateOf(MnemonicPasswordGenerator.SplitMode.SINGLE_USER) }
    var targetLength by remember { mutableIntStateOf(16) }
    
    var rotVariantPages by remember { mutableStateOf<List<List<MnemonicPasswordGenerator.GenerationResult>>>(emptyList()) }
    var rotCurrentPageIndex by remember { mutableIntStateOf(0) }
    var rotNextOffset by remember { mutableIntStateOf(0) }
    var rotIsGenerating by remember { mutableStateOf(false) }
    var rotNoMoreVariants by remember { mutableStateOf(false) }
    var rotSelectedVariantIndex by remember { mutableIntStateOf(-1) }
    var rotShowExplanation by remember { mutableStateOf<MnemonicPasswordGenerator.GenerationResult?>(null) }

    var generatedRandomPwd by remember { mutableStateOf("") }
    var randomLength by remember { mutableIntStateOf(16) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSpecial by remember { mutableStateOf(true) }

    var selectedExistingEntry by remember { mutableStateOf<Entry?>(null) }

    val availableEntries = remember(allProfileEntries, currentEntryId) {
        allProfileEntries.filter { it.id != currentEntryId }
    }
    
    val currentEntryForSeed = remember(allProfileEntries, currentEntryId) {
        allProfileEntries.find { it.id == currentEntryId }
    }

    fun generateRandom() {
        val result = PasswordGenerator.generate(randomLength, useUpper, useDigits, useSpecial, context)
        generatedRandomPwd = result.password
    }

    fun rotLoadNextPage() {
        if (rotIsGenerating || rotNoMoreVariants || phrase.isBlank()) return
        rotIsGenerating = true
        
        val effectiveLength = if (splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS) {
            when { targetLength <= 16 -> 16; targetLength <= 18 -> 18; else -> 20 }
        } else { targetLength }

        val options = MnemonicPasswordGenerator.GenerationOptions(
            phrase = phrase,
            serviceName = serviceName,
            username = currentEntryForSeed?.username ?: "",
            profileId = currentEntryForSeed?.profileId,
            targetLength = effectiveLength,
            rotationMonth = rotationMonth,
            rotationYear = rotationYear,
            variantOffset = rotNextOffset,
            splitMode = splitMode
        )
        
        val newVariants = MnemonicPasswordGenerator.generateVariants(options, count = 3)
        if (newVariants.isNotEmpty()) {
            rotVariantPages = rotVariantPages + listOf(newVariants)
            rotCurrentPageIndex = rotVariantPages.size - 1
            rotNextOffset = options.variantOffset + 300
            rotNoMoreVariants = newVariants.size < 3
        } else {
            rotNoMoreVariants = true
        }
        rotIsGenerating = false
        rotSelectedVariantIndex = -1
    }

    fun rotLoadPreviousPage() {
        if (rotCurrentPageIndex > 0) {
            rotCurrentPageIndex--
            rotSelectedVariantIndex = -1
        }
    }

    LaunchedEffect(selectedMode) {
        if (selectedMode == RotationMode.RANDOM) generateRandom()
    }
    
    LaunchedEffect(phrase, splitMode, targetLength) {
        if (selectedMode == RotationMode.MNEMONIC) {
            rotVariantPages = emptyList()
            rotCurrentPageIndex = 0
            rotNextOffset = 0
            rotNoMoreVariants = false
            rotSelectedVariantIndex = -1
            if (phrase.isNotBlank()) rotLoadNextPage()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Ротация: $serviceName", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Режим генерации", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        RotationMode.entries.forEach { mode ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(
                                    selected = selectedMode == mode,
                                    onClick = { selectedMode = mode }
                                )
                                Text(
                                    when (mode) {
                                        RotationMode.RANDOM -> "Случайный пароль"
                                        RotationMode.MNEMONIC -> "Мнемонический (AMPG v2)"
                                        RotationMode.MANUAL -> "Ввести вручную"
                                        RotationMode.FROM_EXISTING -> "Из другой записи"
                                    },
                                    Modifier.padding(start = 8.dp),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                when (selectedMode) {
                    RotationMode.RANDOM -> {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Параметры", fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Длина: $randomLength", modifier = Modifier.weight(1f))
                                    Slider(value = randomLength.toFloat(), onValueChange = { randomLength = it.toInt() }, valueRange = 8f..32f, steps = 24, modifier = Modifier.weight(2f))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = useUpper, onCheckedChange = { useUpper = it }); Text("A-Z", Modifier.padding(start = 8.dp)) }
                                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = useDigits, onCheckedChange = { useDigits = it }); Text("0-9", Modifier.padding(start = 8.dp)) }
                                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it }); Text("!@#", Modifier.padding(start = 8.dp)) }

                                if (generatedRandomPwd.isNotEmpty()) {
                                    Text(generatedRandomPwd, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { generateRandom() }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Сгенерировать ещё раз", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(generatedRandomPwd))
                                        android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать пароль", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    RotationMode.MNEMONIC -> {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("AMPG v2", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = splitMode == MnemonicPasswordGenerator.SplitMode.SINGLE_USER, onClick = { splitMode = MnemonicPasswordGenerator.SplitMode.SINGLE_USER })
                                    Text(text = "Обычный", modifier = Modifier.padding(start = 4.dp), fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS, onClick = { splitMode = MnemonicPasswordGenerator.SplitMode.TWO_USERS })
                                    Text(text = "Для двух пользователей", modifier = Modifier.padding(start = 4.dp), fontSize = 12.sp)
                                }

                                OutlinedTextField(value = phrase, onValueChange = { phrase = it }, label = { Text("Мнемоническая фраза") }, modifier = Modifier.fillMaxWidth())

                                if (splitMode == MnemonicPasswordGenerator.SplitMode.SINGLE_USER) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Длина: $targetLength", modifier = Modifier.weight(1f), fontSize = 12.sp)
                                        Slider(value = targetLength.toFloat(), onValueChange = { targetLength = it.toInt() }, valueRange = 12f..24f, steps = 12, modifier = Modifier.weight(2f))
                                    }
                                } else {
                                    Text("Длина:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(16, 18, 20).forEach { length ->
                                            FilterChip(selected = targetLength == length, onClick = { targetLength = length }, label = { Text("$length") })
                                        }
                                    }
                                }

                                if (rotVariantPages.isNotEmpty()) {
                                    val currentPage = rotVariantPages[rotCurrentPageIndex]
                                    Text("Варианты ${rotCurrentPageIndex * 3 + 1}–${rotCurrentPageIndex * 3 + currentPage.size}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    
                                    currentPage.forEachIndexed { index, result ->
                                        val isSelected = rotSelectedVariantIndex == index
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { rotSelectedVariantIndex = index },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(text = "Вариант ${rotCurrentPageIndex * 3 + index + 1}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(text = result.password, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                        if (result.splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS) {
                                                            Text("Части: ${result.part1?.length ?: 0} + ${result.part2?.length ?: 0}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                    Column {
                                                        IconButton(onClick = {
                                                            clipboardManager.setText(AnnotatedString(result.password))
                                                            android.widget.Toast.makeText(context, "Скопировано!", android.widget.Toast.LENGTH_SHORT).show()
                                                        }) {
                                                            Icon(Icons.Default.ContentCopy, contentDescription = "Копировать", Modifier.size(18.dp))
                                                        }
                                                        RadioButton(selected = isSelected, onClick = { rotSelectedVariantIndex = index })
                                                    }
                                                }
                                                TextButton(onClick = { rotShowExplanation = result }, modifier = Modifier.align(Alignment.End)) { Text("Как собран", fontSize = 10.sp) }
                                            }
                                        }
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { rotLoadPreviousPage() }, enabled = rotCurrentPageIndex > 0 && !rotIsGenerating) {
                                            Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Предыдущие")
                                        }
                                        if (rotIsGenerating) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        } else {
                                            TextButton(onClick = { rotLoadNextPage() }, enabled = !rotNoMoreVariants && !rotIsGenerating) {
                                                Text("Следующие")
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    if (rotNoMoreVariants && !rotIsGenerating) {
                                        Text("Новых подходящих вариантов не найдено.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                                    }
                                } else if (phrase.isNotBlank() && !rotIsGenerating) {
                                    Text("Не удалось сгенерировать валидные варианты. Попробуйте изменить фразу или длину.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    RotationMode.MANUAL -> {
                        OutlinedTextField(value = manualPassword, onValueChange = { manualPassword = it; showError = null }, label = { Text("Новый пароль") }, modifier = Modifier.fillMaxWidth())
                    }

                    RotationMode.FROM_EXISTING -> {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Выберите запись, пароль которой будет использован:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(8.dp))
                                if (availableEntries.isEmpty()) {
                                    Text("В этом профиле нет других записей.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                } else {
                                    LazyColumn(modifier = Modifier.height(200.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(availableEntries) { entry ->
                                            val isSelected = selectedExistingEntry?.id == entry.id
                                            Card(
                                                modifier = Modifier.fillMaxWidth().clickable { selectedExistingEntry = entry },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            ) {
                                                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(entry.service, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                        Text(entry.username, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    RadioButton(selected = isSelected, onClick = { selectedExistingEntry = entry })
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showError != null) {
                    Text(showError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when (selectedMode) {
                    RotationMode.RANDOM -> {
                        if (generatedRandomPwd.isBlank()) { showError = "Сгенерируйте пароль"; return@Button }
                        onPasswordReplaced(generatedRandomPwd, null, "random", null, null)
                    }
                    RotationMode.MNEMONIC -> {
                        if (rotSelectedVariantIndex < 0 || rotVariantPages.isEmpty() || rotCurrentPageIndex >= rotVariantPages.size) { 
                            showError = "Выберите вариант"; return@Button 
                        }
                        val selected = rotVariantPages[rotCurrentPageIndex][rotSelectedVariantIndex]
                        onPasswordReplaced(selected.password, selected.mnemonicHint, "mnemonic", phrase, null)
                    }
                    RotationMode.MANUAL -> {
                        if (manualPassword.isBlank()) { showError = "Введите пароль"; return@Button }
                        onPasswordReplaced(manualPassword, null, "manual", null, null)
                    }
                    RotationMode.FROM_EXISTING -> {
                        if (selectedExistingEntry == null) { showError = "Выберите запись"; return@Button }
                        val passwordToUse = try { selectedExistingEntry!!.password } catch (e: Exception) {
                            showError = "Не удалось расшифровать пароль выбранной записи"
                            return@Button
                        }
                        onPasswordReplaced(passwordToUse, null, "shuffled", null, null)
                    }
                }
            }) { Text("Заменить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (rotShowExplanation != null) {
        AlertDialog(
            onDismissRequest = { rotShowExplanation = null },
            title = { Text("Как собран пароль") },
            text = { Text(rotShowExplanation!!.explanation, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
            confirmButton = { TextButton(onClick = { rotShowExplanation = null }) { Text("Понятно") } }
        )
    }
}
