@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.utils.PasswordGenerator
import com.securevault.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    var service by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    val currentProfileId by viewModel.currentProfileId.collectAsState()
    
    var length by remember { mutableIntStateOf(12) }
    var useUppercase by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSpecial by remember { mutableStateOf(false) }
    
    var generatedPassword by remember { mutableStateOf("") }
    var passwordStrength by remember { mutableStateOf(PasswordGenerator.Strength.MEDIUM) }
    
    var showCopiedHint by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun generatePassword() {
        if (service.isBlank()) {
            showError = "Введите название сервиса"
            return
        }
        if (currentProfileId == null) {
            showError = "Профиль не выбран"
            return
        }
        
        val result = PasswordGenerator.generate(
            length = length,
            useUpper = useUppercase,
            useDigits = useDigits,
            useSpecial = useSpecial
        )
        generatedPassword = result.password
        passwordStrength = result.strength
        showError = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Генератор паролей", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = service,
                onValueChange = { service = it },
                label = { Text("Название сервиса") },
                placeholder = { Text("например, Google") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = showError != null && service.isBlank()
            )
            
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Логин / Email") },
                placeholder = { Text("user@example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Настройки пароля", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Длина: $length", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Slider(
                            value = length.toFloat(),
                            onValueChange = { length = it.toInt() },
                            valueRange = 8f..20f,
                            steps = 12,
                            modifier = Modifier.weight(2f)
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = useUppercase, onCheckedChange = { useUppercase = it })
                        Text("Заглавные буквы (A-Z)", modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = useDigits, onCheckedChange = { useDigits = it })
                        Text("Цифры (0-9)", modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = useSpecial, onCheckedChange = { useSpecial = it })
                        Text("Спецсимволы (!@#$)", modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
                    }
                }
            }
            
            Button(
                onClick = { generatePassword() },
                modifier = Modifier.fillMaxWidth(),
                enabled = service.isNotBlank() && currentProfileId != null
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Сгенерировать пароль")
            }
            
            if (generatedPassword.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = generatedPassword,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("password", generatedPassword))
                                showCopiedHint = true
                                scope.launch {
                                    kotlinx.coroutines.delay(2000)
                                    showCopiedHint = false
                                }
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Надёжность:", fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
                            val strengthColor = when (passwordStrength) {
                                PasswordGenerator.Strength.WEAK -> MaterialTheme.colorScheme.error
                                PasswordGenerator.Strength.MEDIUM -> MaterialTheme.colorScheme.tertiary
                                PasswordGenerator.Strength.STRONG -> MaterialTheme.colorScheme.primary
                                PasswordGenerator.Strength.VERY_STRONG -> Color(0xFF2E7D32)
                            }
                            Box(
                                modifier = Modifier.weight(1f).height(6.dp).fillMaxWidth().padding(end = 8.dp)
                            ) {
                                val fillFraction = when (passwordStrength) {
                                    PasswordGenerator.Strength.WEAK -> 0.25f
                                    PasswordGenerator.Strength.MEDIUM -> 0.5f
                                    PasswordGenerator.Strength.STRONG -> 0.75f
                                    PasswordGenerator.Strength.VERY_STRONG -> 1f
                                }
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(vertical = 1.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(fillFraction).height(4.dp).background(color = strengthColor)
                                    )
                                }
                            }
                            Text(
                                text = passwordStrength.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = strengthColor
                            )
                        }
                    }
                }
                
                if (showCopiedHint) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.small)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Скопировано в буфер", color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 14.sp)
                    }
                }
            }
            
            Spacer(Modifier.weight(1f))
            
            Button(
                onClick = {
                    if (generatedPassword.isEmpty()) {
                        showError = "Сначала сгенерируйте пароль"
                        return@Button
                    }
                    if (service.isBlank() || currentProfileId == null) {
                        showError = "Заполните все поля"
                        return@Button
                    }
                    
                    val newEntry = Entry.create(
                        service = service,
                        username = username.ifBlank { "user" },
                        password = generatedPassword,
                        profileId = currentProfileId!!
                    )
                    
                    viewModel.insert(newEntry)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = generatedPassword.isNotEmpty() && service.isNotBlank() && currentProfileId != null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Сохранить в хранилище")
            }
            
            if (showError != null && generatedPassword.isEmpty()) {
                Text(text = showError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}
