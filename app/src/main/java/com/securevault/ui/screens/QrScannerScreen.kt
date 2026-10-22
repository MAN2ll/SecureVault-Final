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
import com.securevault.ui.components.LockActionButton
import com.securevault.ui.components.ProfileAccessDialog
import com.securevault.utils.*
import com.securevault.viewmodel.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

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
    var entry by remember { mutableStateOf<Entry?>(null) }
    var password by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var access by remember { mutableStateOf(false) }
    var biometric by remember { mutableStateOf(false) }
    val profile = profiles.find { it.id == entry?.profileId }
    fun reveal() {
        try { password = requireNotNull(entry).password }
        catch (e: Exception) { error = "Ошибка расшифровки: ${e.message}" }
    }
    LaunchedEffect(authViewModel) {
        authViewModel.clearSensitiveEvent.collect { password = null; entry = null; access = false }
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
                error != null -> Column(Modifier.padding(24.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { error = null; entry = null; password = null }) { Text("Попробовать снова") }
                }
                entry == null && !loading -> CameraPreview(onResult = { raw ->
                    if (!loading) {
                        loading = true
                        scope.launch {
                            try {
                                val owner = requireNotNull(profileId) { "Профиль не выбран" }
                                val result = SecureQrManager.validateQrToken(raw, owner, context)
                                require(result.isValid) { "Недействительный QR-код" }
                                val found = viewModel.getEntryById(requireNotNull(result.entryId))
                                require(found != null && found.profileId == owner) { "Запись QR-кода не найдена в профиле" }
                                entry = found
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) { error = e.message ?: "Ошибка QR"
                            } finally { loading = false }
                        }
                    }
                }, onError = { error = it })
                loading -> CircularProgressIndicator()
                else -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(entry!!.service); Text(entry!!.username)
                    Text(password ?: "••••••••••••", fontFamily = FontFamily.Monospace)
                    Button(onClick = {
                        val owner = profile
                        if (owner == null) error = "Профиль записи не найден"
                        else when (val policy = resolveAccess(entry!!, owner)) {
                            AccessResult.Granted -> reveal()
                            AccessResult.PinNotSet -> error = "Сначала задайте PIN профиля"
                            else -> { biometric = policy is AccessResult.BiometricOrPin; access = true }
                        }
                    }) { Text("Показать пароль") }
                    password?.let { pwd ->
                        OutlinedButton(onClick = { ClipboardHelper.copyWithAutoClear(context, pwd, "qr_password") }) { Text("Копировать на 30 секунд") }
                        OutlinedButton(onClick = { password = null }) { Text("Скрыть") }
                    }
                    TextButton(onClick = { password = null; entry = null }) { Text("Сканировать другой QR") }
                }
            }
        }
    }
    if (access && profile != null) ProfileAccessDialog(profile, biometric,
        onDismiss = { access = false }, onGranted = { access = false; reveal() })
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
