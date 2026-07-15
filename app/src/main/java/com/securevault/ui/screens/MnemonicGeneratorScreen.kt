@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicGeneratorScreen(
    profileId: Int?,
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    LaunchedEffect(profileId) {
        if (profileId != null) viewModel.setCurrentProfile(profileId)
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentProfileId by viewModel.currentProfileId.collectAsState()
    val effectiveProfileId = profileId ?: currentProfileId

    var phrase by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    var includeLeet by remember { mutableStateOf(true) }
    var splitMode by remember { mutableStateOf(MnemonicPasswordGenerator.SplitMode.SINGLE_USER) }
    var targetLength by remember { mutableIntStateOf(16) }
    
    var variantPages by remember { mutableStateOf<List<List<MnemonicPasswordGenerator.GenerationResult>>>(emptyList()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var nextOffset by remember { mutableIntStateOf(0) }
    var isGenerating by remember { mutableStateOf(false) }
    var noMoreVariants by remember { mutableStateOf(false) }
    var selectedVariantIndex by remember { mutableIntStateOf(-1) }
    var showExplanation by remember { mutableStateOf<MnemonicPasswordGenerator.GenerationResult?>(null) }

    fun loadNextPage() {
        if (isGenerating || noMoreVariants) return
        isGenerating = true
        val newVariants = mutableListOf<MnemonicPasswordGenerator.GenerationResult>()
        var offset = nextOffset
        val maxAttempts = 60
        var attempts = 0
        
        val effectiveLength = if (splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS) {
            when { targetLength <= 16 -> 16; targetLength <= 18 -> 18; else -> 20 }
        } else { targetLength }

        while (newVariants.size < 3 && attempts < maxAttempts) {
            val options = MnemonicPasswordGenerator.GenerationOptions(
                phrase = phrase,
                serviceName = serviceName,
                username = "", 
                profileId = effectiveProfileId,
                targetLength = effectiveLength,
                includeLeet = includeLeet,
                variantOffset = offset,
                enforceUniqueChars = true,
                splitMode = splitMode
            )
            val variants = MnemonicPasswordGenerator.generateVariants(options, count = 1)
            if (variants.isNotEmpty()) {
                val variant = variants.first()
                if (MnemonicPasswordGenerator.validatePasswordPartsComplexity(variant.password, splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS) &&
                    !variantPages.flatten().any { it.password == variant.password } &&
                    !newVariants.any { it.password == variant.password }
                ) {
                    newVariants.add(variant)
                }
            }
            offset++
            attempts++
        }
        
        if (newVariants.isNotEmpty()) {
            //  Оборачиваем newVariants в listOf(), чтобы добавить как новую страницу
            variantPages = variantPages + listOf(newVariants)
            currentPageIndex = variantPages.size - 1
            nextOffset = offset
            noMoreVariants = newVariants.size < 3
        } else {
            noMoreVariants = true
        }
        isGenerating = false
        selectedVariantIndex = -1
    }

    fun loadPreviousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            selectedVariantIndex = -1
        }
    }

    LaunchedEffect(phrase, serviceName, includeLeet, splitMode, targetLength) {
        variantPages = emptyList()
        currentPageIndex = 0
        nextOffset = 0
        noMoreVariants = false
        selectedVariantIndex = -1
        if (phrase.isNotBlank()) loadNextPage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AMPG генератор", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Режим генерации", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = splitMode == MnemonicPasswordGenerator.SplitMode.SINGLE_USER, onClick = { splitMode = MnemonicPasswordGenerator.SplitMode.SINGLE_USER })
                        Text("Обычный пароль", Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS, onClick = { splitMode = MnemonicPasswordGenerator.SplitMode.TWO_USERS })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text("Для двух пользователей")
                            Text("Один пароль на две равные части", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            OutlinedTextField(value = phrase, onValueChange = { phrase = it }, label = { Text("Мнемоническая фраза") }, placeholder = { Text("например: моя кошка любит молоко") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = serviceName, onValueChange = { serviceName = it }, label = { Text("Сервис (влияет на генерацию)") }, placeholder = { Text("например: Gmail") }, modifier = Modifier.fillMaxWidth())
            
            if (splitMode == MnemonicPasswordGenerator.SplitMode.SINGLE_USER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Длина: $targetLength", modifier = Modifier.weight(1f))
                    Slider(value = targetLength.toFloat(), onValueChange = { targetLength = it.toInt() }, valueRange = 12f..24f, steps = 12, modifier = Modifier.weight(2f))
                }
            } else {
                Text("Длина пароля:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(16, 18, 20).forEach { length ->
                        FilterChip(selected = targetLength == length, onClick = { targetLength = length }, label = { Text("$length") })
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeLeet, onCheckedChange = { includeLeet = it })
                Text("Позиционные замены (leet)", Modifier.padding(start = 8.dp))
            }

            if (variantPages.isNotEmpty()) {
                Text("Варианты ${currentPageIndex * 3 + 1}–${minOf((currentPageIndex + 1) * 3, variantPages.flatten().size)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                val currentPage = variantPages[currentPageIndex]
                currentPage.forEachIndexed { index, result ->
                    val isSelected = selectedVariantIndex == index
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                        onClick = { selectedVariantIndex = index }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Вариант ${currentPageIndex * 3 + index + 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(result.password))
                                    Toast.makeText(context, "Скопировано!", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Копировать пароль", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(result.password, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            if (result.splitMode == MnemonicPasswordGenerator.SplitMode.TWO_USERS) {
                                Text("Режим: один пароль на две равные части", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                Text("Часть 1: ${result.part1?.length ?: 0} символов", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Часть 2: ${result.part2?.length ?: 0} символов", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Без повторов: ${if (result.hasUniqueChars) "Да" else "Нет"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Подсказка: ${result.mnemonicHint}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(result.strength.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = when (result.strength) {
                                PasswordGenerator.Strength.VERY_STRONG -> Color(0xFF4CAF50)
                                PasswordGenerator.Strength.STRONG -> MaterialTheme.colorScheme.primary
                                PasswordGenerator.Strength.MEDIUM -> MaterialTheme.colorScheme.tertiary
                                PasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            })
                            
                            TextButton(onClick = { showExplanation = result }, modifier = Modifier.align(Alignment.End)) {
                                Text("Как собран пароль", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { loadPreviousPage() }, enabled = currentPageIndex > 0 && !isGenerating) {
                        Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Предыдущие")
                    }
                    
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        TextButton(onClick = { loadNextPage() }, enabled = !noMoreVariants && !isGenerating) {
                            Text("Следующие")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp))
                        }
                    }
                }
                
                if (noMoreVariants && !isGenerating) {
                    Text(
                        "Новых подходящих вариантов не найдено. Попробуйте изменить фразу, увеличить длину или ослабить ограничения.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (showExplanation != null) {
        AlertDialog(
            onDismissRequest = { showExplanation = null },
            title = { Text("Как собран пароль") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(showExplanation!!.explanation, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = { showExplanation = null }) { Text("Понятно") }
            }
        )
    }
}
