package com.securevault.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.provider.Settings
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SecureQrManager {
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val LEGACY_VERSION = "1"
    private const val TOKEN_VERSION = 2
    private const val MAX_QR_BYTES = 2000

    data class QrValidationResult(
        val isValid: Boolean,
        val entryId: String? = null,
        val profileId: Int? = null,
        val errorMessage: String? = null,
        val mnemonicPhrase: String? = null
    )

    /** The phrase is readable by any QR reader. The password is never exported here. */
    fun generateQrToken(entryId: String, profileId: Int, context: Context,
        mnemonicPhrase: String? = null): String {
        require(entryId.isNotBlank() && profileId > 0) { "Запись или папка не найдена" }
        val deviceId = getDeviceId(context)
        val timestamp = System.currentTimeMillis().toString()
        val phrase = MnemonicHint.normalize(mnemonicPhrase)
        val payload = canonicalPayload(entryId, profileId, deviceId, timestamp, phrase)
        val token = JSONObject().apply {
            put("secureVaultVersion", TOKEN_VERSION)
            put("entryId", entryId)
            put("profileId", profileId)
            put("deviceId", deviceId)
            put("createdAt", timestamp)
            put("mnemonicPhrase", phrase ?: JSONObject.NULL)
            put("signature", generateSignature(payload, context))
        }.toString()
        require(token.toByteArray(StandardCharsets.UTF_8).size <= MAX_QR_BYTES) {
            "Мнемоническая фраза слишком длинная для одного QR-кода. " +
                "Пароль и подсказка сохранены в приложении."
        }
        return token
    }

    data class QrPresentation(val bitmap: Bitmap, val phraseInCenter: Boolean)

    fun generateQrBitmap(content: String, size: Int = 1024): Bitmap =
        encodeBitmap(content, size, ErrorCorrectionLevel.M)

    /** Never publish an overlaid QR unless ZXing can read the exact original payload. */
    fun generateQrPresentation(content: String, mnemonicPhrase: String?, size: Int = 1024): QrPresentation {
        val highCorrection = runCatching { encodeBitmap(content, size, ErrorCorrectionLevel.H) }.getOrNull()
        val plain = highCorrection ?: generateQrBitmap(content, size)
        val phrase = MnemonicHint.normalize(mnemonicPhrase)
        if (phrase != null && highCorrection != null && phrase.length <= 600) {
            for (fontRatio in listOf(0.035f, 0.031f, 0.028f)) {
                val centered = withCenteredPhrase(plain, phrase, fontRatio) ?: continue
                if (canRead(centered, content)) return QrPresentation(centered, true)
            }
        }
        return QrPresentation(plain, false)
    }

    private fun encodeBitmap(content: String, size: Int, correction: ErrorCorrectionLevel): Bitmap {
        require(content.toByteArray(StandardCharsets.UTF_8).size <= MAX_QR_BYTES) {
            "Текст слишком длинный для одного QR-кода"
        }
        val hints = mapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 4,
            EncodeHintType.ERROR_CORRECTION to correction
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
    }

    private fun withCenteredPhrase(plain: Bitmap, phrase: String, fontRatio: Float): Bitmap? {
        val side = plain.width.toFloat()
        val padding = side * 0.014f
        val boxWidth = side * 0.46f
        val textWidth = (boxWidth - padding * 2).toInt()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = side * fontRatio
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        // StaticLayout wraps by words and honors the two-part phrase's newline.
        val layout = StaticLayout.Builder.obtain(phrase, 0, phrase.length, paint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
        val boxHeight = layout.height + padding * 2
        if (boxHeight > side * 0.22f) return null
        val left = (side - boxWidth) / 2
        val top = (plain.height - boxHeight) / 2
        val result = plain.copy(Bitmap.Config.ARGB_8888, true)
            ?: error("Не удалось подготовить изображение QR-кода")
        val canvas = Canvas(result)
        canvas.drawRoundRect(left, top, left + boxWidth, top + boxHeight,
            side * 0.008f, side * 0.008f, Paint().apply { color = Color.WHITE })
        canvas.save()
        canvas.translate(left + padding, top + padding)
        layout.draw(canvas)
        canvas.restore()
        return result
    }

    private fun canRead(bitmap: Bitmap, expected: String): Boolean = runCatching {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text == expected
    }.getOrDefault(false)

    fun validateQrToken(token: String, currentProfileId: Int, context: Context): QrValidationResult {
        return try {
            if (token.startsWith("{")) validateVersion2(token, currentProfileId, context)
            else validateLegacy(token, currentProfileId, context)
        } catch (_: Exception) {
            invalid()
        }
    }

    private fun validateVersion2(token: String, currentProfileId: Int, context: Context): QrValidationResult {
        if (token.toByteArray(StandardCharsets.UTF_8).size > MAX_QR_BYTES) return invalid()
        val data = JSONObject(token)
        if (data.getInt("secureVaultVersion") != TOKEN_VERSION) return invalid()
        val entryId = data.getString("entryId")
        val profileId = data.getInt("profileId")
        val deviceId = data.getString("deviceId")
        val timestamp = data.getString("createdAt")
        val phrase = if (data.isNull("mnemonicPhrase")) null else data.getString("mnemonicPhrase")
        if (entryId.isBlank() || profileId <= 0 || profileId != currentProfileId ||
            deviceId != getDeviceId(context) || timestamp.toLongOrNull() == null) return invalid()
        val payload = canonicalPayload(entryId, profileId, deviceId, timestamp, phrase)
        if (!signatureMatches(data.getString("signature"), generateSignature(payload, context))) return invalid()
        return QrValidationResult(true, entryId = entryId, profileId = profileId, mnemonicPhrase = phrase)
    }

    private fun validateLegacy(token: String, currentProfileId: Int, context: Context): QrValidationResult {
        val parts = token.split("|")
        if (parts.size != 6 || parts[0] != LEGACY_VERSION || parts[1].isBlank()) return invalid()
        val profileId = parts[2].toIntOrNull() ?: return invalid()
        if (profileId != currentProfileId || parts[3] != getDeviceId(context)) return invalid()
        val payload = parts.take(5).joinToString("|")
        if (!signatureMatches(parts[5], generateSignature(payload, context))) return invalid()
        return QrValidationResult(true, entryId = parts[1], profileId = profileId)
    }

    private fun canonicalPayload(entryId: String, profileId: Int, deviceId: String,
        timestamp: String, phrase: String?): String = JSONArray()
        .put(TOKEN_VERSION).put(entryId).put(profileId).put(deviceId).put(timestamp)
        .put(phrase ?: JSONObject.NULL).toString()

    private fun invalid() = QrValidationResult(false, errorMessage = "Недействительный QR-код")

    private fun signatureMatches(actual: String, expected: String): Boolean =
        MessageDigest.isEqual(Base64.decode(actual, Base64.NO_WRAP), Base64.decode(expected, Base64.NO_WRAP))

    private fun getDeviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: error("Не удалось определить это устройство")

    // Failure must not silently replace authentication with an unsigned checksum.
    private fun generateSignature(payload: String, context: Context): String {
        val key = getHmacKey(context)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return Base64.encodeToString(mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    @Synchronized
    private fun getHmacKey(context: Context): ByteArray {
        val prefs = context.getSharedPreferences("qr_hmac_prefs", Context.MODE_PRIVATE)
        val existingKey = prefs.getString("hmac_key", null)
        if (existingKey != null) {
            return Base64.decode(existingKey, Base64.NO_WRAP).also {
                require(it.size == 32) { "Ключ QR-кодов повреждён" }
            }
        }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        check(prefs.edit().putString("hmac_key", Base64.encodeToString(key, Base64.NO_WRAP)).commit()) {
            "Не удалось сохранить ключ QR-кодов"
        }
        return key
    }
}
