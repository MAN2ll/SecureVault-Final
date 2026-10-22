package com.securevault.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

object ClipboardHelper {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    fun copyWithAutoClear(context: Context, text: String, label: String = "SecureVault", clearDelayMs: Long = 30_000L) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable {
            try {
                val current = clipboard.primaryClip
                if (current?.description?.label?.toString() == label && current.itemCount == 1 &&
                    current.getItemAt(0).text?.toString() == text) {
                    if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                    else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            } catch (_: Exception) { /* Буфер может быть недоступен в фоне. */ }
            pending = null
        }
        pending = task; handler.postDelayed(task, clearDelayMs.coerceAtLeast(0))
    }
}
