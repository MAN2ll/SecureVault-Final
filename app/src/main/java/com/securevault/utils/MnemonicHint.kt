package com.securevault.utils

import org.json.JSONObject

/** Card hints are user text, never the generator's password derivation report. */
object MnemonicHint {
    fun normalize(value: String?): String? = value
        ?.lineSequence()
        ?.map { it.trim().replace(Regex("[\\t ]+"), " ") }
        ?.filter { it.isNotBlank() }
        ?.joinToString("\n")
        ?.takeIf { it.isNotBlank() }

    fun fromPhrases(first: String, second: String? = null): String {
        fun phrase(value: String) = value.trim().replace(Regex("\\s+"), " ")
        return if (second == null) phrase(first)
        else "Часть 1: ${phrase(first)}\nЧасть 2: ${phrase(second)}"
    }

    fun phraseForQr(optionsJson: String?, storedHint: String?): String? {
        val options = runCatching { JSONObject(optionsJson.orEmpty()) }.getOrNull()
        if (options?.optBoolean("phraseStored", false) == true) {
            val first = options.optString("phrase").takeIf { it.isNotBlank() }
            if (first != null) {
                val second = options.optString("phrase2").takeIf { it.isNotBlank() }
                return normalize(fromPhrases(first, second))
            }
        }
        return forDisplay(storedHint)
    }

    fun forDisplay(storedHint: String?): String? {
        val hint = normalize(storedHint) ?: return null
        // Old AMPG records saved this placeholder instead of the user's phrase.
        val legacyPlaceholder = Regex(
            "^(?:AMPG\\s*\\d+\\s*:\\s*)?исходная фраза не сохранена[.!]?$",
            RegexOption.IGNORE_CASE
        )
        return hint.takeUnless { legacyPlaceholder.matches(it) }
    }
}
