@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import com.securevault.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onSetupRequired: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val authState by viewModel.authState.collectAsState()
    val failedAttempts by viewModel.failedAttempts.collectAsState()
    val wipeTriggered by viewModel.wipeTriggered.collectAsState()

    LaunchedEffect(authState) {
        when (authState) {
            is AuthViewModel.AuthState.Success -> onUnlocked()
            is AuthViewModel.AuthState.SetupRequired -> onSetupRequired()
            is AuthViewModel.AuthState.WipeTriggered -> onSetupRequired()
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SecureVault", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Введите мастер-пароль",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { 
                    password = it
                    error = null
                },
                label = { Text("Мастер-пароль") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = error != null
            )
            
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            when (val state = authState) {
                is AuthViewModel.AuthState.Blocked -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (state.isWipe) {
                        Text(
                            text = "Превышено количество попыток! Данные будут удалены.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        LaunchedEffect(Unit) {
                            viewModel.triggerWipe()
                        }
                    } else {
                        Text(
                            text = "Слишком много попыток. Подождите ${viewModel.formatLockoutTime(state.remainingMs)}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is AuthViewModel.AuthState.Failed -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Неверный пароль. Попыток: ${state.attempts}/10",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                else -> {}
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    if (password.isBlank()) {
                        error = "Введите пароль"
                        return@Button
                    }
                    
                    val success = viewModel.verifyPassword(password)
                    if (!success) {
                        error = "Неверный пароль"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = when (authState) {
                    is AuthViewModel.AuthState.Blocked -> false
                    else -> true
                }
            ) {
                Text("Разблокировать")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (failedAttempts >= 5) {
                TextButton(
                    onClick = {
                        viewModel.resetSecurity()
                        password = ""
                        error = null
                    }
                ) {
                    Text("Сбросить попытки")
                }
            }
        }
    }
}
