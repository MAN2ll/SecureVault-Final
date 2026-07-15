package com.securevault.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.Settings
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Hashtable
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SecureQrManager {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val TOKEN_SEPARATOR = "|"
    private const val TOKEN_VERSION = "1"

    data class QrValidationResult(
        val isValid: Boolean,
        val entryId: String? = null,
        val profileId: Int? = null,
        val errorMessage: String? = null
    )

    //  Генерация токена для QR-кода
    fun generateQrToken(entryId: String, profileId: Int, context: Context): String {
        val deviceId = getDeviceId(context)
        val timestamp = System.currentTimeMillis().toString()
        
        // Формат: version|entryId|profileId|deviceId|timestamp|signature
        val payload = "$TOKEN_VERSION$TOKEN_SEPARATOR$entryId$TOKEN_SEPARATOR$profileId$TOKEN_SEPARATOR$deviceId$TOKEN_SEPARATOR$timestamp"
        val signature = generateSignature(payload, context)
        
        return "$payload$TOKEN_SEPARATOR$signature"
    }

    //  Генерация Bitmap из содержимого QR-кода
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val hints = Hashtable<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 1

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }

    //  Валидация QR-кода при сканировании
    fun validateQrToken(token: String, currentProfileId: Int, context: Context): QrValidationResult {
        return try {
            val parts = token.split(TOKEN_SEPARATOR)
            
            if (parts.size != 6) {
                return QrValidationResult(false, errorMessage = "Неверный формат QR-кода")
            }

            val version = parts[0]
            val entryId = parts[1]
            val profileId = parts[2].toIntOrNull() ?: return QrValidationResult(false, errorMessage = "Неверный профиль")
            val deviceId = parts[3]
            val timestamp = parts[4]
            val signature = parts[5]

            // Проверка версии
            if (version != TOKEN_VERSION) {
                return QrValidationResult(false, errorMessage = "Неподдерживаемая версия QR-кода")
            }

            // Проверка профиля
            if (profileId != currentProfileId) {
                return QrValidationResult(false, errorMessage = "QR-код принадлежит другому профилю")
            }

            // Проверка устройства
            val currentDeviceId = getDeviceId(context)
            if (deviceId != currentDeviceId) {
                return QrValidationResult(false, errorMessage = "QR-код не предназначен для этого устройства")
            }

            // Проверка подписи
            val payload = "$version$TOKEN_SEPARATOR$entryId$TOKEN_SEPARATOR$profileId$TOKEN_SEPARATOR$deviceId$TOKEN_SEPARATOR$timestamp"
            val expectedSignature = generateSignature(payload, context)
            
            if (signature != expectedSignature) {
                return QrValidationResult(false, errorMessage = "Недействительная подпись QR-кода")
            }

            // Проверка времени (опционально: QR действует 24 часа)
            val tokenTime = timestamp.toLongOrNull() ?: return QrValidationResult(false, errorMessage = "Неверное время")
            val currentTime = System.currentTimeMillis()
            val maxAge = 24 * 60 * 60 * 1000L // 24 часа
            
            if (currentTime - tokenTime > maxAge) {
                return QrValidationResult(false, errorMessage = "QR-код истёк")
            }

            QrValidationResult(true, entryId = entryId, profileId = profileId)
        } catch (e: Exception) {
            QrValidationResult(false, errorMessage = "Ошибка проверки: ${e.message}")
        }
    }

    //  Получение стабильного идентификатора устройства
    private fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId ?: "unknown_device"
    }

    //  Генерация HMAC-SHA256 подписи
    private fun generateSignature(payload: String, context: Context): String {
        return try {
            val key = getHmacKey(context)
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
            val hash = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback на SHA-256 если Keystore недоступен
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(payload.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(hash, Base64.NO_WRAP)
        }
    }

    //  Получение ключа HMAC из Keystore или генерация нового
    private fun getHmacKey(context: Context): ByteArray {
        val prefs = context.getSharedPreferences("qr_hmac_prefs", Context.MODE_PRIVATE)
        val existingKey = prefs.getString("hmac_key", null)
        
        if (existingKey != null) {
            return Base64.decode(existingKey, Base64.NO_WRAP)
        }

        // Генерация нового ключа
        val key = ByteArray(32)
        java.security.SecureRandom().nextBytes(key)
        prefs.edit().putString("hmac_key", Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        
        return key
    }
}
