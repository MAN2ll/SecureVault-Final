package com.securevault.data

import org.json.JSONArray
import org.json.JSONObject

//  Добавлен недостающий класс EncryptedBackup
data class EncryptedBackup(
    val salt: String,
    val iv: String,
    val ciphertext: String,
    val iterations: Int = 200000
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("salt", salt)
        json.put("iv", iv)
        json.put("ciphertext", ciphertext)
        json.put("iterations", iterations)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): EncryptedBackup {
            val json = JSONObject(jsonString)
            return EncryptedBackup(
                salt = json.getString("salt"),
                iv = json.getString("iv"),
                ciphertext = json.getString("ciphertext"),
                iterations = json.optInt("iterations", 200000)
            )
        }
    }
}

data class BackupData(
    val version: Int = 3,
    val profiles: List<BackupProfile>
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("version", version)
        val profilesArray = JSONArray()
        profiles.forEach { profile ->
            val profileJson = JSONObject()
            profileJson.put("name", profile.name)
            profileJson.put("passwordAccessMode", profile.passwordAccessMode ?: JSONObject.NULL)
            profileJson.put("profileAccessMode", profile.profileAccessMode ?: JSONObject.NULL)
            
            val entriesArray = JSONArray()
            profile.entries.forEach { entry ->
                val entryJson = JSONObject()
                entryJson.put("service", entry.service)
                entryJson.put("username", entry.username)
                entryJson.put("password", entry.password)
                entryJson.put("url", entry.url ?: JSONObject.NULL)
                entryJson.put("notes", entry.notes ?: JSONObject.NULL)
                entryJson.put("textHint", entry.textHint ?: JSONObject.NULL)
                entryJson.put("rotationEnabled", entry.rotationEnabled)
                entryJson.put("rotationPeriodMonths", entry.rotationPeriodMonths)
                entryJson.put("nextRotationDate", entry.nextRotationDate ?: JSONObject.NULL)
                entryJson.put("isFavorite", entry.isFavorite)
                entryJson.put("createdAt", entry.createdAt)
                entryJson.put("lastChanged", entry.lastChanged)
                entryJson.put("generationType", entry.generationType ?: JSONObject.NULL)
                entryJson.put("mnemonicPhraseHint", entry.mnemonicPhraseHint ?: JSONObject.NULL)
                entryJson.put("mnemonicOptionsJson", entry.mnemonicOptionsJson ?: JSONObject.NULL)
                entryJson.put("passwordAccessMode", entry.passwordAccessMode ?: JSONObject.NULL)
                entryJson.put("passwordHistoryJson", entry.passwordHistoryJson ?: JSONObject.NULL)
                entryJson.put("passwordFingerprint", entry.passwordFingerprint ?: JSONObject.NULL)
                
                val historyArray = JSONArray()
                entry.portableHistory?.forEach { item ->
                    val itemJson = JSONObject()
                    itemJson.put("plainOldPassword", item.plainOldPassword)
                    itemJson.put("date", item.date)
                    itemJson.put("type", item.type)
                    itemJson.put("relatedService", item.relatedService ?: JSONObject.NULL)
                    itemJson.put("relatedEntryId", item.relatedEntryId ?: JSONObject.NULL)
                    itemJson.put("hint", item.hint ?: JSONObject.NULL)
                    itemJson.put("passwordFingerprint", item.passwordFingerprint ?: JSONObject.NULL)
                    historyArray.put(itemJson)
                }
                entryJson.put("portableHistory", historyArray)
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
            val version = json.optInt("version", 1)
            val profilesArray = json.getJSONArray("profiles")
            val profiles = mutableListOf<BackupProfile>()
            
            for (i in 0 until profilesArray.length()) {
                val profileJson = profilesArray.getJSONObject(i)
                val name = profileJson.getString("name")
                val passwordAccessMode = if (profileJson.has("passwordAccessMode") && !profileJson.isNull("passwordAccessMode")) {
                    profileJson.getString("passwordAccessMode")
                } else null
                
                val profileAccessMode = if (profileJson.has("profileAccessMode") && !profileJson.isNull("profileAccessMode")) {
                    profileJson.getString("profileAccessMode")
                } else null

                val entriesArray = profileJson.getJSONArray("entries")
                val entries = mutableListOf<BackupEntry>()
                
                for (j in 0 until entriesArray.length()) {
                    val entryJson = entriesArray.getJSONObject(j)
                    val portableHistoryArray = entryJson.optJSONArray("portableHistory")
                    val portableHistory = mutableListOf<PortableHistoryItem>()
                    
                    if (portableHistoryArray != null) {
                        for (k in 0 until portableHistoryArray.length()) {
                            val itemJson = portableHistoryArray.getJSONObject(k)
                            portableHistory.add(PortableHistoryItem(
                                plainOldPassword = itemJson.getString("plainOldPassword"),
                                date = itemJson.getLong("date"),
                                type = itemJson.getString("type"),
                                relatedService = if (itemJson.has("relatedService") && !itemJson.isNull("relatedService")) itemJson.getString("relatedService") else null,
                                relatedEntryId = if (itemJson.has("relatedEntryId") && !itemJson.isNull("relatedEntryId")) itemJson.getString("relatedEntryId") else null,
                                hint = if (itemJson.has("hint") && !itemJson.isNull("hint")) itemJson.getString("hint") else null,
                                passwordFingerprint = if (itemJson.has("passwordFingerprint") && !itemJson.isNull("passwordFingerprint")) itemJson.getString("passwordFingerprint") else null
                            ))
                        }
                    }
                    
                    entries.add(BackupEntry(
                        service = entryJson.getString("service"),
                        username = entryJson.getString("username"),
                        password = entryJson.getString("password"),
                        url = if (entryJson.has("url") && !entryJson.isNull("url")) entryJson.getString("url") else null,
                        notes = if (entryJson.has("notes") && !entryJson.isNull("notes")) entryJson.getString("notes") else null,
                        textHint = if (entryJson.has("textHint") && !entryJson.isNull("textHint")) entryJson.getString("textHint") else null,
                        rotationEnabled = entryJson.getBoolean("rotationEnabled"),
                        rotationPeriodMonths = entryJson.getInt("rotationPeriodMonths"),
                        nextRotationDate = if (entryJson.has("nextRotationDate") && !entryJson.isNull("nextRotationDate")) entryJson.getLong("nextRotationDate") else null,
                        isFavorite = entryJson.getBoolean("isFavorite"),
                        createdAt = entryJson.getLong("createdAt"),
                        lastChanged = entryJson.getLong("lastChanged"),
                        generationType = if (entryJson.has("generationType") && !entryJson.isNull("generationType")) entryJson.getString("generationType") else null,
                        mnemonicPhraseHint = if (entryJson.has("mnemonicPhraseHint") && !entryJson.isNull("mnemonicPhraseHint")) entryJson.getString("mnemonicPhraseHint") else null,
                        mnemonicOptionsJson = if (entryJson.has("mnemonicOptionsJson") && !entryJson.isNull("mnemonicOptionsJson")) entryJson.getString("mnemonicOptionsJson") else null,
                        passwordAccessMode = if (entryJson.has("passwordAccessMode") && !entryJson.isNull("passwordAccessMode")) entryJson.getString("passwordAccessMode") else null,
                        passwordHistoryJson = if (entryJson.has("passwordHistoryJson") && !entryJson.isNull("passwordHistoryJson")) entryJson.getString("passwordHistoryJson") else null,
                        passwordFingerprint = if (entryJson.has("passwordFingerprint") && !entryJson.isNull("passwordFingerprint")) entryJson.getString("passwordFingerprint") else null,
                        portableHistory = portableHistory
                    ))
                }
                
                profiles.add(BackupProfile(
                    oldProfileId = i,
                    name = name,
                    entries = entries,
                    passwordAccessMode = passwordAccessMode,
                    profileAccessMode = profileAccessMode
                ))
            }
            return BackupData(version = version, profiles = profiles)
        }
    }
}

data class BackupProfile(
    val oldProfileId: Int,
    val name: String,
    val entries: List<BackupEntry>,
    val passwordAccessMode: String? = null,
    val profileAccessMode: String? = null
)

// Добавлены passwordHistoryJson и passwordFingerprint
data class BackupEntry(
    val service: String,
    val username: String,
    val password: String,
    val url: String?,
    val notes: String?,
    val textHint: String?,
    val rotationEnabled: Boolean,
    val rotationPeriodMonths: Int,
    val nextRotationDate: Long?,
    val isFavorite: Boolean,
    val createdAt: Long,
    val lastChanged: Long,
    val generationType: String?,
    val mnemonicPhraseHint: String?,
    val mnemonicOptionsJson: String?,
    val passwordAccessMode: String?,
    val passwordHistoryJson: String?,
    val passwordFingerprint: String?,
    val portableHistory: List<PortableHistoryItem>? = null
)

data class PortableHistoryItem(
    val plainOldPassword: String,
    val date: Long,
    val type: String,
    val relatedService: String?,
    val relatedEntryId: String?,
    val hint: String?,
    val passwordFingerprint: String?
)
