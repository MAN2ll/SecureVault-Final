package com.securevault.utils

import java.security.SecureRandom

object PasswordGenerator {
    // ДОБАВЛЕНО: enum Strength для совместимости с UI
    enum class Strength { WEAK, MEDIUM, STRONG, VERY_STRONG }

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SPECIALS = "!@#$%^&*()-_=+[]{}|;:,.<>?"

    private val secureRandom = SecureRandom()

    data class GenerationResult(
        val password: String,
        val explanation: String = ""
    )

    fun generate(
        length: Int,
        useLower: Boolean,
        useUpper: Boolean,
        useDigits: Boolean,
        useSpecials: Boolean
    ): Result<GenerationResult> {
        val enabledCount = listOf(useLower, useUpper, useDigits, useSpecials).count { it }
        
        if (length < enabledCount) {
            return Result.failure(IllegalArgumentException("Длина пароля не может быть меньше количества выбранных категорий"))
        }
        
        if (length > 56) {
            return Result.failure(IllegalArgumentException("Максимальная длина без повторов — 56 символов"))
        }

        val selectedChars = mutableListOf<Char>()
        val usedLowerChars = mutableSetOf<Char>()

        fun addRandomChar(charSet: String): Boolean {
            val available = charSet.filter { !usedLowerChars.contains(it.lowercaseChar()) }
            if (available.isEmpty()) return false
            
            val randomChar = available[secureRandom.nextInt(available.length)]
            usedLowerChars.add(randomChar.lowercaseChar())
            selectedChars.add(randomChar)
            return true
        }

        if (useLower && !addRandomChar(LOWER)) return Result.failure(IllegalArgumentException("Недостаточно уникальных строчных букв"))
        if (useUpper && !addRandomChar(UPPER)) return Result.failure(IllegalArgumentException("Недостаточно уникальных заглавных букв"))
        if (useDigits && !addRandomChar(DIGITS)) return Result.failure(IllegalArgumentException("Недостаточно уникальных цифр"))
        if (useSpecials && !addRandomChar(SPECIALS)) return Result.failure(IllegalArgumentException("Недостаточно уникальных спецсимволов"))

        val allEnabledChars = buildString {
            if (useLower) append(LOWER)
            if (useUpper) append(UPPER)
            if (useDigits) append(DIGITS)
            if (useSpecials) append(SPECIALS)
        }

        while (selectedChars.size < length) {
            val available = allEnabledChars.filter { !usedLowerChars.contains(it.lowercaseChar()) }
            if (available.isEmpty()) {
                return Result.failure(IllegalArgumentException("Невозможно создать пароль заданной длины без повторов"))
            }
            val randomChar = available[secureRandom.nextInt(available.length)]
            usedLowerChars.add(randomChar.lowercaseChar())
            selectedChars.add(randomChar)
        }

        selectedChars.shuffle(secureRandom)
        return Result.success(GenerationResult(selectedChars.joinToString("")))
    }

    fun generateTwoPart(
        length: Int,
        useLower: Boolean,
        useUpper: Boolean,
        useDigits: Boolean,
        useSpecials: Boolean
    ): Result<GenerationResult> {
        if (length % 2 != 0) {
            return Result.failure(IllegalArgumentException("Длина для двух частей должна быть чётной"))
        }
        return generateGlobalUnique(length, useLower, useUpper, useDigits, useSpecials)
    }

    private fun generateGlobalUnique(
        length: Int,
        useLower: Boolean,
        useUpper: Boolean,
        useDigits: Boolean,
        useSpecials: Boolean
    ): Result<GenerationResult> {
        val selectedChars = mutableListOf<Char>()
        val usedLowerChars = mutableSetOf<Char>()

        fun addRandomChar(charSet: String): Boolean {
            val available = charSet.filter { !usedLowerChars.contains(it.lowercaseChar()) }
            if (available.isEmpty()) return false
            val randomChar = available[secureRandom.nextInt(available.length)]
            usedLowerChars.add(randomChar.lowercaseChar())
            selectedChars.add(randomChar)
            return true
        }

        val enabledCount = listOf(useLower, useUpper, useDigits, useSpecials).count { it }
        if (length < enabledCount || length > 56) return Result.failure(IllegalArgumentException("Недопустимая длина"))

        if (useLower && !addRandomChar(LOWER)) return Result.failure(IllegalArgumentException("Ошибка генерации"))
        if (useUpper && !addRandomChar(UPPER)) return Result.failure(IllegalArgumentException("Ошибка генерации"))
        if (useDigits && !addRandomChar(DIGITS)) return Result.failure(IllegalArgumentException("Ошибка генерации"))
        if (useSpecials && !addRandomChar(SPECIALS)) return Result.failure(IllegalArgumentException("Ошибка генерации"))

        val allEnabledChars = buildString {
            if (useLower) append(LOWER)
            if (useUpper) append(UPPER)
            if (useDigits) append(DIGITS)
            if (useSpecials) append(SPECIALS)
        }

        while (selectedChars.size < length) {
            val available = allEnabledChars.filter { !usedLowerChars.contains(it.lowercaseChar()) }
            if (available.isEmpty()) return Result.failure(IllegalArgumentException("Невозможно создать пароль без повторов"))
            val randomChar = available[secureRandom.nextInt(available.length)]
            usedLowerChars.add(randomChar.lowercaseChar())
            selectedChars.add(randomChar)
        }

        selectedChars.shuffle(secureRandom)
        return Result.success(GenerationResult(selectedChars.joinToString("")))
    }

    data class AnchorGenerationResult(val password: String, val explanation: String)

    fun generateWithAnchor(
        anchorWord: String,
        totalLength: Int,
        useLower: Boolean,
        useUpper: Boolean,
        useDigits: Boolean,
        useSpecials: Boolean,
        addService: Boolean = false,
        serviceName: String = "",
        addYear: Boolean = false,
        year: Int = 2026
    ): Result<AnchorGenerationResult> {
        if (anchorWord.isBlank()) {
            return Result.failure(IllegalArgumentException("Якорное слово не может быть пустым"))
        }
        if (anchorWord.length > totalLength) {
            return Result.failure(IllegalArgumentException("Якорное слово длиннее заданной длины пароля"))
        }

        val usedLowerChars = mutableSetOf<Char>()
        val resultChars = mutableListOf<Char>()

        fun addRandomChar(charSet: String): Boolean {
            val available = charSet.filter { !usedLowerChars.contains(it.lowercaseChar()) }
            if (available.isEmpty()) return false
            val randomChar = available[secureRandom.nextInt(available.length)]
            usedLowerChars.add(randomChar.lowercaseChar())
            resultChars.add(randomChar)
            return true
        }

        val anchorResult = buildAnchor(anchorWord, usedLowerChars)
        resultChars.addAll(anchorResult.chars)
        usedLowerChars.addAll(anchorResult.usedLower)

        var explanation = "Якорь: ${anchorResult.explanation}\n"
        
        if (addService && serviceName.isNotEmpty()) {
            val serviceChar = serviceName.first().uppercaseChar()
            if (!usedLowerChars.contains(serviceChar.lowercaseChar())) {
                resultChars.add(0, serviceChar)
                usedLowerChars.add(serviceChar.lowercaseChar())
                explanation += "Сервис: $serviceChar\n"
            }
        }
        if (addYear) {
            val yearStr = year.toString().takeLast(2)
            if (yearStr.length == 2 && yearStr[0] != yearStr[1]) {
                if (!usedLowerChars.contains(yearStr[0].lowercaseChar()) && !usedLowerChars.contains(yearStr[1].lowercaseChar())) {
                    resultChars.add(yearStr[0])
                    resultChars.add(yearStr[1])
                    usedLowerChars.add(yearStr[0].lowercaseChar())
                    usedLowerChars.add(yearStr[1].lowercaseChar())
                    explanation += "Год: $yearStr\n"
                }
            }
        }

        val allEnabledChars = buildString {
            if (useLower) append(LOWER)
            if (useUpper) append(UPPER)
            if (useDigits) append(DIGITS)
            if (useSpecials) append(SPECIALS)
        }

        while (resultChars.size < totalLength) {
            val available = allEnabledChars.filter { !usedLowerChars.contains(it.lowercaseChar()) }
            if (available.isEmpty()) {
                return Result.failure(IllegalArgumentException("Невозможно дополнить якорь без повторов"))
            }
            val randomChar = available[secureRandom.nextInt(available.length)]
            usedLowerChars.add(randomChar.lowercaseChar())
            resultChars.add(randomChar)
        }

        return Result.success(AnchorGenerationResult(resultChars.joinToString(""), explanation))
    }

    private data class AnchorBuildResult(val chars: List<Char>, val usedLower: Set<Char>, val explanation: String)

    private fun buildAnchor(word: String, currentUsed: Set<Char>): AnchorBuildResult {
        val translitMap = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
            'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
            'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
            'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "",
            'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya"
        )
        val leetMap = mapOf("a" to "@", "o" to "0", "t" to "7", "ch" to "4", "s" to "$", "i" to "1", "b" to "6", "l" to "!")

        val chars = mutableListOf<Char>()
        val usedLower = currentUsed.toMutableSet()
        val explanation = StringBuilder()

        var i = 0
        while (i < word.length) {
            val c = word[i]
            val translit = translitMap[c.lowercaseChar()] ?: c.toString()
            
            var j = 0
            while (j < translit.length) {
                val tChar = translit[j]
                val lowerT = tChar.lowercaseChar()
                
                val isCh = (lowerT == 'c' && j + 1 < translit.length && translit[j + 1].lowercaseChar() == 'h')
                val key = if (isCh) "ch" else lowerT.toString()
                val replacement = leetMap[key]

                val finalChar = if (replacement != null && !usedLower.contains(replacement.first().lowercaseChar())) {
                    replacement.first()
                } else if (!usedLower.contains(lowerT)) {
                    if (chars.isEmpty()) tChar.uppercaseChar() else tChar
                } else {
                    null
                }

                if (finalChar != null) {
                    chars.add(finalChar)
                    usedLower.add(finalChar.lowercaseChar())
                    explanation.append(finalChar)
                    if (isCh) j++
                }
                j++
            }
            i++
        }
        return AnchorBuildResult(chars, usedLower, explanation.toString())
    }
}
