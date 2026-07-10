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
        if (profileId != null) {
            viewModel.setCurrentProfile(profileId)
        }
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var phrase by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    var includeLeet by remember { mutableStateOf(true) }
    var includeServiceCode by remember { mutableStateOf(true) }
    var includeRotationCode by remember { mutableStateOf(true) }
    var separator by remember { mutableStateOf("") }
    var enforceUniqueChars by remember { mutableStateOf(true) }

    var globalOffset by remember { mutableIntStateOf(0) }
    var variants by remember { mutableStateOf<List<MnemonicPasswordGenerator.GenerationResult>>(emptyList()) }
    var selectedVariantIndex by remember { mutableIntStateOf(-1) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun generateVariants() {
        if (phrase.isBlank()) {
            errorMessage = "Введите мнемоническую фразу"
            return
        }

        val options = MnemonicPasswordGenerator.GenerationOptions(
            phrase = phrase,
            serviceName = serviceName,
            targetLength = 16,
            includeLeet = includeLeet,
            includeServiceCode = includeServiceCode,
            includeRotationCode = includeRotationCode,
            variantOffset = globalOffset,
            separator = separator,
            enforceUniqueChars = enforceUniqueChars
        )

        val results = MnemonicPasswordGenerator.generateVariants(options, count = 5)

        if (results.isEmpty()) {
            errorMessage = "Не удалось создать все варианты по выбранным правилам. Увеличьте фразу или измените параметры."
        } else {
            variants = results
            selectedVariantIndex = -1
            errorMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AMPG v2 генератор", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                label = { Text("Мнемоническая фраза") },
                placeholder = { Text("например: метроном жёлтый камень") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = serviceName,
                onValueChange = { serviceName = it },
                label = { Text("Название сервиса") },
                placeholder = { Text("например: Gmail") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Разделитель между частями:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("" to "Нет", "-" to "-", "*" to "*", "@" to "@").forEach { (value, label) ->
                    FilterChip(
                        selected = separator == value,
                        onClick = { separator = value },
                        label = { Text(label) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeLeet, onCheckedChange = { includeLeet = it })
                Text("Leet-замены (a→@, o→0...)", Modifier.padding(start = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeServiceCode, onCheckedChange = { includeServiceCode = it })
                Text("Код сервиса", Modifier.padding(start = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeRotationCode, onCheckedChange = { includeRotationCode = it })
                Text("Код ротации (MMYY)", Modifier.padding(start = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = enforceUniqueChars, onCheckedChange = { enforceUniqueChars = it })
                Text("Без повторяющихся символов", Modifier.padding(start = 8.dp))
            }

            Button(
                onClick = { generateVariants() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Сгенерировать")
            }

            if (errorMessage != null) {
                AlertDialog(
                    onDismissRequest = { errorMessage = null },
                    icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Не удалось создать пароль") },
                    text = { Text(errorMessage ?: "") },
                    confirmButton = {
                        TextButton(onClick = { errorMessage = null }) {
                            Text("Понятно")
                        }
                    }
                )
            }

            if (variants.isNotEmpty()) {
                Text("Выберите вариант:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                variants.forEachIndexed { index, result ->
                    val isSelected = selectedVariantIndex == index

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        onClick = { selectedVariantIndex = index }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(result.variantName, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                result.password,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))

                            Text(
                                "Формат: два слова ${if (separator.isEmpty()) "без разделителя" else "с разделителем '$separator'"}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Каждая часть усилена цифрами и спецсимволами",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Без повторов: ${if (result.hasUniqueChars) "Да" else "Нет"}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Подсказка: ${result.mnemonicHint}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(result.password))
                                        Toast.makeText(context, "Скопировано!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Копировать", fontSize = 11.sp)
                                }

                                //  when с else веткой
                                Text(
                                    result.strength.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (result.strength) {
                                        PasswordGenerator.Strength.VERY_STRONG -> Color(0xFF4CAF50)
                                        PasswordGenerator.Strength.STRONG -> MaterialTheme.colorScheme.primary
                                        PasswordGenerator.Strength.MEDIUM -> MaterialTheme.colorScheme.tertiary
                                        PasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        globalOffset += variants.size
                        generateVariants()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Сгенерировать ещё раз (набор №${(globalOffset / variants.size) + 2})")
                }
            }
        }
    }
}
