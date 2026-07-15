@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.data.Entry
import com.securevault.data.Profile
import com.securevault.utils.SecureQrManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeDialog(
    entry: Entry,
    profile: Profile,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val qrContent = remember(entry.id, profile.id) {
        SecureQrManager.generateQrToken(entry.id, profile.id, context)
    }
    val qrBitmap = remember(qrContent) {
        SecureQrManager.generateQrBitmap(qrContent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.QrCode, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("SecureVault QR", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                    Text("Сервис: ${entry.service}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("Логин: ${entry.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Профиль: ${profile.name}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR код",
                    modifier = Modifier.size(200.dp)
                )
                
                Text(
                    "Пароль не содержится в QR-коде",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
