@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.data.Entry
import com.securevault.utils.SecureQrManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeDialog(
    entry: Entry,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val qrBitmap = remember(entry.id) {
        try {
            val token = SecureQrManager.generateQrToken(entry.id, entry.profileId, context)
            SecureQrManager.generateQrBitmap(token, 512)
        } catch (e: Exception) {
            null
        }
    }

    var showSaveResult by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("QR-код для ${entry.service}", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR-код",
                        modifier = Modifier.size(280.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            val saved = saveQrToGallery(context, entry.service, qrBitmap)
                            showSaveResult = if (saved) "QR-код сохранён в галерею" else "Не удалось сохранить QR-код"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SaveAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Сохранить QR-код")
                    }
                } else {
                    Text("Не удалось сгенерировать QR-код", color = MaterialTheme.colorScheme.error)
                }

                //  НОВЫЙ ТЕКСТ: QR вечный
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Безопасный QR-код", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("• QR не содержит пароль", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("• QR открывает карточку записи внутри SecureVault", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("• QR работает только на этом устройстве и в этом профиле", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("• Для просмотра пароля нужен мастер-пароль", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("• QR остаётся действительным после ротации пароля", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("• После сканирования показывается актуальный пароль записи", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
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

    if (showSaveResult != null) {
        val msg = showSaveResult!!
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            showSaveResult = null
        }
    }
}

private fun saveQrToGallery(context: Context, serviceName: String, bitmap: Bitmap): Boolean {
    return try {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val safeServiceName = serviceName.replace(Regex("[^A-Za-z0-9]"), "_").take(30)
        val fileName = "SecureVault_QR_${safeServiceName}_$timestamp.png"

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SecureVault")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val imageUri = resolver.insert(collection, contentValues) ?: return false

        resolver.openOutputStream(imageUri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        } ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)
        }

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
