package com.securevault.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

object ClipboardHelper {
    
    private const val DEFAULT_CLEAR_DELAY_MS = 30_000L // 30 секунд
    private val handler = Handler(Looper.getMainLooper())
    private var clearRunnable: Runnable? = null
    
    /**
     * Копирует текст в буфер обмена с автоматической очисткой.
     * Повторный вызов отменяет предыдущий таймер.
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
        
        // Отменяем предыдущий таймер, если он был
        clearRunnable?.let { handler.removeCallbacks(it) }
        
        clearRunnable = Runnable {
            try {
                if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.label == label) {
                    //  СОВМЕСТИМАЯ ОЧИСТКА: API 28+ поддерживает clearPrimaryClip, ниже используем перезапись пустым значением
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            } catch (e: Exception) {
                // Игнорируем ошибки очистки
            } finally {
                clearRunnable = null
            }
        }
        
        handler.postDelayed(clearRunnable!!, clearDelayMs)
    }
}
