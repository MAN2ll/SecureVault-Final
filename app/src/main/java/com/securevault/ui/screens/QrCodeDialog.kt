@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.utils.MnemonicHint
import com.securevault.utils.SecureQrManager
import com.securevault.utils.QrImageSaver
import com.securevault.utils.qrSaveHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun QrCodeDialog(entry: Entry, profile: Profile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val phrase = remember(entry.mnemonicOptionsJson, entry.mnemonicPhraseHint) {
        MnemonicHint.phraseForQr(entry.mnemonicOptionsJson, entry.mnemonicPhraseHint)
    }
    var presentation by remember(entry, profile) { mutableStateOf<SecureQrManager.QrPresentation?>(null) }
    var error by remember(entry, profile) { mutableStateOf<String?>(null) }
    var saving by remember(entry, profile) { mutableStateOf(false) }
    var saveMessage by remember(entry, profile) { mutableStateOf<String?>(null) }
    var saveError by remember(entry, profile) { mutableStateOf<String?>(null) }
    fun saveQr() {
        val ready = presentation ?: return
        if (saving) return
        val host = context.qrSaveHost()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && host == null) {
            saveError = "Не удалось открыть выбор места сохранения"
            return
        }
        saving = true; saveMessage = null; saveError = null
        scope.launch {
            try {
                val image = withContext(Dispatchers.Default) { QrImageSaver.imageForExport(ready, phrase) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    withContext(Dispatchers.IO) { QrImageSaver.saveToGallery(context.applicationContext, image) }
                    saveMessage = "Сохранено в галерее: Pictures/SecureVault"
                } else {
                    val fileName = withContext(Dispatchers.IO) {
                        QrImageSaver.prepareDocumentSave(context.applicationContext, image)
                    }
                    try {
                        requireNotNull(host).launchQrDocumentSave(fileName)
                    } catch (failure: Exception) {
                        withContext(Dispatchers.IO) { QrImageSaver.discardPending(context.applicationContext) }
                        throw failure
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { saveError = e.message ?: "Не удалось сохранить QR-код"
            } finally { saving = false }
        }
    }
    LaunchedEffect(entry, profile, phrase) {
        presentation = null
        error = null
        try {
            presentation = withContext(Dispatchers.Default) {
                require(entry.profileId == profile.id) { "Папка записи не совпадает" }
                val token = SecureQrManager.generateQrToken(entry.id, profile.id, context, phrase)
                SecureQrManager.generateQrPresentation(token, phrase)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { error = e.message ?: "Не удалось создать QR-код" }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = { Icon(Icons.Default.QrCode, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("SecureVault QR", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Сервис: ${entry.service}", style = MaterialTheme.typography.bodyMedium)
                    Text("Логин: ${entry.username}", style = MaterialTheme.typography.bodySmall)
                    Text("Папка: ${profile.name}", style = MaterialTheme.typography.bodySmall)
                }
                val ready = presentation
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    ready == null -> CircularProgressIndicator()
                    else -> {
                        Image(ready.bitmap.asImageBitmap(),
                            contentDescription = "QR-код" + if (ready.phraseInCenter) ": $phrase" else "",
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                        if (phrase != null && !ready.phraseInCenter) {
                            Text("Мнемоническая фраза", style = MaterialTheme.typography.labelMedium)
                            Text(phrase, style = MaterialTheme.typography.bodyMedium)
                            Text("Фраза показана под кодом, чтобы QR надёжно считывался.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                saveMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall) }
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall) }
                Text(if (phrase != null)
                    "Фраза записана в QR открытым текстом и читается обычным сканером. Самого пароля в QR нет."
                    else "QR открывает запись на этом устройстве. Пароля в нём нет; мнемоническая фраза не сохранена.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { saveQr() }, enabled = presentation != null && !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (saving) "Сохранение…" else "Сохранить QR")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Закрыть") } }
    )
}
