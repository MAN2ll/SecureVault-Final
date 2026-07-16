package com.securevault.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

@Composable
fun LockActionButton(onLock: () -> Unit) {
    IconButton(onClick = onLock) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Заблокировать приложение"
        )
    }
}
