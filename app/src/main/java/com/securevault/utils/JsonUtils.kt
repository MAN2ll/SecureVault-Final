package com.securevault.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object JsonUtils {
    private val gson = Gson()

    fun toJson(value: Any?): String {
        return gson.toJson(value)
    }

    inline fun <reified T> fromJson(json: String): T {
        return gson.fromJson(json, T::class.java)
    }

    fun <T> fromJsonList(json: String, type: Class<T>): List<T> {
        val listType = object : TypeToken<List<T>>() {}.type
        return gson.fromJson(json, listType)
    }
}
