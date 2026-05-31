package com.securevault.utils

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MnemonicPasswordGenerator {

    companion object {
        // Исправлено: используем String (двойные кавычки) вместо Char
        private val CYRILLIC_TO_LATIN = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya"
        )

        private val RUSSIAN_TO_SYMBOL = mapOf(
            'а' to "@", 'с' to "$", 'о' to "0", 'е' to "3", 'к' to "|<",
            'р' to "?", 'х' to "#", 'в' to "\\/", 'м' to "|\\/|", 'т' to "+"
        )

        private val LATIN_TO_SYMBOL = mapOf(
            'a' to "@", 's' to "5", 'i' to "!", 'e' to "3", 'o' to "0",
            't' to "7", 'b' to "8", 'g' to "9", 'l' to "1", 'z' to "2"
        )

        private const val SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    }

    data class GenerationOptions(
        val phrase: String,
        val emoji: String? = null,
        val targetLength: Int = 12,
        val includeUppercase: Boolean = true,
        val includeDigits: Boolean = true,
        val includeSpecial: Boolean = true,
        val useRussianSymbolReplacement: Boolean = false,
        val useLatinSymbolReplacement: Boolean = false,
        val enableRotation: Boolean = false,
        val rotationPeriodMonths: Int = 6,
        val rotationDate: Long? = null,
        val previousHashes: List<String> = emptyList(),
        val maxAttempts: Int = 100
    )

    data class GenerationResult(
        val password: String,
        val mnemonicHint: String,
        val emoji: String?,
        val rotationSuffix: String?,
        val attempts: Int,
        val isUnique: Boolean
    )

    fun generate(options: GenerationOptions): GenerationResult {
        val base = transliterate(options.phrase)
        var password = applyReplacements(base, options)
        password = ensureRequirements(password, options)
        
        var rotationSuffix: String? = null
        if (options.enableRotation && options.rotationDate != null) {
            rotationSuffix = buildRotationSuffix(options.rotationDate)
            password += rotationSuffix
        }
        
        password = adjustLength(password, options.targetLength)
        
        var attempts = 0
        var uniquePassword = password
        while (attempts < options.maxAttempts) {
            if (isPasswordUnique(uniquePassword, options.previousHashes)) {
                break
            }
            uniquePassword = perturbPassword(uniquePassword, attempts)
            attempts++
        }
        
        return GenerationResult(
            password = uniquePassword,
            mnemonicHint = buildMnemonicHint(options.phrase, options.emoji),
            emoji = if (options.emoji != null && options.emoji.isNotEmpty()) options.emoji else null,
            rotationSuffix = rotationSuffix,
            attempts = attempts,
            isUnique = attempts < options.maxAttempts
        )
    }

    private fun transliterate(text: String): String {
        return text.lowercase().flatMap { char ->
            CYRILLIC_TO_LATIN[char]?.toList() ?: if (char.isLetterOrDigit()) listOf(char) else emptyList()
        }.joinToString("")
    }

    private fun applyReplacements(text: String, options: GenerationOptions): String {
        var result = text
        if (options.useRussianSymbolReplacement) {
            result = result.flatMap { char ->
                RUSSIAN_TO_SYMBOL[char]?.toList() ?: listOf(char)
            }.joinToString("")
        }
        if (options.useLatinSymbolReplacement) {
            result = result.flatMap { char ->
                LATIN_TO_SYMBOL[char.lowercaseChar()]?.toList() ?: listOf(char)
            }.joinToString("")
        }
        return result
    }

    private fun ensureRequirements(password: String, options: GenerationOptions): String {
        var result = password
        val chars = result.toMutableList()
        
        if (options.includeUppercase && result.none { it.isUpperCase() }) {
            val idx = chars.indexOfFirst { it.isLowerCase() }.takeIf { it >= 0 } ?: 0
            if (idx < chars.size) chars[idx] = chars[idx].uppercaseChar()
        }
        if (options.includeDigits && result.none { it.isDigit() }) {
            chars.add((0..9).random().toString().first())
        }
        if (options.includeSpecial && result.none { it in SPECIAL_CHARS }) {
            chars.add(SPECIAL_CHARS.random())
        }
        
        return chars.joinToString("")
    }

    private fun buildRotationSuffix(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMyy", Locale.US)
        return "-${format.format(date)}"
    }

    private fun adjustLength(password: String, targetLength: Int): String {
        return when {
            password.length >= targetLength -> password.take(targetLength)
            else -> {
                var result = password
                val pool = (if (password.any { it.isUpperCase() }) "ABCDEFGHIJKLMNOPQRSTUVWXYZ" else "") +
                          (if (password.any { it.isDigit() }) "0123456789" else "") +
                          (if (password.any { it in SPECIAL_CHARS }) SPECIAL_CHARS else "abcdefghijklmnopqrstuvwxyz")
                while (result.length < targetLength && pool.isNotEmpty()) {
                    result += pool.random()
                }
                result
            }
        }
    }

    private fun isPasswordUnique(password: String, previousHashes: List<String>): Boolean {
        val currentHash = computeHash(password)
        return currentHash !in previousHashes
    }

    private fun computeHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun perturbPassword(password: String, seed: Int): String {
        val chars = password.toCharArray()
        if (chars.isEmpty()) return password
        val offset = abs(seed * 31) % chars.size
        for (i in chars.indices) {
            val idx = (i + offset) % chars.size
            chars[idx] = when {
                chars[idx].isLowerCase() -> chars[idx].uppercaseChar()
                chars[idx].isUpperCase() -> chars[idx].lowercaseChar()
                chars[idx].isDigit() -> ((chars[idx].digitToInt() + 1) % 10).toString().first()
                else -> SPECIAL_CHARS.random()
            }
        }
        return chars.joinToString("")
    }

    private fun buildMnemonicHint(phrase: String, emoji: String?): String {
        val cleanPhrase = phrase.trim().take(30)
        return if (emoji != null && emoji.isNotEmpty()) {
            "$cleanPhrase $emoji"
        } else {
            cleanPhrase
        }
    }

    fun validatePhrase(phrase: String): ValidationResult {
        return when {
            phrase.isBlank() -> ValidationResult.Error("Фраза не может быть пустой")
            phrase.length < 3 -> ValidationResult.Error("Минимальная длина фразы — 3 символа")
            phrase.length > 50 -> ValidationResult.Error("Максимальная длина фразы — 50 символов")
            !phrase.any { it in CYRILLIC_TO_LATIN.keys } -> 
                ValidationResult.Error("Фраза должна содержать хотя бы одну русскую букву")
            else -> ValidationResult.Success
        }
    }

    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}
