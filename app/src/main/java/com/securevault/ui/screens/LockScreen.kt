@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.withResumed
import com.securevault.data.Profile
import com.securevault.security.privacyHost
import com.securevault.viewmodel.AuthViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: context.privacyHost() as? FragmentActivity
    val authState by viewModel.authState.collectAsState()
    val lock = authState as? AuthViewModel.AuthState.Locked
    val profilePinRequired = lock?.profileId != null
    val biometricAllowed = lock != null && !profilePinRequired && viewModel.isBiometricLoginEnabled() &&
        !viewModel.isMasterPasswordRequired()
    val scope = rememberCoroutineScope()
    var password by remember(lock) { mutableStateOf("") }
    var error by remember(lock) { mutableStateOf<String?>(null) }
    var profile by remember(lock) { mutableStateOf<Profile?>(null) }
    var checking by remember(lock) { mutableStateOf(false) }
    var activePrompt by remember(activity, lock) { mutableStateOf<BiometricPrompt?>(null) }
    var authenticating by remember(activity, lock) { mutableStateOf(false) }

    LaunchedEffect(lock) {
        if (lock != null && profilePinRequired) {
            profile = try {
                viewModel.profileForUnlock(lock)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (profile == null) viewModel.showMasterUnlock(lock)
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthViewModel.AuthState.BruteForceLocked) {
            delay(1000); viewModel.init()
        }
    }
    DisposableEffect(activity, lock) {
        onDispose {
            authenticating = false
            activePrompt?.cancelAuthentication()
            activePrompt = null
            password = ""
        }
    }

    fun authenticate() {
        val owner = activity ?: return
        val expected = lock ?: return
        if (!biometricAllowed || authenticating || checking || viewModel.authState.value !== expected ||
            !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || owner.supportFragmentManager.isStateSaved) return

        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(owner).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            error = "Биометрия недоступна. Введите мастер-пароль."
            return
        }
        val prompt = activePrompt ?: BiometricPrompt(owner, ContextCompat.getMainExecutor(owner),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!authenticating) return
                    scope.launch {
                        owner.lifecycle.withResumed {
                            if (authenticating && viewModel.authState.value === expected) {
                                authenticating = false
                                if (!viewModel.unlockWithBiometric(expected)) {
                                    error = "Для безопасности введите мастер-пароль"
                                }
                            }
                        }
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!authenticating) return
                    authenticating = false
                    if (viewModel.authState.value === expected && errorCode !in setOf(
                            BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON)) error = errString.toString()
                }
            }).also { activePrompt = it }
        error = null
        authenticating = true
        try {
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle("Разблокировать SecureVault")
                .setAllowedAuthenticators(authenticators)
                .setSubtitle("Используйте отпечаток пальца или лицо")
                .setNegativeButtonText("Использовать мастер-пароль")
                .build())
        } catch (_: IllegalStateException) {
            authenticating = false
            error = "Не удалось открыть биометрию. Повторите попытку или введите мастер-пароль."
        }
    }

    LaunchedEffect(activity, lock, biometricAllowed) {
        if (activity != null && biometricAllowed) activity.lifecycle.withResumed { authenticate() }
    }

    Column(
        modifier = modifier
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        
        Text("SecureVault", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        
        if (profilePinRequired) {
            Text(profile?.let { "Введите PIN профиля «${it.name}»" } ?: "Проверяем профиль…",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        } else if (viewModel.isMasterPasswordRequired()) {
            Text(
                "Для безопасности введите мастер-пароль",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(16.dp))
        } else {
            Text("Введите мастер-пароль", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }

        if (authState is AuthViewModel.AuthState.BruteForceLocked) {
            Text("Повторите через ${((authState as AuthViewModel.AuthState.BruteForceLocked).remainingMillis + 999) / 1000} сек.")
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = if (profilePinRequired) it.filter(Char::isDigit).take(8) else it; error = null },
            label = { Text(if (profilePinRequired) "PIN профиля" else "Мастер-пароль") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = if (profilePinRequired) KeyboardType.NumberPassword else KeyboardType.Password),
            enabled = lock != null && !checking && !authenticating,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = error != null
        )
        
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (lock != null && viewModel.authState.value === lock && !checking && !authenticating) {
                    val entered = password
                    password = ""
                    checking = true
                    scope.launch {
                        try {
                            val unlocked = if (profilePinRequired) viewModel.attemptProfileUnlock(lock, entered)
                                else viewModel.attemptUnlock(entered, lock)
                            if (!unlocked && viewModel.authState.value === lock) {
                                error = if (profilePinRequired) "Неверный PIN" else "Неверный мастер-пароль"
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            if (viewModel.authState.value === lock) error = "Не удалось проверить данные. Повторите попытку."
                        } finally { checking = false }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = lock != null && !checking && !authenticating &&
                (if (profilePinRequired) profile != null && password.length >= 4 else password.isNotEmpty())
        ) {
            Text("Разблокировать", fontSize = 16.sp)
        }
        
        if (profilePinRequired && lock != null) {
            TextButton(onClick = { viewModel.showMasterUnlock(lock) }, enabled = !checking) {
                Text("К общему входу")
            }
        } else if (biometricAllowed) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { authenticate() }, enabled = !authenticating && !checking && activity != null) {
                Icon(Icons.Default.Fingerprint, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Войти по отпечатку")
            }
        }
    }
}
