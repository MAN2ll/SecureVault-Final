package com.securevault.data

import org.json.JSONArray
import org.json.JSONObject

// Модели для защищённого backup v3

data class BackupProfile(
    val oldProfileId: Int,
    val name: String,
    val entries: List<BackupEntry>
)

data class BackupEntry(
    val service: String,
    val username: String,
    val encryptedPassword: String,
    val url: String?,
    val notes: String?,
    val textHint: String?,
    val generationType: String,
    val mnemonicPhraseHint: String?,
    val mnemonicOptionsJson: String?,
    val rotationEnabled: Boolean,
    val rotationPeriodMonths: Int,
    val nextRotationDate: Long?,
    val isFavorite: Boolean,
    val createdAt: Long,
    val lastChanged: Long,
    val passwordHistoryJson: String?,
    val passwordFingerprint: String?
)

data class BackupData(
    val version: Int = 3,
    val exportedAt: Long = System.currentTimeMillis(),
    val profiles: List<BackupProfile>
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("version", version)
        json.put("exportedAt", exportedAt)
        
        val profilesArray = JSONArray()
        for (profile in profiles) {
            val profileJson = JSONObject()
            profileJson.put("oldProfileId", profile.oldProfileId)
            profileJson.put("name", profile.name)
            
            val entriesArray = JSONArray()
            for (entry in profile.entries) {
                val entryJson = JSONObject()
                entryJson.put("service", entry.service)
                entryJson.put("username", entry.username)
                entryJson.put("encryptedPassword", entry.encryptedPassword)
                entryJson.put("url", entry.url ?: JSONObject.NULL)
                entryJson.put("notes", entry.notes ?: JSONObject.NULL)
                entryJson.put("textHint", entry.textHint ?: JSONObject.NULL)
                entryJson.put("generationType", entry.generationType)
                entryJson.put("mnemonicPhraseHint", entry.mnemonicPhraseHint ?: JSONObject.NULL)
                entryJson.put("mnemonicOptionsJson", entry.mnemonicOptionsJson ?: JSONObject.NULL)
                entryJson.put("rotationEnabled", entry.rotationEnabled)
                entryJson.put("rotationPeriodMonths", entry.rotationPeriodMonths)
                entryJson.put("nextRotationDate", entry.nextRotationDate ?: JSONObject.NULL)
                entryJson.put("isFavorite", entry.isFavorite)
                entryJson.put("createdAt", entry.createdAt)
                entryJson.put("lastChanged", entry.lastChanged)
                entryJson.put("passwordHistoryJson", entry.passwordHistoryJson ?: JSONObject.NULL)
                entryJson.put("passwordFingerprint", entry.passwordFingerprint ?: JSONObject.NULL)
                entriesArray.put(entryJson)
            }
            profileJson.put("entries", entriesArray)
            profilesArray.put(profileJson)
        }
        json.put("profiles", profilesArray)
        
        return json.toString()
    }
    
    companion object {
        fun fromJson(jsonString: String): BackupData {
            val json = JSONObject(jsonString)
            val version = json.optInt("version", 3)
            val exportedAt = json.optLong("exportedAt", System.currentTimeMillis())
            
            val profilesArray = json.getJSONArray("profiles")
            val profiles = mutableListOf<BackupProfile>()
            
            for (i in 0 until profilesArray.length()) {
                val profileJson = profilesArray.getJSONObject(i)
                val oldProfileId = profileJson.getInt("oldProfileId")
                val name = profileJson.getString("name")
                
                val entriesArray = profileJson.getJSONArray("entries")
                val entries = mutableListOf<BackupEntry>()
                
                for (j in 0 until entriesArray.length()) {
                    val entryJson = entriesArray.getJSONObject(j)
                    entries.add(
                        BackupEntry(
                            service = entryJson.getString("service"),
                            username = entryJson.getString("username"),
                            encryptedPassword = entryJson.getString("encryptedPassword"),
                            url = entryJson.optString("url").takeIf { it != "null" },
                            notes = entryJson.optString("notes").takeIf { it != "null" },
                            textHint = entryJson.optString("textHint").takeIf { it != "null" },
                            generationType = entryJson.optString("generationType", "random"),
                            mnemonicPhraseHint = entryJson.optString("mnemonicPhraseHint").takeIf { it != "null" },
                            mnemonicOptionsJson = entryJson.optString("mnemonicOptionsJson").takeIf { it != "null" },
                            rotationEnabled = entryJson.optBoolean("rotationEnabled", false),
                            rotationPeriodMonths = entryJson.optInt("rotationPeriodMonths", 6),
                            nextRotationDate = entryJson.optLong("nextRotationDate").takeIf { it != 0L },
                            isFavorite = entryJson.optBoolean("isFavorite", false),
                            createdAt = entryJson.optLong("createdAt", System.currentTimeMillis()),
                            lastChanged = entryJson.optLong("lastChanged", System.currentTimeMillis()),
                            passwordHistoryJson = entryJson.optString("passwordHistoryJson").takeIf { it != "null" },
                            passwordFingerprint = entryJson.optString("passwordFingerprint").takeIf { it != "null" }
                        )
                    )
                }
                
                profiles.add(BackupProfile(oldProfileId, name, entries))
            }
            
            return BackupData(version, exportedAt, profiles)
        }
    }
}

// Модель для зашифрованного файла
data class EncryptedBackup(
    val type: String = "securevault_backup_v3",
    val version: Int = 3,
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 200000,
    val salt: String,
    val iv: String,
    val ciphertext: String
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("type", type)
        json.put("version", version)
        json.put("kdf", kdf)
        json.put("iterations", iterations)
        json.put("salt", salt)
        json.put("iv", iv)
        json.put("ciphertext", ciphertext)
        return json.toString()
    }
    
    companion object {
        fun fromJson(jsonString: String): EncryptedBackup {
            val json = JSONObject(jsonString)
            return EncryptedBackup(
                type = json.optString("type", "securevault_backup_v3"),
                version = json.optInt("version", 3),
                kdf = json.optString("kdf", "PBKDF2WithHmacSHA256"),
                iterations = json.optInt("iterations", 200000),
                salt = json.getString("salt"),
                iv = json.getString("iv"),
                ciphertext = json.getString("ciphertext")
            )
        }
    }
}
