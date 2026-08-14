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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.ui.components.LockActionButton
import com.securevault.ui.components.UnifiedPasswordGeneratorDialog
import com.securevault.viewmodel.AuthViewModel
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicGeneratorScreen(
    profileId: Int?,
    onBack: () -> Unit,
    onLock: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    LaunchedEffect(profileId) {
        if (profileId != null) viewModel.setCurrentProfile(profileId)
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentProfileId by viewModel.currentProfileId.collectAsState()
    val effectiveProfileId = profileId ?: currentProfileId

    var showGeneratorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AMPG генератор", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = {
                    LockActionButton(onLock = onLock)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Мнемонический генератор паролей", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "AMPG создаёт запоминаемые пароли из фразы. " +
                        "Каждый пароль уникален и восстанавливаем из исходной фразы.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showGeneratorDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Casino, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Сгенерировать пароль", fontSize = 16.sp)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Как это работает", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    
                    InfoItem("1. Введите фразу", "Например: 'это был обычный август'")
                    InfoItem("2. Выберите режим", "Один пользователь или два пользователя")
                    InfoItem("3. Получите 3 варианта", "Все варианты валидны и объяснимы")
                    InfoItem("4. Выберите пароль", "Нажмите на вариант для копирования")
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text("Пример", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Фраза: это был обычный август\n" +
                        "Вариант 1: E70BylO4nAvgu\$#5\n" +
                        "Вариант 2: E70By!O4nAvgu\$#5\n" +
                        "Вариант 3: Et0By!O4nAvgu\$#5",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Безопасность", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "Пароли не содержат повторов символов. " +
                            "Каждый пароль проходит проверку сложности.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }

    if (showGeneratorDialog) {
        UnifiedPasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false },
            onGenerated = { pwd, hint, type ->
                clipboardManager.setText(AnnotatedString(pwd))
                showGeneratorDialog = false
            },
            initialServiceName = ""
        )
    }
}

@Composable
private fun InfoItem(title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.width(140.dp)
        )
        Text(
            text = description,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
