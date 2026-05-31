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
