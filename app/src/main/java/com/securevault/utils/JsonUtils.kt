package com.securevault.utils

import com.securevault.data.PasswordHistoryItem
import org.json.JSONArray
import org.json.JSONObject

object JsonUtils {
    fun historyObject(item: PasswordHistoryItem): JSONObject = JSONObject().apply {
        put("encryptedOldPassword", item.encryptedOldPassword ?: JSONObject.NULL)
        put("encryptedNewPassword", item.encryptedNewPassword ?: JSONObject.NULL)
        put("passwordHash", item.passwordHash)
        put("passwordFingerprint", item.passwordFingerprint)
        put("date", item.date); put("type", item.type)
        put("relatedService", item.relatedService ?: JSONObject.NULL)
        put("relatedEntryId", item.relatedEntryId ?: JSONObject.NULL)
        put("hint", item.hint ?: JSONObject.NULL)
    }
    private fun wrap(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is PasswordHistoryItem -> historyObject(value)
        is Iterable<*> -> JSONArray().apply { value.forEach { put(wrap(it)) } }
        is Map<*, *> -> JSONObject().apply { value.forEach { (k, v) -> put(k.toString(), wrap(v)) } }
        else -> value
    }
    fun toJson(value: Any?): String = when (val result = wrap(value)) {
        is String -> JSONObject.quote(result)
        else -> result.toString()
    }
    fun readHistory(json: String?): List<PasswordHistoryItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val raw = array.get(i)
                val obj = when (raw) {
                    is JSONObject -> raw
                    is String -> if (raw.startsWith("PasswordHistoryItem(")) {
                        val fields = Regex("(?:^|, )(encryptedOldPassword|encryptedNewPassword|passwordHash|passwordFingerprint|date|type|relatedService|relatedEntryId|hint)=")
                        val body = raw.removePrefix("PasswordHistoryItem(").removeSuffix(")")
                        val matches = fields.findAll(body).toList()
                        JSONObject().apply {
                            matches.forEachIndexed { n, match ->
                                val value = body.substring(match.range.last + 1,
                                    matches.getOrNull(n + 1)?.range?.first ?: body.length)
                                put(match.groupValues[1], if (value == "null") JSONObject.NULL else value)
                            }
                        }
                    } else JSONObject(raw)
                    else -> error("Неизвестный формат истории")
                }
                fun optional(key: String) = obj.optString(key).takeIf { it.isNotEmpty() && it != "null" }
                PasswordHistoryItem(
                    encryptedOldPassword = optional("encryptedOldPassword"),
                    encryptedNewPassword = optional("encryptedNewPassword"),
                    passwordHash = optional("passwordHash").orEmpty(),
                    passwordFingerprint = optional("passwordFingerprint").orEmpty(),
                    date = obj.optLong("date"), type = optional("type") ?: "unknown",
                    relatedService = optional("relatedService"), relatedEntryId = optional("relatedEntryId"),
                    hint = optional("hint")
                )
            }
        } catch (e: Exception) {
            if (json.trimStart().startsWith("[")) throw IllegalArgumentException("История повреждена; сохранение отменено", e)
            json.split('|').filter { it.isNotBlank() }.map { row ->
                val parts = row.split(':', limit = 3)
                require(parts.size >= 2 && parts[1].toLongOrNull() != null) { "История повреждена" }
                PasswordHistoryItem(passwordHash = parts[0], passwordFingerprint = parts[0],
                    date = parts[1].toLong(), type = parts.getOrElse(2) { "unknown" })
            }
        }
    }
    inline fun <reified T> fromJson(json: String): T {
        @Suppress("UNCHECKED_CAST")
        return when (T::class) {
            String::class -> JSONArray("[$json]").getString(0) as T
            Int::class -> json.toInt() as T
            Long::class -> json.toLong() as T
            Boolean::class -> json.toBooleanStrict() as T
            else -> error("Неподдерживаемый тип JSON")
        }
    }
}
