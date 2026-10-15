package com.securevault.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper

object ClipboardHelper {
    
    private const val DEFAULT_CLEAR_DELAY_MS = 30_000L // 30 секунд
    
    /**
     * Копирует текст в буфер обмена с автоматической очисткой через указанное время.
     */
    fun copyWithAutoClear(
        context: Context,
        text: String,
        label: String = "SecureVault",
        clearDelayMs: Long = DEFAULT_CLEAR_DELAY_MS
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        
        // Автоматическая очистка буфера через указанное время
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // Проверяем, что мы всё ещё владеем этим клипом, прежде чем очищать
                if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.label == label) {
                    clipboard.clearPrimaryClip()
                }
            } catch (e: Exception) {
                // Игнорируем ошибки очистки (например, если буфер уже был очищен пользователем)
            }
        }, clearDelayMs)
    }
}
