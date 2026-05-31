package com.securevault.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHelper @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val AUTO_CLEAR_DELAY = 30_000L // 30 секунд
    }

    private val clipboard: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    fun copy(text: String, label: String = "SecureVault"): ClipboardResult {
        return try {
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            ClipboardResult.Success
        } catch (e: Exception) {
            ClipboardResult.Error(e.message ?: "Unknown error")
        }
    }

    fun copyWithAutoClear(
        text: String,
        label: String = "SecureVault",
        delayMs: Long = AUTO_CLEAR_DELAY,
        scope: CoroutineScope,
        onCleared: (() -> Unit)? = null
    ): ClipboardResult {
        val result = copy(text, label)
        if (result is ClipboardResult.Success) {
            scope.launch {
                delay(delayMs)
                clear()
                onCleared?.invoke()
            }
        }
        return result
    }

    fun clear() {
        try {
            clipboard.clearPrimaryClip()
        } catch (e: Exception) {
            // Игнорируем ошибки при очистке
        }
    }

    fun hasPrimaryClip(): Boolean {
        return clipboard.hasPrimaryClip()
    }

    fun getPrimaryClipText(): String? {
        return try {
            val item = clipboard.primaryClip?.getItemAt(0)
            item?.text?.toString()
        } catch (e: Exception) {
            null
        }
    }

    sealed class ClipboardResult {
        object Success : ClipboardResult()
        data class Error(val message: String) : ClipboardResult()
    }
}
