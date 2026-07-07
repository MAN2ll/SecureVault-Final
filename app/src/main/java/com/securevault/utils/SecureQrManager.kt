package com.securevault.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.security.MessageDigest

object SecureQrManager {

    private const val QR_TYPE = "securevault_qr_v1"
    private const val PREFS_NAME = "qr_tokens_prefs"

    data class QrValidationResult(
        val isValid: Boolean,
        val entryId: String? = null,
        val profileId: Int? = null,
        val errorMessage: String? = null
    )

    // Получение или создание стабильного токена для записи
    private fun getOrCreateStableToken(entryId: String, context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingToken = prefs.getString("token_$entryId", null)
        if (existingToken != null) return existingToken

        // Генерируем новый UUID-подобный токен детерминированно
        val token = generateDeterministicToken(entryId)
        prefs.edit().putString("token_$entryId", token).apply()
        return token
    }

    //  Детерминированная генерация токена на основе entryId
    private fun generateDeterministicToken(entryId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(entryId.toByteArray())
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP).take(32)
    }

    //  Получение device binding hash
    private fun getDeviceBindingHash(context: Context): String {
        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "default_device"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest("securevault_device_$deviceId".toByteArray())
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP).take(24)
    }

    // Генерация QR-токена БЕЗ lastChanged
    fun generateQrToken(entryId: String, profileId: Int, context: Context): String {
        val stableToken = getOrCreateStableToken(entryId, context)
        val deviceBindingHash = getDeviceBindingHash(context)
        val profileIdHash = hashProfileId(profileId)

        val payload = JSONObject().apply {
            put("type", QR_TYPE)
            put("entryId", entryId)
            put("profileIdHash", profileIdHash)
            put("deviceBindingHash", deviceBindingHash)
            put("stableToken", stableToken)
        }

        return payload.toString()
    }

    private fun hashProfileId(profileId: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest("profile_$profileId".toByteArray())
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP).take(16)
    }

    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }

    //  Валидация БЕЗ проверки lastChanged
    fun validateQrToken(token: String, currentProfileId: Int, context: Context): QrValidationResult {
        return try {
            val json = JSONObject(token)

            val type = json.optString("type")
            if (type != QR_TYPE) {
                return QrValidationResult(false, errorMessage = "Недействительный QR-код")
            }

            val entryId = json.optString("entryId")
            val profileIdHash = json.optString("profileIdHash")
            val deviceBindingHash = json.optString("deviceBindingHash")
            val stableToken = json.optString("stableToken")

            if (entryId.isBlank() || profileIdHash.isBlank() || deviceBindingHash.isBlank() || stableToken.isBlank()) {
                return QrValidationResult(false, errorMessage = "QR-код повреждён")
            }

            // Проверка привязки к устройству
            val expectedDeviceHash = getDeviceBindingHash(context)
            if (deviceBindingHash != expectedDeviceHash) {
                return QrValidationResult(false, errorMessage = "QR-код не принадлежит этому устройству")
            }

            // Проверка привязки к профилю
            val expectedProfileHash = hashProfileId(currentProfileId)
            if (profileIdHash != expectedProfileHash) {
                return QrValidationResult(false, errorMessage = "QR-код не принадлежит этому профилю")
            }

            // Проверка стабильного токена
            val expectedToken = getOrCreateStableToken(entryId, context)
            if (stableToken != expectedToken) {
                return QrValidationResult(false, errorMessage = "QR-код недействителен")
            }

            //  НЕ проверяем lastChanged — QR остаётся действительным после ротации
            QrValidationResult(true, entryId = entryId, profileId = currentProfileId)
        } catch (e: Exception) {
            QrValidationResult(false, errorMessage = "Ошибка чтения QR-кода")
        }
    }
}
