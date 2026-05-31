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
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportKey = getOrCreateExportKey()
            
            val jsonArray = JSONArray()
            entries.forEach { entry ->
                jsonArray.put(JSONObject().apply {
                    put("id", entry.id)
                    put("service", entry.service)
                    put("username", entry.username)
                    put("encryptedPassword", entry.encryptedPassword)
                    put("category", entry.category)
                    put("notes", entry.notes)
                    put("isFavorite", entry.isFavorite)
                    put("profile", entry.profile.name)
                    put("createdAt", entry.createdAt)
                    put("lastChanged", entry.lastChanged)
                    put("changeIntervalDays", entry.changeIntervalDays)
                })
            }
            val json = jsonArray.toString()
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, exportKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(json.toByteArray())
            
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext ExportResult.Error("Не удалось открыть файл")
            
            val writer = OutputStreamWriter(outputStream)
            writer.appendLine("$MAGIC_HEADER|${masterPasswordHash.take(16)}")
            writer.appendLine(Base64.encodeToString(iv + encrypted, Base64.DEFAULT))
            writer.flush()
            writer.close()
            
            ExportResult.Success(entries.size)
            
        } catch (e: Exception) {
            ExportResult.Error("Ошибка экспорта: ${e.message}")
        }
    }

    suspend fun importFromFile(
        uri: Uri,
        masterPasswordHash: String
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Error("Не удалось открыть файл")
            
            val reader = InputStreamReader(inputStream)
            val lines = reader.readLines()
            reader
