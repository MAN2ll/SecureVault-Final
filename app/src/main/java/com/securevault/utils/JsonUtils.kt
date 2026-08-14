package com.securevault.utils

import org.json.JSONArray
import org.json.JSONObject

object JsonUtils {
    fun toJson(value: Any?): String {
        if (value == null) return "null"
        if (value is String) return JSONObject.wrap(value).toString()
        if (value is Number || value is Boolean) return value.toString()
        if (value is List<*>) {
            val arr = JSONArray()
            value.forEach { arr.put(toJson(it)) }
            return arr.toString()
        }
        if (value is Map<*, *>) {
            val obj = JSONObject()
            value.forEach { (k, v) -> obj.put(k.toString(), toJson(v)) }
            return obj.toString()
        }
        return value.toString()
    }

    inline fun <reified T> fromJson(json: String): T {
        // Простая реализация для базовых типов
        @Suppress("UNCHECKED_CAST")
        return when (T::class) {
            String::class -> json.trim('"') as T
            Int::class -> json.toInt() as T
            Long::class -> json.toLong() as T
            Boolean::class -> json.toBoolean() as T
            else -> json as T
        }
    }
}
