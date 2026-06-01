@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.security.DataWiper
import com.securevault.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onSetupRequired: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val authState by viewModel.authState.collectAsState()
    val failedAttempts by viewModel.failedAttempts.collectAsState()
    val wipeTriggered by viewModel.wipeTriggered.collectAsState()
    
    // Проверка первого запуска
    LaunchedEffect(Unit) {
        if (!viewModel.isSetupComplete()) {
            onSetupRequired()
        }
    }
    
    // Реакция на состояние авторизации
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthViewModel.AuthState.Success -> onUnlocked()
            is AuthViewModel.AuthState.Blocked -> if (state.isWipe) showWipeDialog = true
            is AuthViewModel.AuthState.WipeTriggered -> showWipeDialog = true
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "SecureVault", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        
        if (wipeTriggered || authState is AuthViewModel.AuthState.WipeTriggered) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Превышено количество попыток", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Данные будут удалены для защиты.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showWipeDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить данные") }
                OutlinedButton(onClick = { viewModel.resetSecurity(); showError = false }, modifier = Modifier.weight(1f)) { Text("Сбросить") }
            }
        } else if (authState is AuthViewModel.AuthState.Blocked) {
            val remaining = (authState as AuthViewModel.AuthState.Blocked).remainingMs
            val formatted = viewModel.formatLockoutTime(remaining)
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Вход заблокирован", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Попробуйте через $formatted", fontSize = 13.sp)
                }
            }
        } else {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Мастер-пароль") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = showError,
                supportingText = {
                    if (showError) Text(text = when {
                        failedAttempts >= 7 -> "Осталось ${10 - failedAttempts} попыток"
                        failedAttempts >= 3 -> "Неверный пароль"
                        else -> ""
                    }, color = MaterialTheme.colorScheme.error)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // ✅ Только кнопка входа (без биометрии)
            Button(
                onClick = {
                    try {
                        showError = false
                        if (password.length < 4) { showError = true; return@Button }
                        val isValid = viewModel.verifyPassword(password)
                        if (!isValid) showError = true else password = ""
                    } catch (e: Exception) {
                        showError = true
                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = authState !is AuthViewModel.AuthState.Blocked
            ) {
                Text("Разблокировать")
            }
            
            if (failedAttempts > 0 && failedAttempts < 3) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Попыток: $failedAttempts/10", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    
    // Диалог подтверждения удаления
    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Подтверждение удаления") },
            text = { Text("Все данные будут безвозвратно удалены. Это действие нельзя отменить.") },
            confirmButton = {
                Button(onClick = {
                    showWipeDialog = false
                    scope.launch {
                        try {
                            val result = viewModel.triggerWipe()
                            if (result is DataWiper.WipeResult.Success) onUnlocked()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showWipeDialog = false }) { Text("Отмена") } }
        )
    }
}
