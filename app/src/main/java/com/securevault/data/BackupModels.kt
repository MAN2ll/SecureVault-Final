package com.securevault.data

import org.json.JSONObject

data class BackupEntry(
    val id: String,
    val service: String,
    val username: String,
    val encryptedPassword: String,
    val profileId: Int,
    val url: String? = null,
    val notes: String? = null,
    val isFavorite: Boolean = false,
    val textHint: String? = null,
    val rotationEnabled: Boolean = false,
    val rotationPeriodMonths: Int = 6,
    val nextRotationDate: Long? = null,
    val createdAt: Long,
    val lastChanged: Long,
    val passwordHistoryJson: String? = null,
    val generationType: String = "random",
    val passwordFingerprint: String? = null,
    val mnemonicPhraseHint: String? = null,
    val mnemonicOptionsJson: String? = null,
    val passwordAccessMode: String,
    val tagsCsv: String = "" //  БЛОК 10: Добавлено поле тегов
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("service", service)
        obj.put("username", username)
        obj.put("encryptedPassword", encryptedPassword)
        obj.put("profileId", profileId)
        if (url != null) obj.put("url", url)
        if (notes != null) obj.put("notes", notes)
        obj.put("isFavorite", isFavorite)
        if (textHint != null) obj.put("textHint", textHint)
        obj.put("rotationEnabled", rotationEnabled)
        obj.put("rotationPeriodMonths", rotationPeriodMonths)
        if (nextRotationDate != null) obj.put("nextRotationDate", nextRotationDate)
        obj.put("createdAt", createdAt)
        obj.put("lastChanged", lastChanged)
        if (passwordHistoryJson != null) obj.put("passwordHistoryJson", passwordHistoryJson)
        obj.put("generationType", generationType)
        if (passwordFingerprint != null) obj.put("passwordFingerprint", passwordFingerprint)
        if (mnemonicPhraseHint != null) obj.put("mnemonicPhraseHint", mnemonicPhraseHint)
        if (mnemonicOptionsJson != null) obj.put("mnemonicOptionsJson", mnemonicOptionsJson)
        obj.put("passwordAccessMode", passwordAccessMode)
        obj.put("tagsCsv", tagsCsv) //  БЛОК 10: Сериализация тегов
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): BackupEntry {
            return BackupEntry(
                id = obj.getString("id"),
                service = obj.getString("service"),
                username = obj.getString("username"),
                encryptedPassword = obj.getString("encryptedPassword"),
                profileId = obj.getInt("profileId"),
                url = obj.optString("url", null),
                notes = obj.optString("notes", null),
                isFavorite = obj.optBoolean("isFavorite", false),
                textHint = obj.optString("textHint", null),
                rotationEnabled = obj.optBoolean("rotationEnabled", false),
                rotationPeriodMonths = obj.optInt("rotationPeriodMonths", 6),
                nextRotationDate = if (obj.has("nextRotationDate")) obj.getLong("nextRotationDate") else null,
                createdAt = obj.getLong("createdAt"),
                lastChanged = obj.getLong("lastChanged"),
                passwordHistoryJson = obj.optString("passwordHistoryJson", null),
                generationType = obj.optString("generationType", "random"),
                passwordFingerprint = obj.optString("passwordFingerprint", null),
                mnemonicPhraseHint = obj.optString("mnemonicPhraseHint", null),
                mnemonicOptionsJson = obj.optString("mnemonicOptionsJson", null),
                passwordAccessMode = obj.optString("passwordAccessMode", "inherit"),
                tagsCsv = obj.optString("tagsCsv", "") //  БЛОК 10: Десериализация (пустая строка для старых бэкапов)
            )
        }
    }
}
