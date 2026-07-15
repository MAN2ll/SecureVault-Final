@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.data.Entry
import com.securevault.data.PasswordHistoryItem
import com.securevault.data.Profile
import com.securevault.security.ProfilePasswordHasher
import com.securevault.utils.AccessResult
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordAccessPolicy
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationJournalScreen(
    profileId: Int?,
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    LaunchedEffect(profileId) {
        if (profileId != null) {
            viewModel.setCurrentProfile(profileId)
        }
    }

    val entries by viewModel.entries.collectAsState()
    val profiles by profileViewModel.profiles.collectAsState()
    val currentProfile = remember(profileId, profiles) { profiles.find { it.id == profileId } }

    var historyItemToShow by remember { mutableStateOf<Pair<Entry, PasswordHistoryItem>?>(null) }
    var showPinDialogForHistory by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var revealedHistoryPassword by remember { mutableStateOf<String?>(null) }
    
    //  Диалог для случая, когда PIN не задан
    var showPinNotSetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал ротации", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text("История изменений пуста", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(entries) { entry ->
                    val history = entry.getPasswordHistory()
                    if (history.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.service, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(entry.username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                                
                                history.forEach { item ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = formatDate(item.date), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            Text(text = "Тип: ${item.type}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (!item.relatedService.isNullOrBlank()) {
                                                Text(text = "Источник: ${item.relatedService}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        
                                        IconButton(
                                            onClick = {
                                                historyItemToShow = entry to item
                                                revealedHistoryPassword = null
                                                
                                                requestHistoryAccess(
                                                    entry = entry,
                                                    profile = currentProfile,
                                                    context = context,
                                                    activity = activity,
                                                    onAccessGranted = {
                                                        revealedHistoryPassword = try {
                                                            item.encryptedOldPassword?.let { CryptoUtils.decrypt(it) } ?: "Недоступно"
                                                        } catch (e: Exception) {
                                                            "Ошибка расшифровки"
                                                        }
                                                    },
                                                    onPinRequired = {
                                                        showPinDialogForHistory = true
                                                    },
                                                    onPinNotSet = {
                                                        showPinNotSetDialog = true
                                                    }
                                                )
                                            }
                                        ) {
                                            Icon(Icons.Default.Visibility, "Просмотреть старый пароль", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPinDialogForHistory && historyItemToShow != null && currentProfile != null) {
        AlertDialog(
            onDismissRequest = { showPinDialogForHistory = false },
            title = { Text("Введите PIN профиля") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it; pinError = null },
                        label = { Text("PIN профиля") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = pinError != null
                    )
                    if (pinError != null) Text(pinError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (ProfilePasswordHasher.verify(pinInput, currentProfile.passwordHash, currentProfile.passwordSalt)) {
                        val item = historyItemToShow!!.second
                        revealedHistoryPassword = try {
                            item.encryptedOldPassword?.let { CryptoUtils.decrypt(it) } ?: "Недоступно"
                        } catch (e: Exception) {
                            "Ошибка расшифровки"
                        }
                        showPinDialogForHistory = false
                        pinInput = ""
                    } else {
                        pinError = "Неверный PIN профиля"
                    }
                }) { Text("Подтвердить") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialogForHistory = false; pinInput = "" }) { Text("Отмена") }
            }
        )
    }

    if (revealedHistoryPassword != null && historyItemToShow != null) {
        AlertDialog(
            onDismissRequest = { revealedHistoryPassword = null },
            title = { Text("Старый пароль") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Сервис: ${historyItemToShow!!.first.service}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = revealedHistoryPassword!!, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                context.getSystemService(android.content.ClipboardManager::class.java)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("old_password", revealedHistoryPassword))
                                android.widget.Toast.makeText(context, "Скопировано", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, "Копировать")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { revealedHistoryPassword = null }) { Text("Закрыть") }
            }
        )
    }
    
    //  Диалог предупреждения об отсутствии PIN
    if (showPinNotSetDialog) {
        AlertDialog(
            onDismissRequest = { showPinNotSetDialog = false },
            title = { Text("PIN профиля не задан") },
            text = { Text("Для этого действия нужно сначала задать PIN профиля в настройках.") },
            confirmButton = {
                TextButton(onClick = { showPinNotSetDialog = false }) { Text("Понятно") }
            }
        )
    }
}

private fun requestHistoryAccess(
    entry: Entry,
    profile: Profile?,
    context: Context,
    activity: FragmentActivity?,
    onAccessGranted: () -> Unit,
    onPinRequired: () -> Unit,
    onPinNotSet: () -> Unit // 
) {
    if (profile == null) {
        onPinRequired()
        return
    }
    
    val accessMode = PasswordAccessPolicy.resolve(entry, profile)
    
    when (accessMode) {
        is AccessResult.Granted -> onAccessGranted()
        is AccessResult.PinRequired -> onPinRequired()
        is AccessResult.BiometricOrPin -> {
            val biometricManager = BiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            
            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS && activity != null) {
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onAccessGranted()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (profile.passwordHash.isNullOrBlank()) onPinNotSet() else onPinRequired()
                    }
                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        if (profile.passwordHash.isNullOrBlank()) onPinNotSet() else onPinRequired()
                    }
                })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Подтвердите личность")
                    .setSubtitle("Для просмотра старого пароля")
                    .setNegativeButtonText("Использовать PIN")
                    .build()

                biometricPrompt.authenticate(promptInfo)
            } else {
                if (profile.passwordHash.isNullOrBlank()) onPinNotSet() else onPinRequired()
            }
        }
        is AccessResult.PinNotSet -> onPinNotSet()
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}
