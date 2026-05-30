package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onBiometricRequest: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🔐 SecureVault",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Мастер-пароль") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (password.length >= 4) {
                    // TODO: Проверка хеша мастер-пароля
                    onUnlocked()
                } else {
                    error = "Пароль слишком короткий"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Разблокировать")
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = onBiometricRequest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🔓 Войти по отпечатку")
        }
    }
}
