package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.viewmodel.AuthViewModel

@Composable
fun PasswordViewDialog(
    entry: Entry,
    onDismiss: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    var masterPwd by remember { mutableStateOf("") }
    var isRevealed by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(entry.service, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(entry.username, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                
                if (!isRevealed) {
                    Text("Введите мастер-пароль для просмотра:", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = masterPwd,
                        onValueChange = { masterPwd = it; isError = false },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = isError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isError) Text("Неверный пароль!", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onDismiss) { Text("Отмена") }
                        Button(onClick = {
                            // ✅ Проверка мастер-пароля
                            if (authViewModel.tryUnlock(masterPwd)) {
                                isRevealed = true
                            } else {
                                isError = true
                            }
                        }) { Text("Показать") }
                    }
                } else {
                    Text("Пароль:", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = entry.password, // ✅ Это вызывает CryptoUtils.decrypt
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    if (!entry.quickTags.isNullOrBlank()) {
                        Text("Подсказки: ${entry.quickTags}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onDismiss) { Text("Закрыть") }
                    }
                }
            }
        }
    }
}
