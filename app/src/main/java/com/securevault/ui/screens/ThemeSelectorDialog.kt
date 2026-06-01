package com.securevault.ui.screens
import androidx.compose.foundation.clickable // ✅ Для .clickable()
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun ThemeSelectorDialog(
    current: ThemeMode,
    onDismiss: () -> Unit,
    onSelected: (ThemeMode) -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        title = { Text("Тема оформления", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOption("Системная", Icons.Default.BrightnessAuto, selected == ThemeMode.SYSTEM) { selected = ThemeMode.SYSTEM }
                ThemeOption("Светлая", Icons.Default.BrightnessHigh, selected == ThemeMode.LIGHT) { selected = ThemeMode.LIGHT }
                ThemeOption("Тёмная", Icons.Default.BrightnessLow, selected == ThemeMode.DARK) { selected = ThemeMode.DARK }
            }
        },
        confirmButton = { TextButton(onClick = { onSelected(selected); onDismiss() }) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun ThemeOption(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Icon(icon, null, Modifier.padding(start = 8.dp))
        Text(title, Modifier.padding(start = 12.dp))
    }
}
