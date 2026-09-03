package com.securevault.utils

import java.security.SecureRandom

object PasswordGenerator {
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SPECIALS = "!@#$%^&*()-_=+[]{}|;:,.<>?"

    private val secureRandom = SecureRandom()

    enum class Strength { WEAK, MEDIUM, STRONG, VERY_STRONG }

    data class GenerationResult(val password: String, val explanation: String = "")
    data class AnchorGenerationResult(val password: String, val explanation: String)
    private data class AnchorBuildResult(val chars: List<Char>, val usedLower: Set<Char>, val explanation: String)

    fun generate(
        length: Int, useLower: Boolean, useUpper: Boolean, useDigits: Boolean, useSpecials: Boolean
    ): Result<GenerationResult> {
        val enabledCount = listOf(useLower, useUpper, useDigits, useSpecials).count { it }
        if (length < enabledCount) return Result.failure(IllegalArgumentException("Длина меньше количества категорий"))
        if (length > 56) return Result.failure(IllegalArgumentException("Максимальная длина без повторов — 56"))

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

        if (useLower && !addRandomChar(LOWER)) return Result.failure(IllegalArgumentException("Нет строчных"))
        if (useUpper && !addRandomChar(UPPER)) return Result.failure(IllegalArgumentException("Нет заглавных"))
        if (useDigits && !addRandomChar(DIGITS)) return Result.failure(IllegalArgumentException("Нет цифр"))
        if (useSpecials && !addRandomChar(SPECIALS)) return Result.failure(IllegalArgumentException("Нет спецсимволов"))

        val allEnabled = buildString {
            if (useLower) append(LOWER)
            if (useUpper) append(UPPER)
            if (useDigits) append(DIGITS)
            if (useSpecials) append(SPECIALS)
        }

        while (selectedChars.size < length) {
            val available = allEnabled.filter { !usedLowerChars.contains(it.lowercaseChar()) }
            if (available.isEmpty()) return Result.failure(IllegalArgumentException("Невозможно создать пароль без повторов"))
            val randomChar = available[secureRandom.nextInt(available.length)]
            usedLowerChars.add(randomChar.lowercaseChar())
            selectedChars.add(randomChar)
        }

        selectedChars.shuffle(secureRandom)
        return Result.success(GenerationResult(selectedChars.joinToString("")))
    }

    //  ИСПРАВЛЕНО: Строгое разделение на две независимые половины с проверкой квот в каждой
    fun generateTwoPart(
        length: Int, useLower: Boolean, useUpper: Boolean, useDigits: Boolean, useSpecials: Boolean
    ): Result<GenerationResult> {
        if (length % 2 != 0) return Result.failure(IllegalArgumentException("Длина должна быть чётной"))
        val half = length / 2

        // Генерируем первую половину
        val part1 = generateStrictHalf(half, useLower, useUpper, useDigits, useSpecials, emptySet()) 
            ?: return Result.failure(IllegalArgumentException("Не удалось сгенерировать первую часть"))

        // Генерируем вторую половину, передавая использованные символы первой, чтобы избежать повторов между частями
        val part2 = generateStrictHalf(half, useLower, useUpper, useDigits, useSpecials, part1.usedLower) 
            ?: return Result.failure(IllegalArgumentException("Не удалось сгенерировать вторую часть без повторов"))

        return Result.success(GenerationResult(part1.password + part2.password))
    }

    private data class HalfResult(val password: String, val usedLower: Set<Char>)

    private fun generateStrictHalf(
        length: Int, useLower: Boolean, useUpper: Boolean, useDigits: Boolean, useSpecials: Boolean, existingUsed: Set<Char>
    ): HalfResult? {
        val selected = mutableListOf<Char>()
        val used = existingUsed.toMutableSet()

        fun addChar(charSet: String): Char? {
            val available = charSet.filter { !used.contains(it.lowercaseChar()) }
            if (available.isEmpty()) return null
            val ch = available[secureRandom.nextInt(available.length)]
            used.add(ch.lowercaseChar())
            selected.add(ch)
            return ch
        }

        // Гарантируем минимумы
        if (useLower && addChar(LOWER) == null) return null
        if (useUpper && addChar(UPPER) == null) return null
        if (useDigits && addChar(DIGITS) == null) return null
        if (useSpecials && addChar(SPECIALS) == null) return null

        val allEnabled = buildString {
            if (useLower) append(LOWER)
            if (useUpper) append(UPPER)
            if (useDigits) append(DIGITS)
            if (useSpecials) append(SPECIALS)
        }

        while (selected.size < length) {
            val available = allEnabled.filter { !used.contains(it.lowercaseChar()) }
            if (available.isEmpty()) return null // Не хватает уникальных символов для этой половины
            val ch = available[secureRandom.nextInt(available.length)]
            used.add(ch.lowercaseChar())
            selected.add(ch)
        }

        // Финальная проверка квот для этой половины
        if (selected.count { it.isUpperCase() } < 2) return null
        if (selected.count { it.isLowerCase() } < 2) return null
        if (selected.count { it.isDigit() } < 2) return null
        if (selected.count { !it.isLetterOrDigit() } < 2) return null

        selected.shuffle(secureRandom)
        return HalfResult(selected.joinToString(""), used)
    }

    fun generateWithAnchor(
        anchorWord: String, totalLength: Int, useLower: Boolean, useUpper: Boolean, useDigits: Boolean, useSpecials: Boolean,
        addService: Boolean = false, serviceName: String = "", addYear: Boolean = false, year: Int = 2026
    ): Result<AnchorGenerationResult> {
        if (anchorWord.isBlank()) return Result.failure(IllegalArgumentException("Якорь пуст"))
        if (anchorWord.length > totalLength) return Result.failure(IllegalArgumentException("Якорь длиннее пароля"))

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

        val allEnabled = buildString {
            if (useLower) append(LOWER)
            if (useUpper) append(UPPER)
            if (useDigits) append(DIGITS)
            if (useSpecials) append(SPECIALS)
        }

        while (resultChars.size < totalLength) {
            val available = allEnabled.filter { !usedLowerChars.contains(it.lowercaseChar()) }
            if (available.isEmpty()) return Result.failure(IllegalArgumentException("Невозможно дополнить якорь без повторов"))
            val randomChar = available[secureRandom.nextInt(available.length)]
            usedLowerChars.add(randomChar.lowercaseChar())
            resultChars.add(randomChar)
        }

        return Result.success(AnchorGenerationResult(resultChars.joinToString(""), explanation))
    }

    //  ИСПРАВЛЕНО: Первая буква якоря ВСЕГДА заглавная, leet применяется только к последующим
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
        var isFirstCharOfAnchor = true

        while (i < word.length) {
            val c = word[i]
            //  Поддержка английских букв: если нет в карте, оставляем как есть
            val translit = translitMap[c.lowercaseChar()] ?: if (c.isLetter()) c.toString() else ""
            
            var j = 0
            while (j < translit.length) {
                val tChar = translit[j]
                val lowerT = tChar.lowercaseChar()
                
                val isCh = (lowerT == 'c' && j + 1 < translit.length && translit[j + 1].lowercaseChar() == 'h')
                val key = if (isCh) "ch" else lowerT.toString()
                val replacement = leetMap[key]

                val finalChar: Char? = if (isFirstCharOfAnchor) {
                    // Первая буква якоря ВСЕГДА заглавная, без leet-замены
                    val upper = tChar.uppercaseChar()
                    if (!usedLower.contains(upper.lowercaseChar())) upper else null
                } else if (replacement != null && !usedLower.contains(replacement.first().lowercaseChar())) {
                    replacement.first()
                } else if (!usedLower.contains(lowerT)) {
                    tChar
                } else {
                    null
                }

                if (finalChar != null) {
                    chars.add(finalChar)
                    usedLower.add(finalChar.lowercaseChar())
                    explanation.append(finalChar)
                    isFirstCharOfAnchor = false // После первого символа флаг сбрасывается
                    if (isCh) j++
                }
                j++
            }
            i++
        }
        return AnchorBuildResult(chars, usedLower, explanation.toString())
    }
}
