package com.securevault.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ClipboardHelper {
    private const val AUTO_CLEAR_DELAY = 30_000L // 30 секунд

    fun copyWithAutoClear(
        context: Context,
        text: String,
        label: String = "SecureVault",
        delayMs: Long = AUTO_CLEAR_DELAY,
        scope: CoroutineScope,
        onCleared: (() -> Unit)? = null
    ): ClipboardResult {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            
            scope.launch {
                delay(delayMs)
                try {
                    // Проверяем, что мы всё ещё владеем этим клипом, прежде чем очищать
                    if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.label == label) {
                        clipboard.clearPrimaryClip()
                        onCleared?.invoke()
                    }
                } catch (e: Exception) {
                    // Игнорируем ошибки при очистке
                }
            }
            ClipboardResult.Success
        } catch (e: Exception) {
            ClipboardResult.Error(e.message ?: "Unknown error")
        }
    }

    sealed class ClipboardResult {
        object Success : ClipboardResult()
        data class Error(val message: String) : ClipboardResult()
    }
}
