@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.viewmodel.AuthViewModel
import com.securevault.viewmodel.VaultViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PasswordViewDialog(
    entry: Entry,
    onDismiss: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    vaultViewModel: VaultViewModel = hiltViewModel()
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Доступ к паролю") },
        text = {
            if (!verified) {
                Column {
                    Text("Введите мастер-пароль:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("Мастер-пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        isError = error
                    )
                    if (error) Text("Неверно", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            } else {
                Column {
                    Text("Сервис: ${entry.service}", fontWeight = FontWeight.Bold)
                    Text("Логин: ${entry.username}")
                    Spacer(Modifier.height(8.dp))
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                entry.password,
                                fontSize = 18.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            entry.emojiHint?.let { Text("💡 $it", fontSize = 12.sp) }
                            Divider(Modifier.padding(vertical = 8.dp))
                            Text("Создан: ${fmt(entry.createdAt)}")
                            Text("Обновлён: ${fmt(entry.lastChanged)}")
                            entry.nextRotationDate?.let {
                                Text("След. смена: ${fmt(it)}", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!verified) {
                Button(onClick = {
                    if (viewModel.verifyPassword(input)) {
                        verified = true
                        error = false
                    } else {
                        error = true
                    }
                }) {
                    Text("Показать")
                }
            } else {
                Button(onClick = {
                    val newPwd = PasswordGenerator.generate(
                        length = 12,
                        useUpper = true,
                        useDigits = true,
                        useSpecial = true
                    ).password
                    vaultViewModel.updatePassword(entry.id, newPwd)
                    onDismiss()
                }) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Обновить пароль")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

private fun fmt(ts: Long) = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ts))
