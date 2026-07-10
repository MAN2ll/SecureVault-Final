package com.securevault.utils

import java.time.LocalDate
import kotlin.random.Random

object MnemonicPasswordGenerator {

    data class GenerationOptions(
        val phrase: String,
        val serviceName: String,
        val targetLength: Int = 16,
        val includeLeet: Boolean = true,
        val includeServiceCode: Boolean = true,
        val includeRotationCode: Boolean = true,
        val rotationMonth: Int? = null,
        val rotationYear: Int? = null,
        val variantOffset: Int = 0,
        val separator: String = "",
        val enforceUniqueChars: Boolean = true
    )

    data class GenerationResult(
        val password: String,
        val mnemonicHint: String,
        val variantName: String,
        val strength: PasswordGenerator.Strength,
        val part1: String,
        val part2: String,
        val hasUniqueChars: Boolean
    )

    private val uppercaseLetters = ('A'..'Z').toList()
    private val lowercaseLetters = ('a'..'z').toList()
    private val digits = ('0'..'9').toList()
    private val specialChars = listOf('@', '$', '!', '#', '%', '&', '*', '_', '-')

    private val leetMap = mapOf(
        'a' to listOf('4', '@'),
        'e' to listOf('3'),
        'i' to listOf('1', '!'),
        'o' to listOf('0'),
        's' to listOf('5', '$'),
        't' to listOf('7'),
        'b' to listOf('8'),
        'g' to listOf('9'),
        'l' to listOf('1')
    )

    // ✅ ИСПРАВЛЕНИЕ ПУНКТА 1: Case-insensitive проверка
    private fun key(char: Char): Char = char.lowercaseChar()

    private fun isUsed(char: Char, usedChars: Set<Char>): Boolean {
        return key(char) in usedChars
    }

    private fun markUsed(char: Char, usedChars: MutableSet<Char>) {
        usedChars.add(key(char))
    }

    fun generateVariants(options: GenerationOptions, count: Int = 5): List<GenerationResult> {
        val results = mutableListOf<GenerationResult>()

        for (i in 0 until count) {
            val variantOptions = options.copy(variantOffset = options.variantOffset + i)
            try {
                val result = generateSingleVariant(variantOptions)
                if (result != null) {
                    results.add(result)
                }
            } catch (e: Exception) {
                // Пропускаем неудачные варианты
            }
        }

        return results
    }

    private fun generateSingleVariant(options: GenerationOptions): GenerationResult? {
        val words = extractWords(options.phrase)
        if (words.size < 2) return null

        val word1 = words[options.variantOffset % words.size]
        val word2 = words[(options.variantOffset + 1) % words.size]

        val usedChars = mutableSetOf<Char>()
        val part1 = generatePart(word1, usedChars, options, isFirstPart = true) ?: return null
        val part2 = generatePart(word2, usedChars, options, isFirstPart = false) ?: return null

        val password = part1 + options.separator + part2

        val hasUniqueChars = !PasswordValidator.hasDuplicateCharacters(password)

        if (options.enforceUniqueChars && !hasUniqueChars) {
            return null
        }

        val hint = "${words.joinToString(" ").take(30)}..."
        val variantName = "Вариант ${options.variantOffset + 1}"
        val strength = calculateStrength(password)

        return GenerationResult(
            password = password,
            mnemonicHint = hint,
            variantName = variantName,
            strength = strength,
            part1 = part1,
            part2 = part2,
            hasUniqueChars = hasUniqueChars
        )
    }

    private fun extractWords(phrase: String): List<String> {
        return phrase
            .lowercase()
            .replace(Regex("[^а-яёa-z\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .distinct()
    }

    private fun generatePart(
        word: String,
        usedChars: MutableSet<Char>,
        options: GenerationOptions,
        isFirstPart: Boolean
    ): String? {
        val transliterated = transliterate(word)
        val chars = mutableListOf<Char>()

        // Первая буква заглавная
        val firstChar = transliterated.first().uppercaseChar()
        if (!isUsed(firstChar, usedChars)) {
            chars.add(firstChar)
            markUsed(firstChar, usedChars)
        } else {
            val freeUppercase = uppercaseLetters.firstOrNull { !isUsed(it, usedChars) } ?: return null
            chars.add(freeUppercase)
            markUsed(freeUppercase, usedChars)
        }

        // Обрабатываем остальные буквы слова
        for (i in 1 until transliterated.length) {
            val char = transliterated[i]
            if (isUsed(char, usedChars)) {
                if (options.includeLeet && char in leetMap) {
                    val replacements = leetMap[char]!!
                    val replacement = replacements.firstOrNull { !isUsed(it, usedChars) }
                    if (replacement != null) {
                        chars.add(replacement)
                        markUsed(replacement, usedChars)
                        continue
                    }
                }
                continue
            }

            if (options.includeLeet && char in leetMap && Random.nextBoolean()) {
                val replacements = leetMap[char]!!
                val replacement = replacements.firstOrNull { !isUsed(it, usedChars) }
                if (replacement != null) {
                    chars.add(replacement)
                    markUsed(replacement, usedChars)
                    continue
                }
            }

            chars.add(char)
            markUsed(char, usedChars)
        }

        // ✅ Усиление каждой части
        val currentUppercase = chars.count { it.isUpperCase() }
        val currentLowercase = chars.count { it.isLowerCase() }
        val currentDigits = chars.count { it.isDigit() }
        val currentSpecials = chars.count { !it.isLetterOrDigit() }

        // Добавляем недостающие заглавные
        val neededUppercase = maxOf(0, 2 - currentUppercase)
        var addedUppercase = 0
        for (letter in uppercaseLetters.shuffled()) {
            if (addedUppercase >= neededUppercase) break
            if (!isUsed(letter, usedChars)) {
                val insertPos = Random.nextInt(1, chars.size)
                chars.add(insertPos, letter)
                markUsed(letter, usedChars)
                addedUppercase++
            }
        }

        // Добавляем недостающие маленькие
        val neededLowercase = maxOf(0, 2 - currentLowercase)
        var addedLowercase = 0
        for (letter in lowercaseLetters.shuffled()) {
            if (addedLowercase >= neededLowercase) break
            if (!isUsed(letter, usedChars)) {
                val insertPos = Random.nextInt(1, chars.size)
                chars.add(insertPos, letter)
                markUsed(letter, usedChars)
                addedLowercase++
            }
        }

        // Добавляем недостающие цифры
        val neededDigits = maxOf(0, 2 - currentDigits)
        var addedDigits = 0
        for (digit in digits.shuffled()) {
            if (addedDigits >= neededDigits) break
            if (!isUsed(digit, usedChars)) {
                val insertPos = Random.nextInt(1, chars.size)
                chars.add(insertPos, digit)
                markUsed(digit, usedChars)
                addedDigits++
            }
        }

        // Добавляем недостающие спецсимволы
        val neededSpecials = maxOf(0, 2 - currentSpecials)
        var addedSpecials = 0
        for (special in specialChars.shuffled()) {
            if (addedSpecials >= neededSpecials) break
            if (!isUsed(special, usedChars)) {
                val insertPos = Random.nextInt(1, chars.size)
                chars.add(insertPos, special)
                markUsed(special, usedChars)
                addedSpecials++
            }
        }

        // Код сервиса
        if (options.includeServiceCode && options.serviceName.isNotBlank() && isFirstPart) {
            val serviceLetters = transliterate(options.serviceName.lowercase())
                .replace(Regex("[^a-z]"), "")
                .take(2)

            for (letter in serviceLetters) {
                val upper = letter.uppercaseChar()
                if (!isUsed(upper, usedChars)) {
                    chars.add(upper)
                    markUsed(upper, usedChars)
                }
            }
        }

        // Код ротации
        if (options.includeRotationCode && !isFirstPart) {
            val now = LocalDate.now()
            val month = options.rotationMonth ?: now.monthValue
            val year = options.rotationYear ?: (now.year % 100)
            val mm = month.toString().padStart(2, '0')
            val yy = year.toString().padStart(2, '0').takeLast(2)
            val rotationCode = mm + yy

            for (digit in rotationCode) {
                if (!isUsed(digit, usedChars)) {
                    chars.add(digit)
                    markUsed(digit, usedChars)
                }
            }
        }

        return chars.joinToString("")
    }

    private fun transliterate(text: String): String {
        val map = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya"
        )

        return text.map { char ->
            if (char in map) map[char]!! else char.toString()
        }.joinToString("")
    }

    private fun calculateStrength(password: String): PasswordGenerator.Strength {
        val length = password.length
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }

        val score = when {
            length >= 16 && hasUpper && hasLower && hasDigit && hasSpecial -> 4
            length >= 12 && hasUpper && hasLower && hasDigit && hasSpecial -> 3
            length >= 10 && (hasUpper && hasLower) && (hasDigit || hasSpecial) -> 2
            else -> 1
        }

        return when (score) {
            4 -> PasswordGenerator.Strength.VERY_STRONG
            3 -> PasswordGenerator.Strength.STRONG
            2 -> PasswordGenerator.Strength.MEDIUM
            else -> PasswordGenerator.Strength.WEAK
        }
    }
}
