package com.securevault.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.viewmodel.AuthViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PasswordViewDialog(
    entry: Entry,
    onDismiss: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var passwordInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var isVerified by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтвердите доступ", fontWeight = FontWeight.Bold) },
        text = {
            if (!isVerified) {
                Column {
                    Text("Введите мастер-пароль для просмотра:", fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Мастер-пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = showError,
                        supportingText = {
                            if (showError) Text("Неверный пароль", color = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            } else {
                Column {
                    Text("Сервис: ${entry.service}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = entry.password, // Расшифрованный пароль из Entry
                                    fontSize = 18.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { /* Логика копирования */ }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                                }
                            }
                            
                            entry.emojiHint?.let {
                                Text(" Подсказка: $it", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                            
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            
                            Text("Создан: ${formatDate(entry.createdAt)}", fontSize = 12.sp)
                            Text("Обновлен: ${formatDate(entry.lastChanged)}", fontSize = 12.sp)
                            entry.nextRotationDate?.let {
                                Text("След. смена: ${formatDate(it)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isVerified) {
                Button(onClick = {
                    if (viewModel.verifyPassword(passwordInput)) {
                        isVerified = true
                        showError = false
                    } else {
                        showError = true
                    }
                }) {
                    Text("Показать")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))
}
