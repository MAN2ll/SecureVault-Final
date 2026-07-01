package com.securevault.utils

import java.util.Calendar

/**
 * AMPG v1 — Adaptive Mnemonic Password Generation
 * Адаптивная мнемоническая генерация паролей с несколькими вариантами.
 *
 * Детерминированный алгоритм: одинаковые входные данные + одинаковый variantOffset = одинаковый пароль.
 * Без скрытой случайности. Пароль должен быть запоминаемым по подсказке.
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
        val serviceName: String,
        val rotationMonth: Int? = null,
        val rotationYear: Int? = null,
        val targetLength: Int = 16,
        val includeLeet: Boolean = true,
        val includeServiceCode: Boolean = true,
        val includeRotationCode: Boolean = true,
        val variantOffset: Int = 0
    )

    // Фиксированные leet-замены (без случайности)
    private val leetMap = mapOf(
        'a' to '@',
        'o' to '0',
        'e' to '3',
        'i' to '1',
        's' to '$'
    )

    // Транслитерация кириллицы в латиницу
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

    /**
     * Генерация нескольких вариантов пароля на основе одной фразы.
     * Варианты отличаются способом построения, но все понятны по подсказке.
     */
    fun generateVariants(options: GenerationOptions, count: Int = 5): List<GenerationResult> {
        val results = mutableListOf<GenerationResult>()
        
        // Вариант 1: Стандартный (блоки + сервис + дата)
        results.add(generateVariant(options.copy(includeLeet = false), "Стандартный"))
        
        // Вариант 2: С leet-заменами
        results.add(generateVariant(options.copy(includeLeet = true), "С leet-заменами"))
        
        // Вариант 3: Короткий (первые буквы слов)
        results.add(generateShortVariant(options, "Короткий (первые буквы)"))
        
        // Вариант 4: Без разделителей
        results.add(generateCompactVariant(options, "Без разделителей"))
        
        // Вариант 5: Усиленный (с фиксированным спецсимволом)
        results.add(generateEnhancedVariant(options, "Усиленный"))
        
        return results.take(count)
    }

    /**
     * Генерация одного варианта (стандартный алгоритм AMPG v1).
     */
    fun generate(options: GenerationOptions): GenerationResult {
        return generateVariant(options, "AMPG v1")
    }

    private fun generateVariant(options: GenerationOptions, variantName: String): GenerationResult {
        val steps = mutableListOf<String>()

        // Шаг 1: Очистка фразы
        val cleanedPhrase = options.phrase
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
        steps.add("1. Очистка фразы: '$cleanedPhrase'")

        // Шаг 2: Разбиение на слова
        val words = cleanedPhrase.split(" ").filter { it.isNotBlank() }
        steps.add("2. Разбиение на слова: ${words.size} слов")

        // Шаг 3: Транслитерация
        val transliteratedWords = words.map { transliterate(it) }
        steps.add("3. Транслитерация: ${transliteratedWords.joinToString(" ")}")

        // Шаг 4: Мнемонические блоки
        val mnemonicBlocks = transliteratedWords.map { createMnemonicBlock(it) }
        steps.add("4. Мнемонические блоки: ${mnemonicBlocks.joinToString("-")}")

        // Шаг 5: Соединение блоков
        var password = mnemonicBlocks.joinToString("-")
        steps.add("5. Соединение через дефис: $password")

        // Шаг 6: Код сервиса
        var serviceCode = ""
        if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            serviceCode = createServiceCode(options.serviceName)
            password = "$password-$serviceCode"
            steps.add("6. Код сервиса: $serviceCode")
        }

        // Шаг 7: Код ротации
        var rotationCode = ""
        if (options.includeRotationCode) {
            rotationCode = createRotationCode(options.rotationMonth, options.rotationYear)
            password = "$password-$rotationCode"
            steps.add("7. Код ротации: $rotationCode")
        }

        // Шаг 8: Leet-замены (фиксированные)
        if (options.includeLeet) {
            password = applyLeet(password)
            steps.add("8. Leet-замены (фиксированные): $password")
        }

        // Шаг 9: Добивание до targetLength
        if (password.length < options.targetLength) {
            val suffix = createPaddingSuffix(options.serviceName, password.length, options.targetLength)
            password = "$password$suffix"
            steps.add("9. Добивание суффиксом: $password")
        }

        val hint = buildHint(options, rotationCode)
        val strength = calculateStrength(password)

        return GenerationResult(
            password = password,
            mnemonicHint = hint,
            strength = strength,
            steps = steps,
            variantName = variantName
        )
    }

    /**
     * Вариант 3: Короткий — только первые буквы каждого слова.
     */
    private fun generateShortVariant(options: GenerationOptions, variantName: String): GenerationResult {
        val steps = mutableListOf<String>()

        val cleanedPhrase = options.phrase.trim().replace(Regex("\\s+"), " ").lowercase()
        steps.add("1. Очистка фразы: '$cleanedPhrase'")

        val words = cleanedPhrase.split(" ").filter { it.isNotBlank() }
        steps.add("2. Разбиение на слова: ${words.size} слов")

        val transliteratedWords = words.map { transliterate(it) }
        steps.add("3. Транслитерация: ${transliteratedWords.joinToString(" ")}")

        // Берём только первую букву каждого слова
        val firstLetters = transliteratedWords.map { word ->
            if (word.isNotEmpty()) word[0].uppercaseChar().toString() else ""
        }
        steps.add("4. Первые буквы слов: ${firstLetters.joinToString("")}")

        var password = firstLetters.joinToString("")
        steps.add("5. Соединение: $password")

        var serviceCode = ""
        if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            serviceCode = createServiceCode(options.serviceName)
            password = "$password-$serviceCode"
            steps.add("6. Код сервиса: $serviceCode")
        }

        var rotationCode = ""
        if (options.includeRotationCode) {
            rotationCode = createRotationCode(options.rotationMonth, options.rotationYear)
            password = "$password-$rotationCode"
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

        val hint = buildHint(options, rotationCode) + " (первые буквы)"
        val strength = calculateStrength(password)

        return GenerationResult(
            password = password,
            mnemonicHint = hint,
            strength = strength,
            steps = steps,
            variantName = variantName
        )
    }

    /**
     * Вариант 4: Компактный — без разделителей между блоками.
     */
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

        // Без разделителей
        var password = mnemonicBlocks.joinToString("")
        steps.add("5. Соединение без разделителей: $password")

        var serviceCode = ""
        if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            serviceCode = createServiceCode(options.serviceName)
            password = "$password$serviceCode"
            steps.add("6. Код сервиса: $serviceCode")
        }

        var rotationCode = ""
        if (options.includeRotationCode) {
            rotationCode = createRotationCode(options.rotationMonth, options.rotationYear)
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

        val hint = buildHint(options, rotationCode) + " (слитно)"
        val strength = calculateStrength(password)

        return GenerationResult(
            password = password,
            mnemonicHint = hint,
            strength = strength,
            steps = steps,
            variantName = variantName
        )
    }

    /**
     * Вариант 5: Усиленный — с фиксированным спецсимволом в определённой позиции.
     */
    private fun generateEnhancedVariant(options: GenerationOptions, variantName: String): GenerationResult {
        val steps = mutableListOf<String>()

        val cleanedPhrase = options.phrase.trim().replace(Regex("\\s+"), " ").lowercase()
        steps.add("1. Очистка фразы: '$cleanedPhrase'")

        val words = cleanedPhrase.split(" ").filter { it.isNotBlank() }
        steps.add("2. Разбиение на слова: ${words.size} слов")

        val transliteratedWords = words.map { transliterate(it) }
        steps.add("3. Транслитерация: ${transliteratedWords.joinToString(" ")}")

        val mnemonicBlocks = transliteratedWords.map { createMnemonicBlock(it) }
        steps.add("4. Мнемонические блоки: ${mnemonicBlocks.joinToString("-")}")

        var password = mnemonicBlocks.joinToString("-")
        steps.add("5. Соединение: $password")

        var serviceCode = ""
        if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            serviceCode = createServiceCode(options.serviceName)
            password = "$password-$serviceCode"
            steps.add("6. Код сервиса: $serviceCode")
        }

        var rotationCode = ""
        if (options.includeRotationCode) {
            rotationCode = createRotationCode(options.rotationMonth, options.rotationYear)
            password = "$password-$rotationCode"
            steps.add("7. Код ротации: $rotationCode")
        }

        // Фиксированная замена: последний дефис → спецсимвол
        val specialChar = "#"
        val lastDashIndex = password.lastIndexOf('-')
        if (lastDashIndex > 0) {
            password = password.substring(0, lastDashIndex) + specialChar + password.substring(lastDashIndex + 1)
            steps.add("8. Замена последнего '-' на '$specialChar': $password")
        }

        if (options.includeLeet) {
            password = applyLeet(password)
            steps.add("9. Leet-замены: $password")
        }

        if (password.length < options.targetLength) {
            val suffix = createPaddingSuffix(options.serviceName, password.length, options.targetLength)
            password = "$password$suffix"
            steps.add("10. Добивание суффиксом: $password")
        }

        val hint = buildHint(options, rotationCode) + " (усиленный)"
        val strength = calculateStrength(password)

        return GenerationResult(
            password = password,
            mnemonicHint = hint,
            strength = strength,
            steps = steps,
            variantName = variantName
        )
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

    private fun createServiceCode(serviceName: String): String {
        val transliterated = transliterate(serviceName.lowercase())
        val cleaned = transliterated.filter { it.isLetterOrDigit() }
        
        if (cleaned.isEmpty()) return "Sv"
        
        val firstChar = cleaned[0].uppercaseChar()
        val nextChars = if (cleaned.length > 1) cleaned[1].lowercaseChar() else ""
        val length = cleaned.length
        
        return "$firstChar$nextChars$length"
    }

    private fun createRotationCode(month: Int?, year: Int?): String {
        val currentMonth = month ?: Calendar.getInstance().get(Calendar.MONTH) + 1
        val currentYear = year ?: (Calendar.getInstance().get(Calendar.YEAR) % 100)
        
        return String.format("%02d%02d", currentMonth, currentYear)
    }

    private fun applyLeet(text: String): String {
        return text.map { char ->
            leetMap[char.lowercaseChar()] ?: char
        }.joinToString("")
    }

    private fun createPaddingSuffix(serviceName: String, currentLength: Int, targetLength: Int): String {
        val needed = targetLength - currentLength
        if (needed <= 0) return ""
        
        val serviceCode = createServiceCode(serviceName)
        val suffix = "-$serviceCode"
        
        var result = ""
        while (result.length < needed) {
            result += suffix
        }
        
        return result.take(needed)
    }

    private fun buildHint(options: GenerationOptions, rotationCode: String): String {
        val phrasePreview = if (options.phrase.length > 30) {
            options.phrase.take(30) + "..."
        } else {
            options.phrase
        }
        
        val servicePreview = if (options.serviceName.length > 15) {
            options.serviceName.take(15) + "..."
        } else {
            options.serviceName
        }
        
        return "$phrasePreview + $servicePreview + $rotationCode (AMPG v1)"
    }

    private fun calculateStrength(password: String): PasswordGenerator.Strength {
        var score = 0
        if (password.length >= 12) score++
        if (password.length >= 16) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        if (password.contains("-")) score++
        
        return when {
            score >= 5 -> PasswordGenerator.Strength.VERY_STRONG
            score >= 4 -> PasswordGenerator.Strength.STRONG
            score >= 2 -> PasswordGenerator.Strength.MEDIUM
            else -> PasswordGenerator.Strength.WEAK
        }
    }
}
