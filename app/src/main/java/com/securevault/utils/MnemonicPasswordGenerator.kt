package com.securevault.utils

import java.util.Calendar

/**
 * AMPG v1 — Adaptive Mnemonic Password Generation
 * Адаптивная мнемоническая генерация паролей с несколькими вариантами.
 *
 * Детерминированный алгоритм: одинаковые входные данные + одинаковый variantOffset = одинаковый пароль.
 */
object MnemonicPasswordGenerator {

    data class GenerationResult(
        val password: String,
        val mnemonicHint: String,
        val strength: PasswordGenerator.Strength,
        val steps: List<String>,
        val variantName: String
    )

    data class GenerationOptions(
        val phrase: String,
        val serviceName: String = "",
        val rotationMonth: Int? = null,
        val rotationYear: Int? = null,
        val targetLength: Int = 16,
        val includeLeet: Boolean = true,
        val includeServiceCode: Boolean = true,
        val includeRotationCode: Boolean = true,
        val variantOffset: Int = 0
    )

    private val leetMap = mapOf(
        'a' to '@', 'o' to '0', 'e' to '3', 'i' to '1', 's' to '$'
    )

    private val translitMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch",
        'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    private val consonants = setOf(
        'b', 'c', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm',
        'n', 'p', 'q', 'r', 's', 't', 'v', 'w', 'x', 'y', 'z'
    )

    // ✅ Наборы параметров для разных вариантов (зависят от variantOffset)
    private val separators = listOf("-", ".", "_", "~", "+")
    private val specialChars = listOf("#", "@", "$", "%", "&", "!")
    private val serviceCodeStyles = listOf("first2+length", "first3", "first+last", "allCaps+length", "first2only")
    private val rotationCodeStyles = listOf("MMYY", "YYMM", "MM-YY", "YY.MM", "MMYY-short")

    /**
     * Генерация нескольких вариантов пароля на основе одной фразы.
     */
    fun generateVariants(options: GenerationOptions, count: Int = 5): List<GenerationResult> {
        val offset = options.variantOffset
        val results = mutableListOf<GenerationResult>()
        
        // Выбираем параметры на основе offset
        val separator = separators[offset % separators.size]
        val specialChar = specialChars[(offset / separators.size) % specialChars.size]
        val serviceCodeStyle = serviceCodeStyles[(offset / (separators.size * specialChars.size)) % serviceCodeStyles.size]
        val rotationCodeStyle = rotationCodeStyles[(offset / (separators.size * specialChars.size * serviceCodeStyles.size)) % rotationCodeStyles.size]
        
        // Вариант 1: Стандартный с выбранным разделителем
        results.add(generateStandardVariant(options, separator, "Стандартный ($separator)"))
        
        // Вариант 2: С leet-заменами
        results.add(generateLeetVariant(options, separator, "С leet-заменами"))
        
        // Вариант 3: Короткий (первые буквы слов)
        results.add(generateShortVariant(options, separator, "Короткий (первые буквы)"))
        
        // Вариант 4: Компактный (без разделителей)
        results.add(generateCompactVariant(options, "Без разделителей"))
        
        // Вариант 5: Усиленный (с фиксированным спецсимволом)
        results.add(generateEnhancedVariant(options, separator, specialChar, "Усиленный ($specialChar)"))
        
        return results.take(count)
    }

    fun generate(options: GenerationOptions): GenerationResult {
        return generateStandardVariant(options, "-", "AMPG v1")
    }

    private fun generateStandardVariant(options: GenerationOptions, separator: String, variantName: String): GenerationResult {
        val steps = mutableListOf<String>()
        val cleanedPhrase = options.phrase.trim().replace(Regex("\\s+"), " ").lowercase()
        steps.add("1. Очистка фразы: '$cleanedPhrase'")

        val words = cleanedPhrase.split(" ").filter { it.isNotBlank() }
        steps.add("2. Разбиение на слова: ${words.size} слов")

        val transliteratedWords = words.map { transliterate(it) }
        steps.add("3. Транслитерация: ${transliteratedWords.joinToString(" ")}")

        val mnemonicBlocks = transliteratedWords.map { createMnemonicBlock(it) }
        steps.add("4. Мнемонические блоки: ${mnemonicBlocks.joinToString(separator)}")

        var password = mnemonicBlocks.joinToString(separator)
        steps.add("5. Соединение через '$separator': $password")

        var serviceCode = ""
        if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            serviceCode = createServiceCode(options.serviceName, "first2+length")
            password = "$password$separator$serviceCode"
            steps.add("6. Код сервиса: $serviceCode")
        }

        var rotationCode = ""
        if (options.includeRotationCode) {
            rotationCode = createRotationCode(options.rotationMonth, options.rotationYear, "MMYY")
            password = "$password$separator$rotationCode"
            steps.add("7. Код ротации: $rotationCode")
        }

        if (options.includeLeet) {
            password = applyLeet(password)
            steps.add("8. Leet-замены (фиксированные): $password")
        }

        if (password.length < options.targetLength) {
            val suffix = createPaddingSuffix(options.serviceName, password.length, options.targetLength)
            password = "$password$suffix"
            steps.add("9. Добивание суффиксом: $password")
        }

        val hint = buildHint(options, rotationCode, separator)
        val strength = calculateStrength(password)

        return GenerationResult(password, hint, strength, steps, variantName)
    }

    private fun generateLeetVariant(options: GenerationOptions, separator: String, variantName: String): GenerationResult {
        val steps = mutableListOf<String>()
        val cleanedPhrase = options.phrase.trim().replace(Regex("\\s+"), " ").lowercase()
        steps.add("1. Очистка фразы: '$cleanedPhrase'")

        val words = cleanedPhrase.split(" ").filter { it.isNotBlank() }
        steps.add("2. Разбиение на слова: ${words.size} слов")

        val transliteratedWords = words.map { transliterate(it) }
        steps.add("3. Транслитерация: ${transliteratedWords.joinToString(" ")}")

        val mnemonicBlocks = transliteratedWords.map { createMnemonicBlock(it) }
        var password = mnemonicBlocks.joinToString(separator)
        steps.add("4. Блоки: $password")

        // ✅ ВСЕГДА применяем leet в этом варианте
        password = applyLeet(password)
        steps.add("5. Leet-замены: $password")

        var serviceCode = ""
        if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            serviceCode = createServiceCode(options.serviceName, "first2+length")
            password = "$password$separator$serviceCode"
            steps.add("6. Код сервиса: $serviceCode")
        }

        var rotationCode = ""
        if (options.includeRotationCode) {
            rotationCode = createRotationCode(options.rotationMonth, options.rotationYear, "MMYY")
            password = "$password$separator$rotationCode"
            steps.add("7. Код ротации: $rotationCode")
        }

        if (password.length < options.targetLength) {
            val suffix = createPaddingSuffix(options.serviceName, password.length, options.targetLength)
            password = "$password$suffix"
            steps.add("8. Добивание суффиксом: $password")
        }

        val hint = buildHint(options, rotationCode, separator) + " (с leet)"
        val strength = calculateStrength(password)

        return GenerationResult(password, hint, strength, steps, variantName)
    }

    private fun generateShortVariant(options: GenerationOptions, separator: String, variantName: String): GenerationResult {
        val steps = mutableListOf<String>()
        val cleanedPhrase = options.phrase.trim().replace(Regex("\\s+"), " ").lowercase()
        steps.add("1. Очистка фразы: '$cleanedPhrase'")

        val words = cleanedPhrase.split(" ").filter { it.isNotBlank() }
        steps.add("2. Разбиение на слова: ${words.size} слов")

        val transliteratedWords = words.map { transliterate(it) }
        steps.add("3. Транслитерация: ${transliteratedWords.joinToString(" ")}")

        val firstLetters = transliteratedWords.map { word ->
            if (word.isNotEmpty()) word[0].uppercaseChar().toString() else ""
        }
        steps.add("4. Первые буквы слов: ${firstLetters.joinToString("")}")

        var password = firstLetters.joinToString("")
        steps.add("5. Соединение: $password")

        var serviceCode = ""
        if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            serviceCode = createServiceCode(options.serviceName, "first3")
            password = "$password$separator$serviceCode"
            steps.add("6. Код сервиса: $serviceCode")
        }

        var rotationCode = ""
        if (options.includeRotationCode) {
            rotationCode = createRotationCode(options.rotationMonth, options.rotationYear, "YYMM")
            password = "$password$separator$rotationCode"
            steps.add("7. Код ротации: $rotationCode")
        }

        if (options.includeLeet) {
            password = applyLeet(password)
            steps.add("8. Leet-замены: $password")
        }

        if (password.length < options.targetLength) {
            val suffix = createPaddingSuffix(options.serviceName, password.length, options.targetLength)
            password = "$password$suffix"
            steps.add("9. Добивание суффиксом: $password")
        }

        val hint = buildHint(options, rotationCode, separator) + " (первые буквы)"
        val strength = calculateStrength(password)

        return GenerationResult(password, hint, strength, steps, variantName)
    }

    private fun generateCompactVariant(options: GenerationOptions, variantName: String): GenerationResult {
        val steps = mutableListOf<String>()
        val cleanedPhrase = options.phrase.trim().replace(Regex("\\s+"), " ").lowercase()
        steps.add("1. Очистка фразы: '$cleanedPhrase'")

        val words = cleanedPhrase.split(" ").filter { it.isNotBlank() }
        steps.add("2. Разбиение на слова: ${words.size} слов")

        val transliteratedWords = words.map { transliterate(it) }
        steps.add("3. Транслитерация: ${transliteratedWords.joinToString(" ")}")

        val mnemonicBlocks = transliteratedWords.map { createMnemonicBlock(it) }
        steps.add("4. Мнемонические блоки: ${mnemonicBlocks.joinToString("")}")

        var password = mnemonicBlocks.joinToString("")
        steps.add("5. Соединение без разделителей: $password")

        var serviceCode = ""
        if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            serviceCode = createServiceCode(options.serviceName, "first2only")
            password = "$password$serviceCode"
            steps.add("6. Код сервиса: $serviceCode")
        }

        var rotationCode = ""
        if (options.includeRotationCode) {
            rotationCode = createRotationCode(options.rotationMonth, options.rotationYear, "MMYY-short")
            password = "$password$rotationCode"
            steps.add("7. Код ротации: $rotationCode")
        }

        if (options.includeLeet) {
            password = applyLeet(password)
            steps.add("8. Leet-замены: $password")
        }

        if (password.length < options.targetLength) {
            val suffix = createPaddingSuffix(options.serviceName, password.length, options.targetLength)
            password = "$password$suffix"
            steps.add("9. Добивание суффиксом: $password")
        }

        val hint = buildHint(options, rotationCode, "") + " (слитно)"
        val strength = calculateStrength(password)

        return GenerationResult(password, hint, strength, steps, variantName)
    }

    private fun generateEnhancedVariant(options: GenerationOptions, separator: String, specialChar: String, variantName: String): GenerationResult {
        val steps = mutableListOf<String>()
        val cleanedPhrase = options.phrase.trim().replace(Regex("\\s+"), " ").lowercase()
        steps.add("1. Очистка фразы: '$cleanedPhrase'")

        val words = cleanedPhrase.split(" ").filter { it.isNotBlank() }
        steps.add("2. Разбиение на слова: ${words.size} слов")

        val transliteratedWords = words.map { transliterate(it) }
        steps.add("3. Транслитерация: ${transliteratedWords.joinToString(" ")}")

        val mnemonicBlocks = transliteratedWords.map { createMnemonicBlock(it) }
        var password = mnemonicBlocks.joinToString(separator)
        steps.add("4. Блоки: $password")

        var serviceCode = ""
        if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            serviceCode = createServiceCode(options.serviceName, "allCaps+length")
            password = "$password$separator$serviceCode"
            steps.add("5. Код сервиса: $serviceCode")
        }

        var rotationCode = ""
        if (options.includeRotationCode) {
            rotationCode = createRotationCode(options.rotationMonth, options.rotationYear, "MM-YY")
            password = "$password$separator$rotationCode"
            steps.add("6. Код ротации: $rotationCode")
        }

        // Фиксированная замена последнего разделителя на спецсимвол
        val lastSepIndex = password.lastIndexOf(separator)
        if (lastSepIndex > 0) {
            password = password.substring(0, lastSepIndex) + specialChar + password.substring(lastSepIndex + 1)
            steps.add("7. Замена '$separator' на '$specialChar': $password")
        }

        if (options.includeLeet) {
            password = applyLeet(password)
            steps.add("8. Leet-замены: $password")
        }

        if (password.length < options.targetLength) {
            val suffix = createPaddingSuffix(options.serviceName, password.length, options.targetLength)
            password = "$password$suffix"
            steps.add("9. Добивание суффиксом: $password")
        }

        val hint = buildHint(options, rotationCode, separator) + " (усиленный с '$specialChar')"
        val strength = calculateStrength(password)

        return GenerationResult(password, hint, strength, steps, variantName)
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ =====

    private fun transliterate(text: String): String {
        val result = StringBuilder()
        for (char in text) {
            val lower = char.lowercaseChar()
            if (lower in translitMap) {
                result.append(translitMap[lower])
            } else if (char.isLetter()) {
                result.append(lower)
            }
        }
        return result.toString()
    }

    private fun createMnemonicBlock(word: String): String {
        if (word.isEmpty()) return ""
        val firstChar = word[0].uppercaseChar()
        val consonantsInWord = word.filter { it.lowercaseChar() in consonants }
        
        var block = "$firstChar"
        for (ch in consonantsInWord) {
            if (block.length >= 4) break
            if (ch.lowercaseChar() != firstChar.lowercaseChar()) {
                block += ch.lowercaseChar()
            }
        }
        
        if (block.length < 2) {
            for (ch in word) {
                if (block.length >= 4) break
                if (ch.lowercaseChar() !in block.lowercase()) {
                    block += ch.lowercaseChar()
                }
            }
        }
        
        return block.take(4)
    }

    // ✅ РАЗНЫЕ СТИЛИ сервисного кода
    private fun createServiceCode(serviceName: String, style: String): String {
        val transliterated = transliterate(serviceName.lowercase())
        val cleaned = transliterated.filter { it.isLetterOrDigit() }
        
        if (cleaned.isEmpty()) return "Sv"
        
        return when (style) {
            "first2+length" -> {
                val first = cleaned[0].uppercaseChar()
                val second = if (cleaned.length > 1) cleaned[1].lowercaseChar() else ""
                "$first$second${cleaned.length}"
            }
            "first3" -> {
                val first = cleaned[0].uppercaseChar()
                val rest = cleaned.substring(1, minOf(3, cleaned.length)).lowercase()
                "$first$rest"
            }
            "first+last" -> {
                val first = cleaned[0].uppercaseChar()
                val last = if (cleaned.length > 1) cleaned.last().uppercaseChar() else ""
                "$first$last"
            }
            "allCaps+length" -> {
                val first = cleaned[0].uppercaseChar()
                val second = if (cleaned.length > 1) cleaned[1].uppercaseChar() else ""
                "$first$second${cleaned.length}"
            }
            "first2only" -> {
                val first = cleaned[0].uppercaseChar()
                val second = if (cleaned.length > 1) cleaned[1].lowercaseChar() else ""
                "$first$second"
            }
            else -> "${cleaned[0].uppercaseChar()}${cleaned.length}"
        }
    }

    // ✅ РАЗНЫЕ СТИЛИ кода ротации
    private fun createRotationCode(month: Int?, year: Int?, style: String): String {
        val currentMonth = month ?: Calendar.getInstance().get(Calendar.MONTH) + 1
        val currentYear = year ?: (Calendar.getInstance().get(Calendar.YEAR) % 100)
        
        return when (style) {
            "MMYY" -> String.format("%02d%02d", currentMonth, currentYear)
            "YYMM" -> String.format("%02d%02d", currentYear, currentMonth)
            "MM-YY" -> String.format("%02d-%02d", currentMonth, currentYear)
            "YY.MM" -> String.format("%02d.%02d", currentYear, currentMonth)
            "MMYY-short" -> String.format("%d%02d", currentMonth, currentYear)
            else -> String.format("%02d%02d", currentMonth, currentYear)
        }
    }

    private fun applyLeet(text: String): String {
        return text.map { char ->
            leetMap[char.lowercaseChar()] ?: char
        }.joinToString("")
    }

    private fun createPaddingSuffix(serviceName: String, currentLength: Int, targetLength: Int): String {
        val needed = targetLength - currentLength
        if (needed <= 0) return ""
        
        val serviceCode = createServiceCode(serviceName, "first2+length")
        val suffix = "-$serviceCode"
        
        var result = ""
        while (result.length < needed) {
            result += suffix
        }
        
        return result.take(needed)
    }

    private fun buildHint(options: GenerationOptions, rotationCode: String, separator: String): String {
        val phrasePreview = if (options.phrase.length > 30) {
            options.phrase.take(30) + "..."
        } else {
            options.phrase
        }
        
        val parts = mutableListOf<String>()
        parts.add(phrasePreview)
        
        if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            val servicePreview = if (options.serviceName.length > 15) {
                options.serviceName.take(15) + "..."
            } else {
                options.serviceName
            }
            parts.add(servicePreview)
        }
        
        if (options.includeRotationCode && rotationCode.isNotBlank()) {
            parts.add(rotationCode)
        }
        
        return parts.joinToString(" + ") + " (AMPG v1)"
    }

    private fun calculateStrength(password: String): PasswordGenerator.Strength {
        var score = 0
        if (password.length >= 12) score++
        if (password.length >= 16) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        if (password.contains("-") || password.contains(".") || password.contains("_")) score++
        
        return when {
            score >= 5 -> PasswordGenerator.Strength.VERY_STRONG
            score >= 4 -> PasswordGenerator.Strength.STRONG
            score >= 2 -> PasswordGenerator.Strength.MEDIUM
            else -> PasswordGenerator.Strength.WEAK
        }
    }
}
