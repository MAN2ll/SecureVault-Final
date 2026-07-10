@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.securevault.data.Entry
import com.securevault.utils.SecureQrManager
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeDialog(
    entry: Entry,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val qrToken = remember(entry.id) {
        SecureQrManager.generateQrToken(entry.id, entry.profileId, context)
    }

    val qrBitmap = remember(qrToken) {
        SecureQrManager.generateQrBitmap(qrToken, 512)
    }

    var showSaveSuccess by remember { mutableStateOf(false) }
    var showSaveError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.QrCode, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("QR-код: ${entry.service}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Логин:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(entry.username, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR-код для ${entry.service}",
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "QR-код привязан к устройству и профилю",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "После ротации пароля этот QR-код продолжит работать и покажет новый пароль.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                if (showSaveSuccess) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("QR-код сохранён в галерею", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                if (showSaveError != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(showSaveError!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                //  Кнопки на всю ширину, не в Row
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val saved = saveQrToGallery(context, qrBitmap, entry.service)
                                if (saved) {
                                    showSaveSuccess = true
                                    showSaveError = null
                                } else {
                                    showSaveError = "Не удалось сохранить QR-код"
                                    showSaveSuccess = false
                                }
                            } catch (e: Exception) {
                                showSaveError = "Ошибка: ${e.message}"
                                showSaveSuccess = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Download, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Сохранить в галерею")
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                shareQrCode(context, qrBitmap, entry.service)
                            } catch (e: Exception) {
                                showSaveError = "Ошибка шаринга: ${e.message}"
                                showSaveSuccess = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Поделиться QR-кодом")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

private fun saveQrToGallery(context: Context, bitmap: Bitmap, serviceName: String): Boolean {
    return try {
        val picturesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            ?: context.filesDir
        val qrDir = File(picturesDir, "SecureVault_QR")
        if (!qrDir.exists()) qrDir.mkdirs()

        val fileName = "qr_${serviceName.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ]"), "_")}_${System.currentTimeMillis()}.png"
        val file = File(qrDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Уведомляем систему о новом файле
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/SecureVault_QR")
        }
        context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun shareQrCode(context: Context, bitmap: Bitmap, serviceName: String) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "qr_${serviceName.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ]"), "_")}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
            type = "image/png"
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(android.content.Intent.createChooser(shareIntent, "Поделиться QR-кодом"))
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка шаринга: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
