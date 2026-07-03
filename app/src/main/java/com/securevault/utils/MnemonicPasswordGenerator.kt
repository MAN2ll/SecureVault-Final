package com.securevault.utils

import java.util.Calendar

/**
 * AMPG v1 — Adaptive Mnemonic Password Generation
 * Адаптивная мнемоническая генерация паролей с несколькими стратегиями.
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

    // ✅ НОВАЯ СТРУКТУРА: спецификация варианта
    data class VariantSpec(
        val blockStrategy: BlockStrategy,
        val orderStrategy: OrderStrategy,
        val casingStrategy: CasingStrategy,
        val leetStrategy: LeetStrategy,
        val tokenOrderStrategy: TokenOrderStrategy,
        val serviceCodeStyle: ServiceCodeStyle,
        val rotationCodeStyle: RotationCodeStyle,
        val separatorStrategy: SeparatorStrategy
    )

    // ===== СТРАТЕГИИ =====
    
    enum class BlockStrategy {
        CONSONANTS,      // первая буква + согласные
        FIRST_LAST,      // первая + последняя буква + длина
        FIRST_VOWELS,    // первая буква + гласные
        SYLLABLE_LIKE,   // первые 2-3 символа транслитерации
        INITIALS_ONLY    // только первые буквы слов
    }

    enum class OrderStrategy {
        ORIGINAL,        // исходный порядок
        REVERSED,        // обратный порядок
        ALTERNATING,     // нечётные сначала, потом чётные
        LONG_FIRST       // сначала длинные слова
    }

    enum class CasingStrategy {
        CAPITALIZE_BLOCKS,  // первая буква блока заглавная
        ALTERNATING_CASE,   // чередование регистра по блокам
        SERVICE_UPPER,      // код сервиса заглавными
        FIRST_LAST_UPPER    // первый и последний блок заглавными
    }

    enum class LeetStrategy {
        NONE,           // без замен
        SOFT,           // 1-2 символа
        FULL,           // все разрешённые
        VOWEL_ONLY      // только гласные
    }

    enum class TokenOrderStrategy {
        PHRASE_SERVICE_ROTATION,
        SERVICE_PHRASE_ROTATION,
        PHRASE_ROTATION_SERVICE,
        PHRASE_ONLY
    }

    enum class ServiceCodeStyle {
        FIRST2_LENGTH,  // Gm5
        FIRST3,         // Gma
        FIRST_LAST,     // Gl
        CONSONANTS,     // Gml
        NO_SERVICE
    }

    enum class RotationCodeStyle {
        MMYY,           // 0626
        YYMM,           // 2606
        MM_YY,          // 06-26
        QYY,            // Q2-26
        NO_ROTATION
    }

    enum class SeparatorStrategy {
        DASH,           // -
        DOT,            // .
        NONE,           // без разделителей
        MAJOR_ONLY      // только между крупными частями
    }

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

    private val vowels = setOf('a', 'e', 'i', 'o', 'u')

    // ✅ НОВЫЙ МЕТОД: построение спецификации варианта
    private fun buildVariantSpec(setIndex: Int, variantIndex: Int, options: GenerationOptions): VariantSpec {
        val seed = setIndex * 5 + variantIndex
        
        val blockStrategy = BlockStrategy.values()[(seed + variantIndex) % BlockStrategy.values().size]
        val orderStrategy = OrderStrategy.values()[(seed / 2 + variantIndex + setIndex) % OrderStrategy.values().size]
        val casingStrategy = CasingStrategy.values()[(seed / 3 + setIndex * 2) % CasingStrategy.values().size]
        
        val leetStrategy = if (!options.includeLeet) {
            LeetStrategy.NONE
        } else {
            LeetStrategy.values()[(seed / 4 + variantIndex + setIndex) % LeetStrategy.values().size]
        }
        
        val tokenOrderStrategy = TokenOrderStrategy.values()[(seed / 5 + variantIndex * 2) % TokenOrderStrategy.values().size]
        
        val serviceCodeStyle = if (!options.includeServiceCode) {
            ServiceCodeStyle.NO_SERVICE
        } else {
            ServiceCodeStyle.values()[(seed / 6 + setIndex + variantIndex) % ServiceCodeStyle.values().size]
        }
        
        val rotationCodeStyle = if (!options.includeRotationCode) {
            RotationCodeStyle.NO_ROTATION
        } else {
            RotationCodeStyle.values()[(seed / 7 + variantIndex + setIndex * 3) % RotationCodeStyle.values().size]
        }
        
        val separatorStrategy = SeparatorStrategy.values()[(seed / 8 + setIndex + variantIndex * 2) % SeparatorStrategy.values().size]
        
        return VariantSpec(
            blockStrategy, orderStrategy, casingStrategy, leetStrategy,
            tokenOrderStrategy, serviceCodeStyle, rotationCodeStyle, separatorStrategy
        )
    }

    // ✅ ИСПРАВЛЕНО: проверка на повторы символов
    fun generateVariants(options: GenerationOptions, count: Int = 5): List<GenerationResult> {
        val results = mutableListOf<GenerationResult>()
        val usedPasswords = mutableSetOf<String>()
        val setIndex = options.variantOffset
        
        var variantIndex = 0
        var attempts = 0
        val maxAttempts = count * 10 // Увеличиваем лимит попыток
        
        while (results.size < count && attempts < maxAttempts) {
            val spec = buildVariantSpec(setIndex, variantIndex, options)
            val result = generateWithStrategies(options, spec)
            
            // ✅ ПРОВЕРКА 1: уникальность пароля
            val isUniquePassword = result.password !in usedPasswords
            
            // ✅ ПРОВЕРКА 2: нет повторяющихся символов (A и a считаются повтором)
            val hasNoDuplicateChars = !PasswordValidator.hasDuplicateCharacters(result.password)
            
            if (isUniquePassword && hasNoDuplicateChars) {
                results.add(result)
                usedPasswords.add(result.password)
            }
            
            variantIndex++
            attempts++
        }
        
        return results
    }

    fun generate(options: GenerationOptions): GenerationResult {
        val spec = VariantSpec(
            BlockStrategy.CONSONANTS,
            OrderStrategy.ORIGINAL,
            CasingStrategy.CAPITALIZE_BLOCKS,
            if (options.includeLeet) LeetStrategy.SOFT else LeetStrategy.NONE,
            TokenOrderStrategy.PHRASE_SERVICE_ROTATION,
            if (options.includeServiceCode) ServiceCodeStyle.FIRST2_LENGTH else ServiceCodeStyle.NO_SERVICE,
            if (options.includeRotationCode) RotationCodeStyle.MMYY else RotationCodeStyle.NO_ROTATION,
            SeparatorStrategy.DASH
        )
        return generateWithStrategies(options, spec)
    }

    private fun generateWithStrategies(
        options: GenerationOptions,
        spec: VariantSpec
    ): GenerationResult {
        val steps = mutableListOf<String>()
        
        // Шаг 1: Очистка фразы
        val cleanedPhrase = options.phrase.trim().replace(Regex("\\s+"), " ").lowercase()
        steps.add("1. Очистка фразы: '$cleanedPhrase'")

        // Шаг 2: Разбиение на слова
        val words = cleanedPhrase.split(" ").filter { it.isNotBlank() }
        steps.add("2. Разбиение на слова: ${words.size} слов")

        // Шаг 3: Транслитерация
        val transliteratedWords = words.map { transliterate(it) }
        steps.add("3. Транслитерация: ${transliteratedWords.joinToString(" ")}")

        // Шаг 4: Построение блоков
        val blocks = when (spec.blockStrategy) {
            BlockStrategy.CONSONANTS -> transliteratedWords.map { createConsonantBlock(it) }
            BlockStrategy.FIRST_LAST -> transliteratedWords.map { createFirstLastBlock(it) }
            BlockStrategy.FIRST_VOWELS -> transliteratedWords.map { createVowelBlock(it) }
            BlockStrategy.SYLLABLE_LIKE -> transliteratedWords.map { createSyllableBlock(it) }
            BlockStrategy.INITIALS_ONLY -> transliteratedWords.map { it.firstOrNull()?.uppercaseChar()?.toString() ?: "" }
        }
        steps.add("4. Блоки (${spec.blockStrategy.name}): ${blocks.joinToString(", ")}")

        // Шаг 5: Порядок блоков
        val orderedBlocks = when (spec.orderStrategy) {
            OrderStrategy.ORIGINAL -> blocks
            OrderStrategy.REVERSED -> blocks.reversed()
            OrderStrategy.ALTERNATING -> {
                val odd = blocks.filterIndexed { index, _ -> index % 2 == 0 }
                val even = blocks.filterIndexed { index, _ -> index % 2 == 1 }
                odd + even
            }
            OrderStrategy.LONG_FIRST -> blocks.sortedByDescending { it.length }
        }
        steps.add("5. Порядок (${spec.orderStrategy.name}): ${orderedBlocks.joinToString(", ")}")

        // Шаг 6: Регистр
        val casedBlocks = when (spec.casingStrategy) {
            CasingStrategy.CAPITALIZE_BLOCKS -> orderedBlocks.map { it.capitalize() }
            CasingStrategy.ALTERNATING_CASE -> orderedBlocks.mapIndexed { index, block ->
                if (index % 2 == 0) block.uppercase() else block.lowercase()
            }
            CasingStrategy.SERVICE_UPPER -> orderedBlocks
            CasingStrategy.FIRST_LAST_UPPER -> orderedBlocks.mapIndexed { index, block ->
                if (index == 0 || index == orderedBlocks.size - 1) block.uppercase() else block.lowercase()
            }
        }
        steps.add("6. Регистр (${spec.casingStrategy.name}): ${casedBlocks.joinToString(", ")}")

        // Шаг 7: Leet-замены
        val leetBlocks = when (spec.leetStrategy) {
            LeetStrategy.NONE -> casedBlocks
            LeetStrategy.SOFT -> casedBlocks.map { applySoftLeet(it) }
            LeetStrategy.FULL -> casedBlocks.map { applyFullLeet(it) }
            LeetStrategy.VOWEL_ONLY -> casedBlocks.map { applyVowelLeet(it) }
        }
        if (spec.leetStrategy != LeetStrategy.NONE) {
            steps.add("7. Leet (${spec.leetStrategy.name}): ${leetBlocks.joinToString(", ")}")
        }

        // Шаг 8: Код сервиса
        var serviceCode = ""
        if (spec.serviceCodeStyle != ServiceCodeStyle.NO_SERVICE && options.serviceName.isNotBlank()) {
            serviceCode = when (spec.serviceCodeStyle) {
                ServiceCodeStyle.FIRST2_LENGTH -> createServiceCode(options.serviceName, "first2+length")
                ServiceCodeStyle.FIRST3 -> createServiceCode(options.serviceName, "first3")
                ServiceCodeStyle.FIRST_LAST -> createServiceCode(options.serviceName, "first+last")
                ServiceCodeStyle.CONSONANTS -> createServiceCode(options.serviceName, "consonants")
                ServiceCodeStyle.NO_SERVICE -> ""
            }
            if (spec.casingStrategy == CasingStrategy.SERVICE_UPPER) {
                serviceCode = serviceCode.uppercase()
            }
            steps.add("8. Код сервиса (${spec.serviceCodeStyle.name}): $serviceCode")
        }

        // Шаг 9: Код ротации
        var rotationCode = ""
        if (spec.rotationCodeStyle != RotationCodeStyle.NO_ROTATION) {
            rotationCode = when (spec.rotationCodeStyle) {
                RotationCodeStyle.MMYY -> createRotationCode(options.rotationMonth, options.rotationYear, "MMYY")
                RotationCodeStyle.YYMM -> createRotationCode(options.rotationMonth, options.rotationYear, "YYMM")
                RotationCodeStyle.MM_YY -> createRotationCode(options.rotationMonth, options.rotationYear, "MM-YY")
                RotationCodeStyle.QYY -> createRotationCode(options.rotationMonth, options.rotationYear, "QYY")
                RotationCodeStyle.NO_ROTATION -> ""
            }
            steps.add("9. Код ротации (${spec.rotationCodeStyle.name}): $rotationCode")
        }

        // Шаг 10: Сборка пароля
        val separator = when (spec.separatorStrategy) {
            SeparatorStrategy.DASH -> "-"
            SeparatorStrategy.DOT -> "."
            SeparatorStrategy.NONE -> ""
            SeparatorStrategy.MAJOR_ONLY -> "-"
        }

        var password = when (spec.tokenOrderStrategy) {
            TokenOrderStrategy.PHRASE_SERVICE_ROTATION -> {
                val phrasePart = leetBlocks.joinToString(if (spec.separatorStrategy == SeparatorStrategy.MAJOR_ONLY) "" else separator)
                val parts = mutableListOf(phrasePart)
                if (serviceCode.isNotBlank()) parts.add(serviceCode)
                if (rotationCode.isNotBlank()) parts.add(rotationCode)
                parts.joinToString(separator)
            }
            TokenOrderStrategy.SERVICE_PHRASE_ROTATION -> {
                val phrasePart = leetBlocks.joinToString(if (spec.separatorStrategy == SeparatorStrategy.MAJOR_ONLY) "" else separator)
                val parts = mutableListOf<String>()
                if (serviceCode.isNotBlank()) parts.add(serviceCode)
                parts.add(phrasePart)
                if (rotationCode.isNotBlank()) parts.add(rotationCode)
                parts.joinToString(separator)
            }
            TokenOrderStrategy.PHRASE_ROTATION_SERVICE -> {
                val phrasePart = leetBlocks.joinToString(if (spec.separatorStrategy == SeparatorStrategy.MAJOR_ONLY) "" else separator)
                val parts = mutableListOf(phrasePart)
                if (rotationCode.isNotBlank()) parts.add(rotationCode)
                if (serviceCode.isNotBlank()) parts.add(serviceCode)
                parts.joinToString(separator)
            }
            TokenOrderStrategy.PHRASE_ONLY -> {
                leetBlocks.joinToString(if (spec.separatorStrategy == SeparatorStrategy.MAJOR_ONLY) "" else separator)
            }
        }
        steps.add("10. Сборка (${spec.tokenOrderStrategy.name}, ${spec.separatorStrategy.name}): $password")

        // Шаг 11: Добивание до targetLength
        if (password.length < options.targetLength) {
            val suffix = createPaddingSuffix(options.serviceName, password.length, options.targetLength)
            password = "$password$suffix"
            steps.add("11. Добивание суффиксом: $password")
        }

        val hint = buildHint(options, spec.blockStrategy, spec.orderStrategy, spec.leetStrategy, spec.serviceCodeStyle, spec.rotationCodeStyle)
        val strength = calculateStrength(password)
        val variantName = buildVariantName(spec)

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

    private fun createConsonantBlock(word: String): String {
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

    private fun createFirstLastBlock(word: String): String {
        if (word.isEmpty()) return ""
        val first = word[0].uppercaseChar()
        val last = if (word.length > 1) word.last().lowercaseChar() else ""
        val length = word.length
        return "$first$last$length"
    }

    private fun createVowelBlock(word: String): String {
        if (word.isEmpty()) return ""
        val firstChar = word[0].uppercaseChar()
        val vowelsInWord = word.filter { it.lowercaseChar() in vowels }
        
        var block = "$firstChar"
        for (ch in vowelsInWord) {
            if (block.length >= 4) break
            block += ch.lowercaseChar()
        }
        
        return block.take(4)
    }

    private fun createSyllableBlock(word: String): String {
        return word.take(3).capitalize()
    }

    private fun applySoftLeet(text: String): String {
        val chars = text.toCharArray()
        var count = 0
        for (i in chars.indices) {
            if (chars[i].lowercaseChar() in leetMap && count < 2) {
                chars[i] = leetMap[chars[i].lowercaseChar()]!!
                count++
            }
        }
        return String(chars)
    }

    private fun applyFullLeet(text: String): String {
        return text.map { char ->
            leetMap[char.lowercaseChar()] ?: char
        }.joinToString("")
    }

    private fun applyVowelLeet(text: String): String {
        return text.map { char ->
            if (char.lowercaseChar() in vowels && char.lowercaseChar() in leetMap) {
                leetMap[char.lowercaseChar()]!!
            } else {
                char
            }
        }.joinToString("")
    }

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
            "consonants" -> {
                val first = cleaned[0].uppercaseChar()
                val consonantsOnly = cleaned.filter { it.lowercaseChar() in consonants }
                val second = if (consonantsOnly.length > 1) consonantsOnly[1].lowercaseChar() else ""
                "$first$second"
            }
            else -> "${cleaned[0].uppercaseChar()}${cleaned.length}"
        }
    }

    private fun createRotationCode(month: Int?, year: Int?, style: String): String {
        val currentMonth = month ?: Calendar.getInstance().get(Calendar.MONTH) + 1
        val currentYear = year ?: (Calendar.getInstance().get(Calendar.YEAR) % 100)
        
        return when (style) {
            "MMYY" -> String.format("%02d%02d", currentMonth, currentYear)
            "YYMM" -> String.format("%02d%02d", currentYear, currentMonth)
            "MM-YY" -> String.format("%02d-%02d", currentMonth, currentYear)
            "QYY" -> {
                val quarter = ((currentMonth - 1) / 3) + 1
                "Q$quarter-$currentYear"
            }
            else -> String.format("%02d%02d", currentMonth, currentYear)
        }
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

    private fun buildHint(options: GenerationOptions, blockStrategy: BlockStrategy, orderStrategy: OrderStrategy, leetStrategy: LeetStrategy, serviceCodeStyle: ServiceCodeStyle, rotationCodeStyle: RotationCodeStyle): String {
        val phrasePreview = if (options.phrase.length > 20) {
            options.phrase.take(20) + "..."
        } else {
            options.phrase
        }
        
        val parts = mutableListOf<String>()
        parts.add(phrasePreview)
        
        if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            parts.add("сервис")
        }
        
        if (options.includeRotationCode) {
            parts.add("ротация")
        }
        
        return "${parts.joinToString(" + ")} [${blockStrategy.name}, ${orderStrategy.name}]"
    }

    // ✅ УЛУЧШЕННЫЙ МЕТОД: понятные названия вариантов
    private fun buildVariantName(spec: VariantSpec): String {
        val parts = mutableListOf<String>()
        
        when (spec.blockStrategy) {
            BlockStrategy.CONSONANTS -> parts.add("Согласные")
            BlockStrategy.FIRST_LAST -> parts.add("Первая+Последняя")
            BlockStrategy.FIRST_VOWELS -> parts.add("Гласные")
            BlockStrategy.SYLLABLE_LIKE -> parts.add("Слоги")
            BlockStrategy.INITIALS_ONLY -> parts.add("Инициалы")
        }
        
        when (spec.orderStrategy) {
            OrderStrategy.REVERSED -> parts.add("Обратный порядок")
            OrderStrategy.ALTERNATING -> parts.add("Чередование")
            OrderStrategy.LONG_FIRST -> parts.add("Длинные сначала")
            else -> {}
        }
        
        if (spec.leetStrategy != LeetStrategy.NONE) {
            parts.add("Leet")
        }
        
        when (spec.tokenOrderStrategy) {
            TokenOrderStrategy.SERVICE_PHRASE_ROTATION -> parts.add("Сервис в начале")
            TokenOrderStrategy.PHRASE_ROTATION_SERVICE -> parts.add("Сервис в конце")
            else -> {}
        }
        
        when (spec.rotationCodeStyle) {
            RotationCodeStyle.QYY -> parts.add("Квартальный код")
            RotationCodeStyle.MM_YY -> parts.add("Код через дефис")
            else -> {}
        }
        
        return parts.joinToString(" + ")
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
