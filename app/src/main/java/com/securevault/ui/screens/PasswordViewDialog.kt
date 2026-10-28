@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.security.SensitiveContent
import com.securevault.ui.screens.QrCodeDialog
import com.securevault.utils.AccessResult
import com.securevault.utils.ClipboardHelper
import com.securevault.utils.CryptoUtils
import com.securevault.utils.MnemonicHint
import com.securevault.utils.resolvePasswordRevealAccess
import com.securevault.viewmodel.AuthViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private enum class PasswordCardAction { REVEAL, COPY }

@Composable
fun PasswordViewDialog(
    entry: Entry,
    profile: Profile,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    var decryptedPassword by remember(entry, profile) { mutableStateOf<String?>(null) }
    var errorMessage by remember(entry, profile) { mutableStateOf<String?>(null) }
    var showAccessDialog by remember(entry, profile) { mutableStateOf(false) }
    var pendingAction by remember(entry, profile) { mutableStateOf<PasswordCardAction?>(null) }
    var showQrDialog by remember(entry, profile) { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val cardHint = MnemonicHint.forDisplay(entry.mnemonicPhraseHint)

    fun clearSecrets() {
        decryptedPassword = null
        errorMessage = null
        showAccessDialog = false
        pendingAction = null
        showQrDialog = false
    }

    LaunchedEffect(authViewModel, entry, profile) {
        authViewModel.clearSensitiveEvent.collect { clearSecrets() }
    }

    fun requestAccess(action: PasswordCardAction) {
        // Even a second reveal or a copy is a new operation requiring the folder PIN.
        decryptedPassword = null
        errorMessage = null
        pendingAction = null
        if (authViewModel.authState.value !is AuthViewModel.AuthState.Unlocked) return
        when (resolvePasswordRevealAccess(entry, profile)) {
            AccessResult.PinRequired -> {
                pendingAction = action
                showAccessDialog = true
            }
            AccessResult.PinNotSet -> {
                errorMessage = "Сначала задайте PIN папки в настройках профиля."
            }
            else -> errorMessage = "Для доступа требуется PIN папки."
        }
    }

    fun finishAccess() {
        val action = pendingAction
        pendingAction = null
        showAccessDialog = false
        if (action == null || authViewModel.authState.value !is AuthViewModel.AuthState.Unlocked) return
        if (resolvePasswordRevealAccess(entry, profile) !is AccessResult.PinRequired) return
        try {
            val password = CryptoUtils.decrypt(entry.encryptedPassword)
            when (action) {
                PasswordCardAction.REVEAL -> decryptedPassword = password
                PasswordCardAction.COPY -> ClipboardHelper.copyWithAutoClear(context, password, "password")
            }
        } catch (_: Exception) {
            errorMessage = "Не удалось расшифровать пароль."
        }
    }

    val safeOnDismiss = { clearSecrets(); onDismiss() }
    AlertDialog(
        onDismissRequest = safeOnDismiss,
        title = { Text(entry.service, fontWeight = FontWeight.Bold) },
        text = {
            SensitiveContent()
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow("Логин", entry.username)
                if (cardHint != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Мнемоническая подсказка", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(cardHint, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                InfoRow("Пароль", decryptedPassword ?: "••••••••", isPassword = true)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = {
                        if (decryptedPassword != null) decryptedPassword = null
                        else requestAccess(PasswordCardAction.REVEAL)
                    }) {
                        Icon(
                            if (decryptedPassword != null) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (decryptedPassword != null) "Скрыть пароль" else "Показать пароль после PIN папки"
                        )
                    }
                    IconButton(onClick = { requestAccess(PasswordCardAction.COPY) }) {
                        Icon(Icons.Default.ContentCopy, "Копировать после PIN папки")
                    }
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Default.QrCode, "Показать QR-код")
                    }
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (entry.url?.isNotBlank() == true) InfoRow("URL", entry.url)
                if (entry.notes?.isNotBlank() == true) InfoRow("Заметки", entry.notes)
                if (entry.tags.isNotEmpty()) InfoRow("Теги", entry.tags.joinToString(", "))
                InfoRow("Создан", dateFormat.format(entry.createdAt))
                InfoRow("Изменён", dateFormat.format(entry.lastChanged))
            }
        },
        confirmButton = {
            Button(onClick = { clearSecrets(); onEdit() }) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Редактировать")
            }
        },
        dismissButton = {
            TextButton(onClick = { clearSecrets(); onDelete() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Удалить")
            }
        }
    )
    if (showAccessDialog) {
        ProfileAccessDialog(
            profile = profile,
            allowBiometric = false,
            onDismiss = { showAccessDialog = false; pendingAction = null },
            onGranted = { finishAccess() }
        )
    }
    if (showQrDialog) {
        QrCodeDialog(entry, profile, onDismiss = { showQrDialog = false })
    }
}

@Composable
private fun InfoRow(label: String, value: String, isPassword: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$label: ", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(80.dp))
        Text(value, fontSize = 14.sp,
            fontFamily = if (isPassword) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(1f))
    }
}
