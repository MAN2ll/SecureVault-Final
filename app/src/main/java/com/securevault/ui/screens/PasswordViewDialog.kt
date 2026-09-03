@file:OptIn(ExperimentalMaterial3Api::class)

package com.securevault.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.data.Entry
import com.securevault.utils.CryptoUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordViewDialog(
    entry: Entry,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }
    
    // Расшифровываем пароль для отображения
    val decryptedPassword = remember(entry.encryptedPassword) {
        try { CryptoUtils.decrypt(entry.encryptedPassword) } catch (e: Exception) { "Ошибка расшифровки" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.service, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow("Логин", entry.username)
                
                InfoRow("Пароль", decryptedPassword, isPassword = true, visible = passwordVisible)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            "Показать/скрыть пароль"
                        )
                    }
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("password", decryptedPassword))
                    }) {
                        Icon(Icons.Default.ContentCopy, "Копировать пароль")
                    }
                }

                if (entry.url?.isNotBlank() == true) {
                    InfoRow("URL", entry.url)
                }
                
                if (entry.notes?.isNotBlank() == true) {
                    InfoRow("Заметки", entry.notes)
                }

                //  БЛОК 9: Отображение тегов в диалоге просмотра
                if (entry.tags.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            "Теги: ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.width(80.dp)
                        )
                        Text(
                            entry.tags.joinToString(", "),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                InfoRow("Создан", java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(entry.createdAt))
                InfoRow("Изменен", java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(entry.lastChanged))
            }
        },
        confirmButton = {
            Button(onClick = onEdit) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Редактировать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Удалить")
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String, isPassword: Boolean = false, visible: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "$label: ",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = if (isPassword && !visible) "••••••••" else value,
            fontSize = 14.sp,
            fontFamily = if (isPassword) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(1f)
        )
    }
}
