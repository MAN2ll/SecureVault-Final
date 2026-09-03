package com.securevault.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val profiles: List<BackupProfile>
) {
    fun toJson(): String {
        val array = JSONArray()
        for (profile in profiles) {
            array.put(profile.toJson())
        }
        return array.toString()
    }

    companion object {
        fun fromJson(jsonString: String): BackupData {
            val array = JSONArray(jsonString)
            val profiles = mutableListOf<BackupProfile>()
            for (i in 0 until array.length()) {
                profiles.add(BackupProfile.fromJson(array.getJSONObject(i)))
            }
            return BackupData(profiles = profiles)
        }
    }
}

data class BackupProfile(
    val oldProfileId: Int,
    val name: String,
    val entries: List<BackupEntry>,
    val passwordAccessMode: String? = null,
    val profileAccessMode: String? = null
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("oldProfileId", oldProfileId)
        obj.put("name", name)
        if (passwordAccessMode != null) obj.put("passwordAccessMode", passwordAccessMode)
        if (profileAccessMode != null) obj.put("profileAccessMode", profileAccessMode)
        
        val entriesArray = JSONArray()
        for (entry in entries) {
            entriesArray.put(entry.toJson())
        }
        obj.put("entries", entriesArray)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): BackupProfile {
            val entriesArray = obj.getJSONArray("entries")
            val entries = mutableListOf<BackupEntry>()
            for (i in 0 until entriesArray.length()) {
                entries.add(BackupEntry.fromJson(entriesArray.getJSONObject(i)))
            }
            return BackupProfile(
                oldProfileId = obj.getInt("oldProfileId"),
                name = obj.getString("name"),
                entries = entries,
                passwordAccessMode = obj.optString("passwordAccessMode", null),
                profileAccessMode = obj.optString("profileAccessMode", null)
            )
        }
    }
}

data class BackupEntry(
    val service: String,
    val username: String,
    val password: String,
    val url: String? = null,
    val notes: String? = null,
    val textHint: String? = null,
    val generationType: String = "random",
    val mnemonicPhraseHint: String? = null,
    val mnemonicOptionsJson: String? = null,
    val rotationEnabled: Boolean = false,
    val rotationPeriodMonths: Int = 6,
    val nextRotationDate: Long? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val lastChanged: Long,
    val passwordHistoryJson: String? = null,
    val passwordFingerprint: String? = null,
    val portableHistory: List<PortableHistoryItem>? = null,
    val passwordAccessMode: String? = null,
    val tagsCsv: String = "" //  БЛОК 10: Добавлено поле тегов
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("service", service)
        obj.put("username", username)
        obj.put("password", password)
        if (url != null) obj.put("url", url)
        if (notes != null) obj.put("notes", notes)
        if (textHint != null) obj.put("textHint", textHint)
        obj.put("generationType", generationType)
        if (mnemonicPhraseHint != null) obj.put("mnemonicPhraseHint", mnemonicPhraseHint)
        if (mnemonicOptionsJson != null) obj.put("mnemonicOptionsJson", mnemonicOptionsJson)
        obj.put("rotationEnabled", rotationEnabled)
        obj.put("rotationPeriodMonths", rotationPeriodMonths)
        if (nextRotationDate != null) obj.put("nextRotationDate", nextRotationDate)
        obj.put("isFavorite", isFavorite)
        obj.put("createdAt", createdAt)
        obj.put("lastChanged", lastChanged)
        if (passwordHistoryJson != null) obj.put("passwordHistoryJson", passwordHistoryJson)
        if (passwordFingerprint != null) obj.put("passwordFingerprint", passwordFingerprint)
        if (passwordAccessMode != null) obj.put("passwordAccessMode", passwordAccessMode)
        obj.put("tagsCsv", tagsCsv) //  БЛОК 10: Сериализация тегов
        
        if (portableHistory != null) {
            val historyArray = JSONArray()
            for (item in portableHistory) {
                val itemObj = JSONObject()
                itemObj.put("plainOldPassword", item.plainOldPassword)
                itemObj.put("date", item.date)
                itemObj.put("type", item.type)
                if (item.relatedService != null) itemObj.put("relatedService", item.relatedService)
                if (item.relatedEntryId != null) itemObj.put("relatedEntryId", item.relatedEntryId)
                if (item.hint != null) itemObj.put("hint", item.hint)
                if (item.passwordFingerprint != null) itemObj.put("passwordFingerprint", item.passwordFingerprint)
                historyArray.put(itemObj)
            }
            obj.put("portableHistory", historyArray)
        }
        
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): BackupEntry {
            val portableHistory = if (obj.has("portableHistory")) {
                val array = obj.getJSONArray("portableHistory")
                val list = mutableListOf<PortableHistoryItem>()
                for (i in 0 until array.length()) {
                    val itemObj = array.getJSONObject(i)
                    list.add(
                        PortableHistoryItem(
                            plainOldPassword = itemObj.getString("plainOldPassword"),
                            date = itemObj.getLong("date"),
                            type = itemObj.optString("type", "unknown"),
                            relatedService = itemObj.optString("relatedService", null),
                            relatedEntryId = itemObj.optString("relatedEntryId", null),
                            hint = itemObj.optString("hint", null),
                            passwordFingerprint = itemObj.optString("passwordFingerprint", null)
                        )
                    )
                }
                list
            } else null

            return BackupEntry(
                service = obj.getString("service"),
                username = obj.getString("username"),
                password = obj.getString("password"),
                url = obj.optString("url", null),
                notes = obj.optString("notes", null),
                textHint = obj.optString("textHint", null),
                generationType = obj.optString("generationType", "random"),
                mnemonicPhraseHint = obj.optString("mnemonicPhraseHint", null),
                mnemonicOptionsJson = obj.optString("mnemonicOptionsJson", null),
                rotationEnabled = obj.optBoolean("rotationEnabled", false),
                rotationPeriodMonths = obj.optInt("rotationPeriodMonths", 6),
                nextRotationDate = if (obj.has("nextRotationDate")) obj.getLong("nextRotationDate") else null,
                isFavorite = obj.optBoolean("isFavorite", false),
                createdAt = obj.getLong("createdAt"),
                lastChanged = obj.getLong("lastChanged"),
                passwordHistoryJson = obj.optString("passwordHistoryJson", null),
                passwordFingerprint = obj.optString("passwordFingerprint", null),
                portableHistory = portableHistory,
                passwordAccessMode = obj.optString("passwordAccessMode", null),
                tagsCsv = obj.optString("tagsCsv", "") //  БЛОК 10: Десериализация (пустая строка для старых бэкапов)
            )
        }
    }
}

data class PortableHistoryItem(
    val plainOldPassword: String,
    val date: Long,
    val type: String,
    val relatedService: String? = null,
    val relatedEntryId: String? = null,
    val hint: String? = null,
    val passwordFingerprint: String? = null
)

data class EncryptedBackup(
    val salt: String,
    val iv: String,
    val ciphertext: String,
    val iterations: Int = 200000
)
