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
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel

@Composable
fun GeneratorScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var length by remember { mutableIntStateOf(12) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSpecial by remember { mutableStateOf(true) }
    var selectedProfile by remember { mutableStateOf<Profile?>(null) }
    
    var result by remember { mutableStateOf<PasswordGenerator.Result?>(null) }

    LaunchedEffect(length, useUpper, useDigits, useSpecial) {
        if (service.isNotBlank()) {
            result = PasswordGenerator.generate(length, useUpper, useDigits, useSpecial)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Новый пароль (Простой)") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(service, { service = it }, label = { Text("Сервис") }, singleLine = true)
            OutlinedTextField(username, { username = it }, label = { Text("Логин") }, singleLine = true)
            
            Text("Профиль:", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Profile.entries.forEach { p ->
                    FilterChip(selected = selectedProfile == p, onClick = { selectedProfile = p }, label = { Text(p.label) })
                }
            }

            Card {
                Column(Modifier.padding(12.dp)) {
                    Row(Alignment.CenterVertically) { Text("Длина: $length"); Slider(length.toFloat(), { length = it.toInt() }, 8f..20f, steps = 12, Modifier.weight(1f)) }
                    SwitchSetting("Заглавные", useUpper, { useUpper = it })
                    SwitchSetting("Цифры", useDigits, { useDigits = it })
                    SwitchSetting("Спецсимволы", useSpecial, { useSpecial = it })
                }
            }

            result?.let { res ->
                Card { Column(Modifier.padding(12.dp)) { Text(res.password, fontSize = 18.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace); Text("Надёжность: ${res.strength.name}", fontSize = 12.sp) } }
            }

            Button(onClick = {
                if (service.isBlank() || username.isBlank() || selectedProfile == null || result == null) return@Button
                viewModel.insert(Entry(service = service, username = username, encryptedPassword = com.securevault.utils.CryptoUtils.encrypt(result!!.password), profile = selectedProfile!!))
                onBack()
            }, enabled = service.isNotBlank() && username.isNotBlank() && selectedProfile != null && result != null) { Text("Сохранить") }
        }
    }
}

@Composable private fun SwitchSetting(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label); Switch(checked, onChange)
    }
}
