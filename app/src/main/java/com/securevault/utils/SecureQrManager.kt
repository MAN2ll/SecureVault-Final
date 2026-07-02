package com.securevault.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Менеджер защищённых QR-кодов.
 * QR содержит ТОЛЬКО entryId + токен, но НЕ пароль.
 */
object SecureQrManager {

    private const val QR_TYPE = "securevault_qr_v1"

    /**
     * Генерация QR-токена для записи.
     */
    fun generateQrToken(entryId: String, profileId: Int, context: Context): String {
        val prefs = context.getSharedPreferences("qr_prefs", Context.MODE_PRIVATE)
        
        // Привязка к профилю
        val profileIdHash = hashString("profile_$profileId")
        
        // Привязка к устройству (Android ID)
        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val deviceBindingHash = hashString("device_$deviceId")
        
        // Уникальный nonce
        val nonce = UUID.randomUUID().toString()
        
        // Сохраняем nonce для проверки
        prefs.edit()
            .putString("qr_nonce_$entryId", nonce)
            .putLong("qr_nonce_time_$entryId", System.currentTimeMillis())
            .apply()
        
        val payload = JSONObject().apply {
            put("type", QR_TYPE)
            put("entryId", entryId)
            put("profileIdHash", profileIdHash)
            put("deviceBindingHash", deviceBindingHash)
            put("nonce", nonce)
        }
        
        return payload.toString()
    }

    /**
     * Генерация Bitmap QR-кода.
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        
        return bitmap
    }

    /**
     * Проверка QR-токена.
     */
    fun validateQrToken(token: String, profileId: Int, context: Context): QrValidationResult {
        return try {
            val json = JSONObject(token)
            
            if (json.optString("type") != QR_TYPE) {
                return QrValidationResult(false, "Неверный тип QR-кода")
            }
            
            val entryId = json.optString("entryId", "")
            val profileIdHash = json.optString("profileIdHash", "")
            val deviceBindingHash = json.optString("deviceBindingHash", "")
            val nonce = json.optString("nonce", "")
            
            if (entryId.isBlank()) {
                return QrValidationResult(false, "QR-код повреждён")
            }
            
            // Проверка профиля
            val expectedProfileHash = hashString("profile_$profileId")
            if (profileIdHash != expectedProfileHash) {
                return QrValidationResult(false, "QR-код не принадлежит этому профилю")
            }
            
            // Проверка устройства
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            val expectedDeviceHash = hashString("device_$deviceId")
            if (deviceBindingHash != expectedDeviceHash) {
                return QrValidationResult(false, "QR-код не принадлежит этому устройству")
            }
            
            // Проверка nonce
            val prefs = context.getSharedPreferences("qr_prefs", Context.MODE_PRIVATE)
            val storedNonce = prefs.getString("qr_nonce_$entryId", null)
            if (storedNonce != nonce) {
                return QrValidationResult(false, "QR-код недействителен")
            }
            
            QrValidationResult(true, null, entryId)
        } catch (e: Exception) {
            QrValidationResult(false, "Ошибка чтения QR-кода")
        }
    }

    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    data class QrValidationResult(
        val isValid: Boolean,
        val errorMessage: String?,
        val entryId: String? = null
    )
}
