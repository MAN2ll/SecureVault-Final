@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.securevault.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.*
import com.securevault.ui.components.LockActionButton
import com.securevault.security.ScreenPrivacyController
import com.securevault.utils.RotationNotificationWorker
import com.securevault.viewmodel.AuthViewModel
import com.securevault.viewmodel.VaultViewModel
import java.util.concurrent.TimeUnit

@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigateToExport: () -> Unit = {},
    onNavigateToChangePassword: () -> Unit = {}, onLock: () -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel(), authViewModel: AuthViewModel) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val prefs = remember { context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE) }
    var allowScreenshots by remember { mutableStateOf(prefs.getBoolean(ScreenPrivacyController.ALLOW_SCREENSHOTS, false)) }
    var biometric by remember { mutableStateOf(authViewModel.isBiometricLoginEnabled()) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications_rotation_enabled", false)) }
    var timeout by remember { mutableIntStateOf(prefs.getInt("auto_lock_timeout_minutes", 5)) }
    var error by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf<BiometricPrompt?>(null) }
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { active = false; prompt?.cancelAuthentication() } }
    fun setNotifications(enabled: Boolean) {
        notifications = enabled
        prefs.edit().putBoolean("notifications_rotation_enabled", enabled).apply()
        val manager = WorkManager.getInstance(context)
        if (enabled) manager.enqueueUniquePeriodicWork("securevault_rotation_notifications",
            ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<RotationNotificationWorker>(1, TimeUnit.DAYS).build())
        else manager.cancelUniqueWork("securevault_rotation_notifications")
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        setNotifications(granted)
        if (!granted) error = "Разрешение на уведомления не предоставлено"
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
        actions = { LockActionButton(onLock) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Вход по биометрии", Modifier.weight(1f))
                Switch(biometric, { enabled ->
                    if (!enabled) { biometric = false; authViewModel.setBiometricLoginEnabled(false) }
                    else if (activity == null || BiometricManager.from(context).canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
                        error = "Сильная биометрия недоступна. Добавьте её в настройках Android."
                    } else {
                        prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    if (active) { biometric = true; authViewModel.setBiometricLoginEnabled(true) }
                                }
                            }).also { it.authenticate(BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Подтвердите включение биометрии")
                                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                .setNegativeButtonText("Отмена").build()) }
                    }
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Разрешить скриншоты паролей", Modifier.weight(1f))
                Switch(allowScreenshots, { enabled ->
                    allowScreenshots = enabled
                    prefs.edit().putBoolean(ScreenPrivacyController.ALLOW_SCREENSHOTS, enabled).apply()
                })
            }
            Text("В списке последних приложений пароли скрываются при любом положении переключателя. При выключении экрана открытый пароль закрывается.")
            Text("Автоблокировка")
            listOf(0 to "Сразу при сворачивании", 1 to "Через 1 минуту", 5 to "Через 5 минут",
                15 to "Через 15 минут", 30 to "Через 30 минут").forEach { (value, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(timeout == value, {
                        timeout = value; prefs.edit().putInt("auto_lock_timeout_minutes", value).apply()
                    }); Text(label)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Напоминания о сроках смены", Modifier.weight(1f))
                Switch(notifications, { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else setNotifications(enabled)
                })
            }
            Text("Фоновая проверка просроченных паролей раз в день; время зависит от Android.")
            Button(onClick = onNavigateToChangePassword, modifier = Modifier.fillMaxWidth()) { Text("Изменить мастер-пароль") }
            OutlinedButton(onClick = onNavigateToExport, modifier = Modifier.fillMaxWidth()) { Text("Экспорт / Импорт") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
