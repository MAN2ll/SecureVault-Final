package com.securevault.utils

import android.content.Context
import android.net.Uri
import com.securevault.data.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import java.security.KeyStore

class ExportManager(private val context: Context) {

    companion object {
        private const val MAGIC_HEADER = "SV_EXPORT_V1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val KEY_ALIAS = "SecureVaultExportKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    suspend fun exportToFile(
        entries: List<Entry>,
        uri: Uri,
        masterPasswordHash: String
    ): ExportResult {
        return withContext(Dispatchers.IO) {
            try {
                doExport(entries, uri, masterPasswordHash)
            } catch (e: Exception) {
                ExportResult.Error("Ошибка: ${e.message}")
            }
        }
    }

    private fun doExport(
        entries: List<Entry>,
        uri: Uri,
        masterPasswordHash: String
    ): ExportResult {
        val exportKey = getOrCreateExportKey()
        val jsonArray = JSONArray()
        
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("service", entry.service)
            obj.put("username", entry.username)
            obj.put("encryptedPassword", entry.encryptedPassword)
            obj.put("category", entry.category)
            obj.put("notes", entry.notes)
            obj.put("isFavorite", entry.isFavorite)
            obj.put("profile", entry.profile.name)
            obj.put("createdAt", entry.createdAt)
            obj.put("lastChanged", entry.lastChanged)
            obj.put("changeIntervalDays", entry.changeIntervalDays)
            jsonArray.put(obj)
        }
        
        val json = jsonArray.toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, exportKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(json.toByteArray())
        
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: return ExportResult.Error("Не удалось открыть файл")
            
        val writer = OutputStreamWriter(outputStream)
        writer.appendLine("$MAGIC_HEADER|${masterPasswordHash.take(16)}")
        writer.appendLine(Base64.encodeToString(iv + encrypted, Base64.DEFAULT))
        writer.flush()
        writer.close()
        
        return ExportResult.Success(entries.size)
    }

    suspend fun importFromFile(
        uri: Uri,
        masterPasswordHash: String
    ): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                doImport(uri, masterPasswordHash)
            } catch (e: Exception) {
                ImportResult.Error("Ошибка: ${e.message}")
            }
        }
    }

    private fun doImport(uri: Uri, masterPasswordHash: String): ImportResult {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return ImportResult.Error("Не удалось открыть файл")
            
        val reader = InputStreamReader(inputStream)
        val lines = reader.readLines()
        reader.close()
        
        if (lines.isEmpty() || !lines[0].startsWith(MAGIC_HEADER)) {
            return ImportResult.Error("Неверный формат файла")
        }
        
        val headerParts = lines[0].split("|")
        if (headerParts.size < 2 || headerParts[1] != masterPasswordHash.take(16)) {
            return ImportResult.Error("Файл создан другим пользователем")
        }
        
        val exportKey = getOrCreateExportKey()
        val encryptedData = Base64.decode(lines[1], Base64.DEFAULT)
        val iv = encryptedData.copyOfRange(0, IV_SIZE)
        val ciphertext = encryptedData.copyOfRange(IV_SIZE, encryptedData.size)
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, exportKey, GCMParameterSpec(128, iv))
        val json = String(cipher.doFinal(ciphertext))
        
        val jsonArray = JSONArray(json)
        val entries = mutableListOf<Entry>()
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val entry = Entry(
                id = obj.getString("id"),
                service = obj.getString("service"),
                username = obj.getString("username"),
                encryptedPassword = obj.getString("encryptedPassword"),
                category = obj.optString("category", "general"),
                notes = obj.optString("notes", ""),
                isFavorite = obj.optBoolean("isFavorite", false),
                profile = com.securevault.data.Profile.valueOf(
                    obj.optString("profile", "PERSONAL")
                ),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                lastChanged = obj.optLong("lastChanged", System.currentTimeMillis()),
                changeIntervalDays = obj.optInt("changeIntervalDays", 90)
            )
            entries.add(entry)
        }
        
        return ImportResult.Success(entries)
    }

    private fun getOrCreateExportKey(): SecretKey {
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        
        return KeyGenerator.getInstance("AES", ANDROID_KEYSTORE).apply {
            init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            )
        }.generateKey()
    }

    sealed class ExportResult {
        data class Success(val count: Int) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    sealed class ImportResult {
        data class Success(val entries: List<Entry>) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }
}
