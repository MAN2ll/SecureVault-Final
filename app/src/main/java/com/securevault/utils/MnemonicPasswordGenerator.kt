package com.securevault.utils

import java.time.LocalDate

object MnemonicPasswordGenerator {

    enum class SplitMode {
        SINGLE_USER,
        TWO_USERS
    }

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
        val enforceUniqueChars: Boolean = true,
        val splitMode: SplitMode = SplitMode.SINGLE_USER
    )

    data class GenerationResult(
        val password: String,
        val mnemonicHint: String,
        val variantName: String,
        val strength: PasswordGenerator.Strength,
        val part1: String?,
        val part2: String?,
        val hasUniqueChars: Boolean,
        val splitMode: SplitMode
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

    //  Case-insensitive проверка
    private fun key(char: Char): Char = char.lowercaseChar()

    private fun isUsed(char: Char, usedChars: Set<Char>): Boolean {
        return key(char) in usedChars
    }

    private fun markUsed(char: Char, usedChars: MutableSet<Char>) {
        usedChars.add(key(char))
    }

    //  Детерминированный выбор символа
    private fun pickDeterministic(
        candidates: List<Char>,
        usedChars: Set<Char>,
        salt: Int
    ): Char? {
        val available = candidates.filter { !isUsed(it, usedChars) }
        if (available.isEmpty()) return null
        val index = kotlin.math.abs(salt) % available.size
        return available[index]
    }

    //  Детерминированное решение о leet-замене
    private fun shouldApplyLeet(variantOffset: Int, charIndex: Int, partIndex: Int): Boolean {
        return (variantOffset + charIndex + partIndex) % 2 == 0
    }

    fun generateVariants(options: GenerationOptions, count: Int = 5): List<GenerationResult> {
        val results = mutableListOf<GenerationResult>()

        //  Для TWO_USERS длина только чётная 16/18/20
        val effectiveOptions = if (options.splitMode == SplitMode.TWO_USERS) {
            val evenLength = when {
                options.targetLength <= 16 -> 16
                options.targetLength <= 18 -> 18
                else -> 20
            }
            options.copy(targetLength = evenLength, separator = "")
        } else {
            options
        }

        for (i in 0 until count) {
            val variantOptions = effectiveOptions.copy(variantOffset = effectiveOptions.variantOffset + i)
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
        if (words.isEmpty()) return null

        return when (options.splitMode) {
            SplitMode.SINGLE_USER -> generateSingleUserPassword(options, words)
            SplitMode.TWO_USERS -> generateTwoUsersPassword(options, words)
        }
    }

    // SINGLE_USER с резервированием места
    private fun generateSingleUserPassword(
        options: GenerationOptions,
        words: List<String>
    ): GenerationResult? {
        val usedChars = mutableSetOf<Char>()
        val chars = mutableListOf<Char>()

        // 1. Сначала формируем обязательные добавки
        val serviceCode = if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            transliterate(options.serviceName.lowercase())
                .replace(Regex("[^a-z]"), "")
                .take(2)
        } else ""

        val rotationCode = if (options.includeRotationCode) {
            val now = LocalDate.now()
            val month = options.rotationMonth ?: now.monthValue
            val year = options.rotationYear ?: (now.year % 100)
            val mm = month.toString().padStart(2, '0')
            val yy = year.toString().padStart(2, '0').takeLast(2)
            mm + yy
        } else ""

        // 2. Резервируем место
        val reservedForService = serviceCode.length
        val reservedForRotation = rotationCode.length
        val wordLimit = options.targetLength - reservedForService - reservedForRotation

        if (wordLimit < 3) return null // Недостаточно места для слова

        // 3. Строим основу из слова
        val word = words[options.variantOffset % words.size]
        val transliterated = transliterate(word)

        // Первая буква заглавная
        val firstChar = transliterated.first().uppercaseChar()
        if (!isUsed(firstChar, usedChars)) {
            chars.add(firstChar)
            markUsed(firstChar, usedChars)
        } else {
            val freeUppercase = pickDeterministic(uppercaseLetters, usedChars, options.variantOffset) ?: return null
            chars.add(freeUppercase)
            markUsed(freeUppercase, usedChars)
        }

        // Обрабатываем остальные буквы слова (с ограничением по длине)
        for (i in 1 until transliterated.length) {
            if (chars.size >= wordLimit) break
            val char = transliterated[i]
            
            if (isUsed(char, usedChars)) {
                if (options.includeLeet && char in leetMap) {
                    val replacements = leetMap[char]!!
                    val replacement = pickDeterministic(replacements, usedChars, options.variantOffset + i)
                    if (replacement != null) {
                        chars.add(replacement)
                        markUsed(replacement, usedChars)
                    }
                }
                continue
            }

            //  Детерминированное решение о leet-замене
            if (options.includeLeet && char in leetMap && shouldApplyLeet(options.variantOffset, i, 0)) {
                val replacements = leetMap[char]!!
                val replacement = pickDeterministic(replacements, usedChars, options.variantOffset + i)
                if (replacement != null) {
                    chars.add(replacement)
                    markUsed(replacement, usedChars)
                    continue
                }
            }

            chars.add(char)
            markUsed(char, usedChars)
        }

        // 4. Вставляем сервисный код
        for ((index, letter) in serviceCode.withIndex()) {
            if (chars.size >= options.targetLength) break
            val upper = letter.uppercaseChar()
            if (!isUsed(upper, usedChars)) {
                chars.add(upper)
                markUsed(upper, usedChars)
            } else {
                // Подбираем замену детерминированно
                val replacement = pickDeterministic(uppercaseLetters, usedChars, options.variantOffset + 100 + index)
                if (replacement != null) {
                    chars.add(replacement)
                    markUsed(replacement, usedChars)
                }
            }
        }

        // 5. Вставляем ротационный код
        for ((index, digit) in rotationCode.withIndex()) {
            if (chars.size >= options.targetLength) break
            if (!isUsed(digit, usedChars)) {
                chars.add(digit)
                markUsed(digit, usedChars)
            } else {
                // Подбираем замену детерминированно
                val replacement = pickDeterministic(digits, usedChars, options.variantOffset + 200 + index)
                if (replacement != null) {
                    chars.add(replacement)
                    markUsed(replacement, usedChars)
                }
            }
        }

        // 6. Добиваем свободными символами
        var saltCounter = 300
        while (chars.size < options.targetLength) {
            val allChars = uppercaseLetters + lowercaseLetters + digits + specialChars
            val randomChar = pickDeterministic(allChars, usedChars, options.variantOffset + saltCounter) ?: break
            chars.add(randomChar)
            markUsed(randomChar, usedChars)
            saltCounter++
        }

        // 7. Финальная проверка
        val finalPassword = chars.take(options.targetLength).joinToString("")
        val hasUniqueChars = !PasswordValidator.hasDuplicateCharacters(finalPassword)

        if (options.enforceUniqueChars && !hasUniqueChars) {
            return null
        }

        val hint = if (options.phrase.isNotBlank()) options.phrase.take(30) else ""
        val variantName = "Вариант ${options.variantOffset + 1}"
        val strength = calculateStrength(finalPassword)

        return GenerationResult(
            password = finalPassword,
            mnemonicHint = hint,
            variantName = variantName,
            strength = strength,
            part1 = null,
            part2 = null,
            hasUniqueChars = hasUniqueChars,
            splitMode = SplitMode.SINGLE_USER
        )
    }

    //  TWO_USERS с резервированием места
    private fun generateTwoUsersPassword(
        options: GenerationOptions,
        words: List<String>
    ): GenerationResult? {
        if (words.size < 2) return null

        val word1 = words[options.variantOffset % words.size]
        val word2 = words[(options.variantOffset + 1) % words.size]

        val usedChars = mutableSetOf<Char>()

        val halfLength = options.targetLength / 2

        // Генерируем две части с резервированием
        val part1 = generatePart(word1, usedChars, options, halfLength, isFirstPart = true) ?: return null
        val part2 = generatePart(word2, usedChars, options, halfLength, isFirstPart = false) ?: return null

        val password = part1 + part2

        val hasUniqueChars = !PasswordValidator.hasDuplicateCharacters(password)

        if (options.enforceUniqueChars && !hasUniqueChars) {
            return null
        }

        val hint = if (options.phrase.isNotBlank()) options.phrase.take(30) else ""
        val variantName = "Вариант ${options.variantOffset + 1}"
        val strength = calculateStrength(password)

        return GenerationResult(
            password = password,
            mnemonicHint = hint,
            variantName = variantName,
            strength = strength,
            part1 = part1,
            part2 = part2,
            hasUniqueChars = hasUniqueChars,
            splitMode = SplitMode.TWO_USERS
        )
    }

    //  Генерация одной части для TWO_USERS с резервированием
    private fun generatePart(
        word: String,
        usedChars: MutableSet<Char>,
        options: GenerationOptions,
        targetLength: Int,
        isFirstPart: Boolean
    ): String? {
        val transliterated = transliterate(word)
        val chars = mutableListOf<Char>()

        // 1. Формируем обязательные добавки
        val serviceCode = if (options.includeServiceCode && options.serviceName.isNotBlank() && isFirstPart) {
            transliterate(options.serviceName.lowercase())
                .replace(Regex("[^a-z]"), "")
                .take(2)
        } else ""

        val rotationCode = if (options.includeRotationCode && !isFirstPart) {
            val now = LocalDate.now()
            val month = options.rotationMonth ?: now.monthValue
            val year = options.rotationYear ?: (now.year % 100)
            val mm = month.toString().padStart(2, '0')
            val yy = year.toString().padStart(2, '0').takeLast(2)
            mm + yy
        } else ""

        // 2. Резервируем место
        val reservedForService = serviceCode.length
        val reservedForRotation = rotationCode.length
        val wordLimit = targetLength - reservedForService - reservedForRotation

        if (wordLimit < 2) return null // Недостаточно места

        // 3. Строим основу из слова
        val firstChar = transliterated.first().uppercaseChar()
        if (!isUsed(firstChar, usedChars)) {
            chars.add(firstChar)
            markUsed(firstChar, usedChars)
        } else {
            val freeUppercase = pickDeterministic(uppercaseLetters, usedChars, options.variantOffset + if (isFirstPart) 0 else 50) ?: return null
            chars.add(freeUppercase)
            markUsed(freeUppercase, usedChars)
        }

        for (i in 1 until transliterated.length) {
            if (chars.size >= wordLimit) break
            val char = transliterated[i]
            
            if (isUsed(char, usedChars)) {
                if (options.includeLeet && char in leetMap) {
                    val replacements = leetMap[char]!!
                    val replacement = pickDeterministic(replacements, usedChars, options.variantOffset + i)
                    if (replacement != null) {
                        chars.add(replacement)
                        markUsed(replacement, usedChars)
                    }
                }
                continue
            }

            if (options.includeLeet && char in leetMap && shouldApplyLeet(options.variantOffset, i, if (isFirstPart) 0 else 1)) {
                val replacements = leetMap[char]!!
                val replacement = pickDeterministic(replacements, usedChars, options.variantOffset + i)
                if (replacement != null) {
                    chars.add(replacement)
                    markUsed(replacement, usedChars)
                    continue
                }
            }

            chars.add(char)
            markUsed(char, usedChars)
        }

        // 4. Вставляем сервисный код (в первую часть)
        if (isFirstPart) {
            for ((index, letter) in serviceCode.withIndex()) {
                if (chars.size >= targetLength) break
                val upper = letter.uppercaseChar()
                if (!isUsed(upper, usedChars)) {
                    chars.add(upper)
                    markUsed(upper, usedChars)
                } else {
                    val replacement = pickDeterministic(uppercaseLetters, usedChars, options.variantOffset + 100 + index)
                    if (replacement != null) {
                        chars.add(replacement)
                        markUsed(replacement, usedChars)
                    }
                }
            }
        }

        // 5. Вставляем ротационный код (во вторую часть)
        if (!isFirstPart) {
            for ((index, digit) in rotationCode.withIndex()) {
                if (chars.size >= targetLength) break
                if (!isUsed(digit, usedChars)) {
                    chars.add(digit)
                    markUsed(digit, usedChars)
                } else {
                    val replacement = pickDeterministic(digits, usedChars, options.variantOffset + 200 + index)
                    if (replacement != null) {
                        chars.add(replacement)
                        markUsed(replacement, usedChars)
                    }
                }
            }
        }

        // 6. Добиваем свободными символами
        var saltCounter = if (isFirstPart) 300 else 400
        while (chars.size < targetLength) {
            val allChars = uppercaseLetters + lowercaseLetters + digits + specialChars
            val randomChar = pickDeterministic(allChars, usedChars, options.variantOffset + saltCounter) ?: break
            chars.add(randomChar)
            markUsed(randomChar, usedChars)
            saltCounter++
        }

        return chars.take(targetLength).joinToString("")
    }

    private fun extractWords(phrase: String): List<String> {
        return phrase
            .lowercase()
            .replace(Regex("[^а-яёa-z\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .distinct()
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
