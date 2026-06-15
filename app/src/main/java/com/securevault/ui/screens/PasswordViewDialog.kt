@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordViewDialog(
    entry: Entry,
    onDismiss: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    var masterPwd by remember { mutableStateOf("") }
    var isRevealed by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.service, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
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
                            if (authViewModel.tryUnlock(masterPwd)) {
                                isRevealed = true
                            } else {
                                isError = true
                            }
                        }) { Text("Показать") }
                    }
                } else {
                    // ✅ Текущий пароль
                    Text("Текущий пароль:", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = entry.password,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    
                    if (!entry.textHint.isNullOrBlank()) {
                        Text("Подсказка: ${entry.textHint}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // ✅ КНОПКА ИСТОРИИ
                    val history = entry.getPasswordHistory()
                    if (history.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showHistory = !showHistory },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.History, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("История изменений (${history.size})")
                        }
                        
                        // ✅ ПОКАЗ ИСТОРИИ
                        if (showHistory) {
                            Spacer(Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .heightIn(max = 200.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text("Предыдущие пароли:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(Modifier.height(8.dp))
                                    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                    history.forEachIndexed { idx, item ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = item.password,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = dateFormat.format(Date(item.date)),
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
