@file:OptIn(ExperimentalMaterial3Api::class)
package com.securevault.ui.screens
import androidx.compose.ui.unit.sp // ✅ Для .sp
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.utils.MnemonicPasswordGenerator
import com.securevault.viewmodel.VaultViewModel

@Composable
fun MnemonicGeneratorScreen(onBack: () -> Unit, viewModel: VaultViewModel = hiltViewModel()) {
    var phrase by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf<Profile?>(null) }
    var result by remember { mutableStateOf<MnemonicPasswordGenerator.GenerationResult?>(null) }
    val gen = remember { MnemonicPasswordGenerator() }

    LaunchedEffect(phrase) {
        if (phrase.isNotBlank() && service.isNotBlank()) {
            result = gen.generate(MnemonicPasswordGenerator.GenerationOptions(phrase = phrase, targetLength = 12, includeUppercase = true, includeDigits = true))
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Мнемонический") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(service, { service = it }, label = { Text("Сервис") }, singleLine = true)
            OutlinedTextField(username, { username = it }, label = { Text("Логин") }, singleLine = true)
            OutlinedTextField(phrase, { phrase = it }, label = { Text("Фраза-подсказка") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Profile.entries.forEach { p -> FilterChip(selected = selectedProfile == p, onClick = { selectedProfile = p }, label = { Text(p.label) }) }
            }
            result?.let { res ->
                Card { Column(Modifier.padding(12.dp)) { Text(res.password, fontSize = 18.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace); Text("💡 ${res.mnemonicHint}") } }
            }
            Button(onClick = {
                if (service.isBlank() || username.isBlank() || selectedProfile == null || result == null) return@Button
                viewModel.insert(Entry(service = service, username = username, encryptedPassword = com.securevault.utils.CryptoUtils.encrypt(result!!.password), profile = selectedProfile!!, emojiHint = result!!.mnemonicHint))
                onBack()
            }, enabled = service.isNotBlank() && selectedProfile != null && result != null) { Text("Сохранить") }
        }
    }
}
