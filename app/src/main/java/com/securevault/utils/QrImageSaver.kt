package com.securevault.utils

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

interface QrSaveHost {
    fun launchQrDocumentSave(fileName: String)
}

fun Context.qrSaveHost(): QrSaveHost? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is QrSaveHost) return current
        val next = current.baseContext
        if (next === current) break
        current = next
    }
    return current as? QrSaveHost
}

object QrImageSaver {
    private const val PREFERENCES = "qr_image_export"
    private const val PENDING_FILE = "pending_file"
    private const val CACHE_DIRECTORY = "qr_exports"

    /** Keep the complete phrase in the exported PNG, including the below-QR fallback. */
    fun imageForExport(presentation: SecureQrManager.QrPresentation, phrase: String?): Bitmap {
        val text = MnemonicHint.normalize(phrase)
        if (presentation.phraseInCenter || text == null) return presentation.bitmap
        val qr = presentation.bitmap
        val padding = (qr.width * 0.045f).toInt()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = qr.width * 0.032f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, qr.width - padding * 2)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
        return Bitmap.createBitmap(qr.width, qr.height + layout.height + padding * 2,
            Bitmap.Config.ARGB_8888).also { result ->
            val canvas = Canvas(result)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(qr, 0f, 0f, null)
            canvas.save()
            canvas.translate(padding.toFloat(), (qr.height + padding).toFloat())
            layout.draw(canvas)
            canvas.restore()
        }
    }

    fun fileName(): String = "SecureVault_QR_" +
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date()) +
        "_${UUID.randomUUID().toString().take(8)}.png"

    /** Android 10+: publish only a completely written image; no storage permission is needed. */
    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    fun saveToGallery(context: Context, bitmap: Bitmap): Uri {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Используйте системный выбор файла" }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName())
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SecureVault")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = requireNotNull(resolver.insert(collection, values)) { "Не удалось создать изображение в галерее" }
        try {
            requireNotNull(resolver.openOutputStream(uri, "w")) { "Не удалось открыть файл для записи" }.use {
                writePng(bitmap, it)
            }
            val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            check(resolver.update(uri, published, null, null) > 0) { "Не удалось завершить сохранение QR-кода" }
            return uri
        } catch (failure: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw failure
        }
    }

    /** Save a private snapshot before opening the picker, which closes sensitive dialogs. */
    @Synchronized
    fun prepareDocumentSave(context: Context, bitmap: Bitmap): String {
        discardPending(context)
        val directory = File(context.cacheDir, CACHE_DIRECTORY)
        check(directory.isDirectory || directory.mkdirs()) { "Не удалось подготовить изображение" }
        val name = fileName()
        val file = File(directory, name)
        try {
            file.outputStream().use { writePng(bitmap, it) }
            check(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(PENDING_FILE, name).commit()) { "Не удалось подготовить сохранение QR-кода" }
            return name
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
    }

    /** Called by MainActivity so a navigation reset does not lose the export result. */
    @Synchronized
    fun finishDocumentSave(context: Context, uri: Uri?): Boolean {
        val file = pendingFile(context)
        if (uri == null) { discardPending(context); return false }
        try {
            val source = requireNotNull(file) { "Откройте QR и повторите сохранение." }
            check(source.isFile) { "Изображение больше недоступно. Откройте QR и повторите сохранение." }
            requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
                "Не удалось открыть выбранный файл"
            }.use { output -> source.inputStream().use { input -> input.copyTo(output) }; output.flush() }
            return true
        } catch (failure: Throwable) {
            // ACTION_CREATE_DOCUMENT returns a newly created file, never an existing overwrite target.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw failure
        } finally {
            discardPending(context)
        }
    }

    @Synchronized
    fun discardPending(context: Context) {
        pendingFile(context)?.delete()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(PENDING_FILE).commit()
    }

    private fun pendingFile(context: Context): File? {
        val name = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(PENDING_FILE, null) ?: return null
        if (!Regex("SecureVault_QR_[0-9]{8}_[0-9]{6}_[a-f0-9]{8}\\.png").matches(name)) return null
        return File(File(context.cacheDir, CACHE_DIRECTORY), name)
    }

    private fun writePng(bitmap: Bitmap, output: OutputStream) {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Не удалось записать PNG-изображение" }
        output.flush()
    }
}
