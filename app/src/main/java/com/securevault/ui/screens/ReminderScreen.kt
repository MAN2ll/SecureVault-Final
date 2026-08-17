@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.securevault.ui.components.LockActionButton
import com.securevault.viewmodel.PasswordOperationResult
import com.securevault.viewmodel.VaultViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(
    profileId: Int? = null, // Добавлено для совместимости с SecureVaultNavHost
    onBack: () -> Unit,
    onLock: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val rotationEntries by viewModel.rotationEntries.collectAsState()
    var entryToUpdate by remember { mutableStateOf<Entry?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Напоминания о ротации", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = { LockActionButton(onLock = onLock) }
            )
        },
        snackbarHost = {
            snackbarMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { snackbarMessage = null }) { Text("OK") } }
                ) { Text(msg) }
            }
        }
    ) { padding ->
        if (rotationEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Все пароли актуальны", fontWeight = FontWeight.Medium, fontSize = 18.sp)
                    Text("На данный момент нет просроченных паролей", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rotationEntries) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.service, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(entry.username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val nextRotation = entry.nextRotationDate
                                if (nextRotation != null) {
                                    Text("Просрочен с: ${dateFormat.format(Date(nextRotation))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Button(onClick = { entryToUpdate = entry }) {
                                Text("Обновить")
                            }
                        }
                    }
                }
            }
        }
    }

    if (entryToUpdate != null) {
        val entry = entryToUpdate!!

        PasswordRotationDialog(
            currentEntryId = entry.id,
            serviceName = entry.service,
            currentHint = entry.textHint,
            generationType = entry.generationType,
            rotationMonth = entry.rotationPeriodMonths,
            rotationYear = null,
            allProfileEntries = rotationEntries,
            onPasswordReplaced = { newPassword, newHint, newGenType, newMnemonicHint, newMnemonicOptions ->
                viewModel.replacePassword(
                    entry = entry,
                    newPassword = newPassword,
                    newHint = newHint,
                    newGenerationType = newGenType,
                    newMnemonicPhraseHint = newMnemonicHint,
                    newMnemonicOptionsJson = newMnemonicOptions
                ) { result ->
                    when (result) {
                        is PasswordOperationResult.Success -> {
                            snackbarMessage = "Пароль успешно обновлён"
                            entryToUpdate = null
                        }
                        is PasswordOperationResult.Error -> {
                            snackbarMessage = result.message
                        }
                    }
                }
            },
            onDismiss = { entryToUpdate = null }
        )
    }
}
