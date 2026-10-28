@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.securevault.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.securevault.data.Entry
import com.securevault.security.SensitiveContent
import com.securevault.ui.components.LockActionButton
import com.securevault.ui.components.ProfileAccessDialog
import com.securevault.utils.*
import com.securevault.viewmodel.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private enum class QrSecretAction { REVEAL, COPY }

@Composable
fun QrScannerScreen(profileId: Int?, onBack: () -> Unit, onLock: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(), profileViewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles by profileViewModel.profiles.collectAsState()
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,
        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    var entry by remember(profileId) { mutableStateOf<Entry?>(null) }
    var password by remember(profileId) { mutableStateOf<String?>(null) }
    var qrPhrase by remember(profileId) { mutableStateOf<String?>(null) }
    var error by remember(profileId) { mutableStateOf<String?>(null) }
    var loading by remember(profileId) { mutableStateOf(false) }
    var access by remember(profileId) { mutableStateOf(false) }
    var pending by remember(profileId) { mutableStateOf<QrSecretAction?>(null) }
    val profile = profiles.find { it.id == entry?.profileId }

    fun reset() {
        password = null; entry = null; qrPhrase = null; access = false; pending = null; error = null
    }
    fun requestAccess(action: QrSecretAction) {
        password = null; error = null; pending = null
        if (authViewModel.authState.value !is AuthViewModel.AuthState.Unlocked) return
        val owner = profile ?: run { error = "Папка записи не найдена"; return }
        val item = entry ?: return
        when (resolvePasswordRevealAccess(item, owner)) {
            AccessResult.PinRequired -> { pending = action; access = true }
            AccessResult.PinNotSet -> error = "Сначала задайте PIN папки в настройках профиля"
            else -> error = "Для доступа требуется PIN папки"
        }
    }
    fun finishAccess() {
        val action = pending
        pending = null; access = false
        if (action == null || authViewModel.authState.value !is AuthViewModel.AuthState.Unlocked) return
        val item = entry ?: return
        val owner = profile ?: return
        if (resolvePasswordRevealAccess(item, owner) !is AccessResult.PinRequired) return
        try {
            val value = CryptoUtils.decrypt(item.encryptedPassword)
            when (action) {
                QrSecretAction.REVEAL -> password = value
                QrSecretAction.COPY -> ClipboardHelper.copyWithAutoClear(context, value, "qr_password")
            }
        } catch (_: Exception) { error = "Не удалось расшифровать пароль" }
    }
    LaunchedEffect(authViewModel) {
        authViewModel.clearSensitiveEvent.collect { reset() }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Сканирование QR") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
        actions = { LockActionButton(onLock) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !permitted -> Column(Modifier.padding(24.dp)) {
                    Text("Для сканирования нужна камера")
                    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Разрешить камеру") }
                }
                entry == null && error != null -> Column(Modifier.padding(24.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { reset() }) { Text("Попробовать снова") }
                }
                entry == null && !loading -> CameraPreview(onResult = { raw ->
                    if (!loading) {
                        loading = true
                        scope.launch {
                            try {
                                val owner = requireNotNull(profileId) { "Папка не выбрана" }
                                val result = SecureQrManager.validateQrToken(raw, owner, context)
                                require(result.isValid) { "Недействительный QR-код" }
                                val found = viewModel.getEntryById(requireNotNull(result.entryId))
                                require(found != null && found.profileId == owner) { "Запись QR-кода не найдена в папке" }
                                if (authViewModel.authState.value is AuthViewModel.AuthState.Unlocked) {
                                    entry = found; qrPhrase = result.mnemonicPhrase
                                }
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) { error = e.message ?: "Ошибка QR"
                            } finally { loading = false }
                        }
                    }
                }, onError = { error = it })
                loading -> CircularProgressIndicator()
                else -> {
                    SensitiveContent()
                    val item = entry ?: return@Box
                    val hint = MnemonicHint.forDisplay(item.mnemonicPhraseHint)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(item.service, style = MaterialTheme.typography.titleMedium)
                        Text(item.username)
                        hint?.let {
                            Text("Мнемоническая подсказка", style = MaterialTheme.typography.labelMedium)
                            Text(it)
                        }
                        if (!qrPhrase.isNullOrBlank() && qrPhrase != hint) {
                            Text("Фраза в QR на момент его создания", style = MaterialTheme.typography.labelMedium)
                            Text(qrPhrase.orEmpty())
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(password ?: "••••••••••••", fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                if (password != null) password = null else requestAccess(QrSecretAction.REVEAL)
                            }) {
                                Icon(if (password != null) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    if (password != null) "Скрыть пароль" else "Показать после PIN папки")
                            }
                        }
                        OutlinedButton(onClick = { requestAccess(QrSecretAction.COPY) }) {
                            Text("Копировать после PIN папки")
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = { reset() }) { Text("Сканировать другой QR") }
                    }
                }
            }
        }
    }
    if (access && profile != null) ProfileAccessDialog(profile, allowBiometric = false,
        onDismiss = { access = false; pending = null }, onGranted = { finishAccess() })
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onResult: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val result by rememberUpdatedState(onResult)
    val failure by rememberUpdatedState(onError)
    val view = remember { PreviewView(context) }
    DisposableEffect(owner, view) {
        var active = true
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var provider: ProcessCameraProvider? = null
        analysis.setAnalyzer(executor) { frame ->
            val media = frame.image
            if (media == null || !active) frame.close()
            else try {
                scanner.process(InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees))
                    .addOnSuccessListener(main) { codes -> if (active) codes.firstNotNullOfOrNull { it.rawValue }?.let(result) }
                    .addOnFailureListener(main) { if (active) failure("Не удалось распознать QR-код") }
                    .addOnCompleteListener(main) { frame.close() }
            } catch (_: Exception) { frame.close() }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (active) try {
                provider = future.get()
                provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) { failure("Не удалось запустить камеру") }
        }, main)
        onDispose {
            active = false; analysis.clearAnalyzer(); provider?.unbind(preview, analysis)
            scanner.close(); executor.shutdown()
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}
