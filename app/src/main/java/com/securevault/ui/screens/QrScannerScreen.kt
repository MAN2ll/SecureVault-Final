@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
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
import com.securevault.data.Profile
import com.securevault.security.ProfilePasswordHasher
import com.securevault.utils.AccessMode
import com.securevault.utils.PasswordAccessPolicy
import com.securevault.utils.SecureQrManager
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val profiles by profileViewModel.profiles.collectAsState()
    
    // Состояние для хранения результата сканирования
    var scannedResult by remember { mutableStateOf<SecureQrManager.QrValidationResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Состояния для управления доступом к паролю после сканирования
    var showPassword by remember { mutableStateOf(false) }
    var decryptedPassword by remember { mutableStateOf<String?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    // Функция запроса доступа на основе политики
    fun requestAccess(entry: Entry, profile: Profile) {
        val accessMode = PasswordAccessPolicy.resolve(entry, profile)
        
        when (accessMode) {
            AccessMode.NO_CONFIRMATION -> {
                showPassword = true
                decryptedPassword = entry.password
            }
            AccessMode.PIN_REQUIRED, AccessMode.PIN_ALWAYS -> {
                showPinDialog = true
            }
            AccessMode.BIOMETRIC_OR_PIN -> {
                val biometricManager = BiometricManager.from(context)
                val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                
                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS && activity != null) {
                    val executor = ContextCompat.getMainExecutor(context)
                    val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            showPassword = true
                            decryptedPassword = entry.password
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            showPinDialog = true
                        }
                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            showPinDialog = true
                        }
                    })

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Подтвердите личность")
                        .setSubtitle("Для просмотра пароля из QR-кода")
                        .setNegativeButtonText("Использовать PIN")
                        .build()

                    biometricPrompt.authenticate(promptInfo)
                } else {
                    showPinDialog = true
                }
            }
            else -> {
                showPinDialog = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сканирование QR", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (scannedResult == null && errorMessage == null) {
                // Здесь должен быть ваш компонент камеры для сканирования.
                // Для примера оставим заглушку или вызов реального сканера, если он у вас вынесен.
                // Предполагается, что вы используете что-то вроде QrCodeAnalyzer.
                // Если у вас есть готовый Composable для камеры, вставьте его сюда.
                Text("Камера сканирования QR-кода", color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Пример вызова (замените на ваш реальный компонент сканера):
                // QrCameraPreview(onQrCodeScanned = { qrData -> ... })
            } else if (errorMessage != null) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Ошибка сканирования", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(8.dp))
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { 
                            scannedResult = null
                            errorMessage = null 
                        }) {
                            Text("Попробовать снова")
                        }
                    }
                }
            } else if (scannedResult != null) {
                val result = scannedResult!!
                val entry = result.entry
                val profile = profiles.find { it.id == entry.profileId }

                if (profile != null) {
                    // Карточка результата QR с применением политики доступа
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("QR-код успешно проверен", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Устройство и профиль совпадают", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            
                            HorizontalDivider()

                            InfoRow("Сервис", entry.service)
                            InfoRow("Логин", entry.username)
                            if (!entry.textHint.isNullOrBlank()) {
                                InfoRow("Подсказка", entry.textHint)
                            }
                            if (!entry.mnemonicPhraseHint.isNullOrBlank()) {
                                InfoRow("Мнемоника", entry.mnemonicPhraseHint)
                            }

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Пароль", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (showPassword && decryptedPassword != null) decryptedPassword!! else "••••••••••••",
                                            fontSize = 14.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (!showPassword) {
                                            IconButton(onClick = { requestAccess(entry, profile) }) {
                                                Icon(Icons.Default.Visibility, "Показать пароль", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        } else {
                                            IconButton(onClick = {
                                                context.getSystemService(android.content.ClipboardManager::class.java)
                                                    .setPrimaryClip(android.content.ClipData.newPlainText("qr_password", decryptedPassword))
                                                android.widget.Toast.makeText(context, "Скопировано", android.widget.Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Default.ContentCopy, "Копировать пароль", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { 
                                    scannedResult = null
                                    showPassword = false
                                    decryptedPassword = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Сканировать другой QR")
                            }
                        }
                    }
                }
            }
        }
    }

    //  Диалог ввода PIN для QR
    if (showPinDialog && scannedResult != null) {
        val entry = scannedResult!!.entry
        val profile = profiles.find { it.id == entry.profileId }
        
        if (profile != null) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                title = { Text("Введите PIN профиля") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it; pinError = null },
                            label = { Text("PIN") },
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
                        if (ProfilePasswordHasher.verify(pinInput, profile.passwordHash, profile.passwordSalt)) {
                            showPassword = true
                            decryptedPassword = entry.password
                            showPinDialog = false
                            pinInput = ""
                        } else {
                            pinError = "Неверный PIN профиля"
                        }
                    }) { Text("Подтвердить") }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false; pinInput = "" }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
