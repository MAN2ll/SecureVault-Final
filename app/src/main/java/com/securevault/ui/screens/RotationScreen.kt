@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.securevault.ui.screens

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securevault.ui.components.LockActionButton
import com.securevault.viewmodel.VaultViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RotationScreen(profileId: Int? = null, onBack: () -> Unit, onLock: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(), onEditEntry: (String) -> Unit = {}) {
    val all by viewModel.allEntries.collectAsState()
    val entries = all.filter { it.profileId == profileId && it.rotationEnabled }.sortedBy { it.nextRotationDate ?: Long.MAX_VALUE }
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Сроки смены паролей") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
        actions = { LockActionButton(onLock) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Напоминания о смене. Пароль на сайте необходимо изменить самостоятельно.") }
            if (entries.isEmpty()) item { Text("Нет записей с напоминаниями") }
            message?.let { item { Text(it) } }
            items(entries, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(entry.service); Text(entry.username)
                    Text(entry.nextRotationDate?.let { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it)) } ?: "Срок не задан")
                    Button(onClick = { onEditEntry(entry.id) }) { Text("Изменить пароль") }
                    TextButton(onClick = {
                        try {
                            val start = entry.nextRotationDate ?: System.currentTimeMillis()
                            context.startActivity(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                                .putExtra(CalendarContract.Events.TITLE, "Сменить пароль: ${entry.service}")
                                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 30 * 60_000L))
                        } catch (_: Exception) { message = "Приложение календаря не найдено" }
                    }) { Text("Добавить в календарь") }
                } }
            }
        }
    }
}
