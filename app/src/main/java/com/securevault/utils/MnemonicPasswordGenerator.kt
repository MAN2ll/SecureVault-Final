package com.securevault.utils

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MnemonicPasswordGenerator {

    companion object {
        // Транслитерация: Русские буквы -> Латинские (читаемые)
        private val CYRILLIC_TO_LATIN = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya"
        )

        // Точечная замена для запоминания (Leet Speak): только похожие символы
        private val LEET_REPLACE = mapOf(
            'a' to "@", 'A' to "@",
            'e' to "3", 'E' to "3",
            'o' to "0", 'O' to "0",
            's' to "$", 'S' to "$",
            'i' to "!", 'I' to "!",
            'l' to "1", 'L' to "1"
        )

        private const val SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    }

    data class GenerationOptions(
        val phrase: String,
        val emoji: String? = null,
        val targetLength: Int = 12,
        val includeUppercase: Boolean = true,
        val includeDigits: Boolean = true,
        val includeSpecial: Boolean = false, // По умолчанию выключаем сложный спецсимволы
        val useLeetSpeak: Boolean = false,   // Новая опция: заменять a->@, e->3 и т.д.
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
        // 1. Транслитерация фразы (всегда)
        var password = transliterate(options.phrase)
        
        // 2. Если включен LeetSpeak, заменяем некоторые буквы на символы
        if (options.useLeetSpeak) {
            password = applyLeetSpeak(password)
        }
        
        // 3. Приводим к нужному регистру (если нужно)
        if (options.includeUppercase) {
            password = capitalizeWords(password)
        } else {
            password = password.lowercase()
        }
        
        // 4. Добавляем цифры, если включено и их нет
        if (options.includeDigits && password.none { it.isDigit() }) {
            // Добавляем случайную цифру в конец или начало, чтобы не ломать слово сильно
            password += (0..9).random().toString()
        }
        
        // 5. Добавляем спецсимволы из набора, если включено
        if (options.includeSpecial && password.none { it in SPECIAL_CHARS }) {
             // Добавляем один случайный спецсимвол в конец
             password += SPECIAL_CHARS.random()
        }

        // 6. Ротация (дата)
        var rotationSuffix: String? = null
        if (options.enableRotation && options.rotationDate != null) {
            rotationSuffix = buildRotationSuffix(options.rotationDate)
            password += rotationSuffix
        }
        
        // 7. Корректировка длины
        password = adjustLength(password, options.targetLength)
        
        // 8. Проверка уникальности
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

    private fun applyLeetSpeak(text: String): String {
        return text.map { char ->
            LEET_REPLACE[char] ?: char.toString()
        }.joinToString("")
    }

    private fun capitalizeWords(text: String): String {
        return text.split(" ").joinToString(" ") { word ->
            if (word.isEmpty()) word else word.replaceFirstChar { it.uppercaseChar() }
        }
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
                // Добиваем длину простыми символами, чтобы не усложнять запоминание
                val paddingChars = "abcdefghijklmnopqrstuvwxyz0123456789"
                while (result.length < targetLength) {
                    result += paddingChars.random()
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
        // Простая пертурбация для уникальности: меняем одну букву на случайную
        if (password.isEmpty()) return password
        val chars = password.toCharArray()
        val idx = abs(seed) % chars.size
        chars[idx] = if (chars[idx].isLetter()) {
            if (chars[idx].isUpperCase()) 'A' + (seed % 26) else 'a' + (seed % 26)
        } else {
            '0' + (seed % 10)
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
            else -> ValidationResult.Success
        }
    }

    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}
