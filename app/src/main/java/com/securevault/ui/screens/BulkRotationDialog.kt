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
import com.securevault.data.Entry
import com.securevault.utils.PasswordGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkRotationDialog(
    entries: List<Entry>,
    onDismiss: () -> Unit,
    onBulkReplace: (List<Triple<String, String, String>>) -> Unit
) {
    var replacements by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var length by remember { mutableIntStateOf(16) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSpecial by remember { mutableStateOf(true) }

    LaunchedEffect(length, useUpper, useDigits, useSpecial) {
        replacements = entries.map { entry ->
            val result = PasswordGenerator.generate(length, useUpper, useDigits, useSpecial, null)
            Triple(entry.id, result.password, "random")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Массовая ротация (${entries.size})", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Параметры генерации", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Длина: $length", modifier = Modifier.weight(1f))
                            Slider(
                                value = length.toFloat(),
                                onValueChange = { length = it.toInt() },
                                valueRange = 8f..32f,
                                steps = 24,
                                modifier = Modifier.weight(2f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = useUpper, onCheckedChange = { useUpper = it })
                            Text("Заглавные", Modifier.padding(start = 8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = useDigits, onCheckedChange = { useDigits = it })
                            Text("Цифры", Modifier.padding(start = 8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it })
                            Text("Спецсимволы", Modifier.padding(start = 8.dp))
                        }
                    }
                }

                Text("Записи для ротации:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                entries.forEachIndexed { index, entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(entry.service, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(entry.username, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (index < replacements.size) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Новый пароль: ${replacements[index].second}",
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onBulkReplace(replacements) }) {
                Text("Заменить все")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
