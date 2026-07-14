@file:OptIn(ExperimentalMaterial3Api::class, androidx.camera.core.ExperimentalGetImage::class)

package com.securevault.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.security.ProfilePasswordHasher
import com.securevault.utils.AccessMode
import com.securevault.utils.CryptoUtils
import com.securevault.utils.PasswordAccessPolicy
import com.securevault.utils.SecureQrManager
import com.securevault.viewmodel.ProfileViewModel
import com.securevault.viewmodel.VaultViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    profileId: Int?,
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    val profiles by profileViewModel.profiles.collectAsState()
    val currentProfile = remember(profileId, profiles) { profiles.find { it.id == profileId } }

    var hasCameraPermission by remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var scannedResult by remember { mutableStateOf<SecureQrManager.QrValidationResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Состояния для управления доступом к паролю после сканирования
    var showPassword by remember { mutableStateOf(false) }
    var decryptedPassword by remember { mutableStateOf<String?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    // Запрос разрешения на камеру
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                hasCameraPermission = true
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasCameraPermission) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.NoPhotography, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Требуется разрешение на использование камеры", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Предоставить разрешение")
                    }
                }
            } else if (scannedResult == null && errorMessage == null) {
                // Настоящий CameraX сканер
                CameraPreview(
                    onQrCodeScanned = { rawValue ->
                        try {
                            val result = SecureQrManager.validateQrToken(rawValue, profileId, context)
                            if (result.isValid && result.entryId != null) {
                                val entry = viewModel.findEntryById(result.entryId)
                                val profile = profiles.find { it.id == entry?.profileId }
                                
                                if (entry != null && profile != null && entry.profileId == profileId) {
                                    scannedResult = SecureQrManager.QrValidationResult(true, entry.id, profile.id)
                                    // Сохраняем ссылки для отображения
                                    // (В реальном проекте лучше вернуть entry/profile из validateQrToken или найти их здесь)
                                } else {
                                    errorMessage = "Запись не найдена или принадлежит другому профилю"
                                }
                            } else {
                                errorMessage = "Недействительный или просроченный QR-код"
                            }
                        } catch (e: Exception) {
                            errorMessage = "Ошибка проверки QR: ${e.message}"
                        }
                    },
                    lifecycleOwner = lifecycleOwner,
                    cameraExecutor = cameraExecutor
                )
                
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    ) {
                        Text(
                            "Наведите камеру на QR-код",
                            modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (errorMessage != null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Error, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text("Ошибка сканирования", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { 
                        scannedResult = null
                        errorMessage = null 
                    }) {
                        Text("Попробовать снова")
                    }
                }
            } else if (scannedResult != null && currentProfile != null) {
                //  Карточка результата с применением политики доступа
                val entry = viewModel.findEntryById(scannedResult!!.entryId)
                
                if (entry != null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
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
                                                IconButton(onClick = { requestAccess(entry, currentProfile) }) {
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
    }

    //  Диалог ввода PIN для QR
    if (showPinDialog && scannedResult != null && currentProfile != null) {
        val entry = viewModel.findEntryById(scannedResult!!.entryId)
        if (entry != null) {
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
                        if (ProfilePasswordHasher.verify(pinInput, currentProfile.passwordHash, currentProfile.passwordSalt)) {
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

// ✅ ИСПРАВЛЕНИЕ 2: Реальная реализация CameraX + MLKit
@androidx.camera.core.ExperimentalGetImage
@Composable
private fun CameraPreview(
    onQrCodeScanned: (String) -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraExecutor: ExecutorService
) {
    val context = LocalContext.current
    
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                val scanner = BarcodeScanning.getClient()
                                
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            val rawValue = barcode.rawValue
                                            if (!rawValue.isNullOrBlank()) {
                                                onQrCodeScanned(rawValue)
                                                break // Обрабатываем только первый найденный QR
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }
                
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
