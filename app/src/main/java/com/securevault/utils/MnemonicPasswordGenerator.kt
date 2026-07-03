package com.securevault.utils

import java.util.Calendar

/**
 * AMPG v2 — Unique Mnemonic Flow
 * Гарантированная генерация паролей БЕЗ повторяющихся символов.
 * 
 * Логика:
 * 1. Строим уникальный поток из фразы
 * 2. Применяем замены только если символ свободен
 * 3. Добавляем сервис и ротацию через безопасный механизм
 * 4. Используем разные разделители
 * 5. Финальная очистка от повторов
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

    // ===== ФИКСИРОВАННЫЕ ЗАМЕНЫ =====
    private val leetMap = mapOf(
        'a' to '@', 'o' to '0', 'e' to '3', 'i' to '1',
        'u' to '^', 's' to '$', 'y' to '7'
    )

    private val softLeetMap = mapOf(
        'a' to '@', 'o' to '0', 'e' to '3', 'i' to '1'
    )

    // Разделители (используем по одному)
    private val separators = listOf('-', '.', '_', '~')

    // Суффиксный набор для добивания
    private val paddingChars = "ZXCVBNM2468!#%"

    private val translitMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch",
        'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    enum class VariantStrategy {
        UNIQUE_FLOW_SOFT,
        UNIQUE_FLOW_FULL,
        SERVICE_FIRST,
        ROTATION_FIRST,
        COMPACT_INITIALS
    }

    // ===== ТРАНСЛИТЕРАЦИЯ =====
    private fun transliterate(text: String): String {
        val result = StringBuilder()
        for (char in text) {
            val lower = char.lowercaseChar()
            if (lower in translitMap) {
                result.append(translitMap[lower])
            } else if (char.isLetterOrDigit()) {
                result.append(lower)
            } else if (char.isWhitespace()) {
                result.append(' ')
            }
        }
        return result.toString()
    }

    private fun normalizePhrase(phrase: String): String {
        return phrase.trim().replace(Regex("\\s+"), " ").lowercase()
    }

    // ===== УНИКАЛЬНЫЙ ПОТОК =====
    private fun buildUniqueFlow(transliteratedPhrase: String): String {
        val seen = mutableSetOf<Char>()
        val result = StringBuilder()
        
        for (char in transliteratedPhrase) {
            if (char.isWhitespace() || char == '-') continue
            if (!char.isLetterOrDigit()) continue
            
            val lower = char.lowercaseChar()
            if (lower !in seen) {
                seen.add(lower)
                result.append(lower)
            }
        }
        
        return result.toString()
    }

    // ===== БЕЗОПАСНОЕ ДОБАВЛЕНИЕ СИМВОЛА =====
    /**
     * Добавляет символ в builder, если он свободен.
     * Если занят — пытается добавить замену.
     * Если замена занята — пропускает.
     */
    private fun appendUniqueOrReplacement(
        builder: StringBuilder,
        rawChar: Char,
        usedChars: MutableSet<Char>,
        leetMap: Map<Char, Char>
    ) {
        val lower = rawChar.lowercaseChar()
        
        // Если символ свободен — добавляем
        if (lower !in usedChars && rawChar !in usedChars) {
            builder.append(rawChar)
            usedChars.add(lower)
            usedChars.add(rawChar)
            return
        }
        
        // Если занят — пробуем замену
        val replacement = leetMap[lower]
        if (replacement != null && replacement !in usedChars) {
            builder.append(replacement)
            usedChars.add(replacement)
            usedChars.add(replacement.lowercaseChar())
            return
        }
        
        // Если всё занято — пропускаем
    }

    // ===== БЕЗОПАСНОЕ ДОБАВЛЕНИЕ РАЗДЕЛИТЕЛЯ =====
    private fun appendUniqueSeparator(
        builder: StringBuilder,
        usedChars: MutableSet<Char>
    ) {
        for (sep in separators) {
            if (sep !in usedChars) {
                builder.append(sep)
                usedChars.add(sep)
                return
            }
        }
        // Если все разделители заняты — не добавляем
    }

    // ===== ДОБИВАНИЕ ДО TARGET LENGTH =====
    private fun padToLength(
        password: String,
        targetLength: Int,
        usedChars: MutableSet<Char>
    ): String {
        if (password.length >= targetLength) return password
        
        val builder = StringBuilder(password)
        
        for (char in paddingChars) {
            if (builder.length >= targetLength) break
            val lower = char.lowercaseChar()
            if (lower !in usedChars && char !in usedChars) {
                builder.append(char)
                usedChars.add(char)
                usedChars.add(lower)
            }
        }
        
        return builder.toString()
    }

    // ===== ФИНАЛЬНАЯ ОЧИСТКА =====
    /**
     * Проходит по паролю и удаляет/заменяет повторы.
     * Гарантирует отсутствие дубликатов.
     */
    private fun sanitizeToUniquePassword(
        password: String,
        targetLength: Int
    ): String {
        val usedChars = mutableSetOf<Char>()
        val builder = StringBuilder()
        
        for (char in password) {
            val lower = char.lowercaseChar()
            
            if (lower !in usedChars && char !in usedChars) {
                builder.append(char)
                usedChars.add(lower)
                usedChars.add(char)
            } else {
                // Пытаемся заменить
                val replacement = leetMap[lower]
                if (replacement != null && replacement !in usedChars) {
                    builder.append(replacement)
                    usedChars.add(replacement)
                    usedChars.add(replacement.lowercaseChar())
                }
                // Если замена невозможна — пропускаем
            }
        }
        
        // Добиваем до targetLength, если нужно
        return padToLength(builder.toString(), targetLength, usedChars)
    }

    // ===== ГЛАВНЫЙ МЕТОД: ГЕНЕРАЦИЯ ВАРИАНТОВ =====
    fun generateVariants(options: GenerationOptions, count: Int = 5): List<GenerationResult> {
        val results = mutableListOf<GenerationResult>()
        
        val normalizedPhrase = normalizePhrase(options.phrase)
        if (normalizedPhrase.isBlank()) return emptyList()
        
        val transliterated = transliterate(normalizedPhrase)
        val words = normalizedPhrase.split(" ").filter { it.isNotBlank() }
        val transliteratedWords = words.map { transliterate(it) }
        val uniqueFlow = buildUniqueFlow(transliterated)
        
        val strategies = listOf(
            VariantStrategy.UNIQUE_FLOW_SOFT,
            VariantStrategy.UNIQUE_FLOW_FULL,
            VariantStrategy.SERVICE_FIRST,
            VariantStrategy.ROTATION_FIRST,
            VariantStrategy.COMPACT_INITIALS
        )
        
        for (strategy in strategies) {
            if (results.size >= count) break
            
            val result = generateWithStrategy(
                options = options,
                strategy = strategy,
                normalizedPhrase = normalizedPhrase,
                transliterated = transliterated,
                transliteratedWords = transliteratedWords,
                uniqueFlow = uniqueFlow
            )
            
            //  принимаем только варианты без повторов
            if (result != null && !PasswordValidator.hasDuplicateCharacters(result.password)) {
                results.add(result)
            }
        }
        
        // Если вариантов меньше 5, пробуем fallback
        var offset = options.variantOffset
        var attempts = 0
        while (results.size < count && attempts < 20) {
            val fallback = generateFallbackVariant(
                options = options,
                normalizedPhrase = normalizedPhrase,
                transliterated = transliterated,
                transliteratedWords = transliteratedWords,
                uniqueFlow = uniqueFlow,
                offset = offset
            )
            
            if (fallback != null && 
                !PasswordValidator.hasDuplicateCharacters(fallback.password) &&
                results.none { it.password == fallback.password }) {
                results.add(fallback)
            }
            offset++
            attempts++
        }
        
        return results
    }

    // ===== ГЕНЕРАЦИЯ С СТРАТЕГИЕЙ =====
    private fun generateWithStrategy(
        options: GenerationOptions,
        strategy: VariantStrategy,
        normalizedPhrase: String,
        transliterated: String,
        transliteratedWords: List<String>,
        uniqueFlow: String
    ): GenerationResult? {
        val steps = mutableListOf<String>()
        steps.add("1. Нормализация: '$normalizedPhrase'")
        steps.add("2. Транслитерация: '$transliterated'")
        steps.add("3. Уникальный поток: '$uniqueFlow'")
        
        val usedChars = mutableSetOf<Char>()
        val builder = StringBuilder()
        val activeLeetMap = if (options.includeLeet) leetMap else emptyMap()
        
        return when (strategy) {
            VariantStrategy.UNIQUE_FLOW_SOFT -> {
                // Обрабатываем uniqueFlow посимвольно
                for (char in uniqueFlow) {
                    appendUniqueOrReplacement(builder, char, usedChars, softLeetMap)
                }
                steps.add("4. Уникальный поток + мягкие замены: '${builder}'")
                
                // Заглавная первая буква
                if (builder.isNotEmpty()) {
                    val firstChar = builder[0]
                    if (firstChar.isLetter() && firstChar.isLowerCase()) {
                        builder.setCharAt(0, firstChar.uppercaseChar())
                        usedChars.remove(firstChar)
                        usedChars.add(firstChar.uppercaseChar())
                    }
                }
                
                // Добавляем сервис
                if (options.includeServiceCode && options.serviceName.isNotBlank()) {
                    appendUniqueSeparator(builder, usedChars)
                    appendServiceCode(builder, options.serviceName, usedChars)
                }
                
                // Добавляем ротацию
                if (options.includeRotationCode) {
                    appendUniqueSeparator(builder, usedChars)
                    appendRotationCode(builder, options.rotationMonth, options.rotationYear, usedChars)
                }
                
                var password = builder.toString()
                password = sanitizeToUniquePassword(password, options.targetLength)
                steps.add("5. Финальная сборка: '$password'")
                
                //  ФИНАЛЬНАЯ ПРОВЕРКА
                if (PasswordValidator.hasDuplicateCharacters(password)) {
                    return null
                }
                
                val hint = buildHint(normalizedPhrase, options, "мягкие замены")
                val variantName = "AMPG v2 — Уникальный поток + мягкие замены"
                
                GenerationResult(password, hint, calculateStrength(password), steps, variantName)
            }
            
            VariantStrategy.UNIQUE_FLOW_FULL -> {
                for (char in uniqueFlow) {
                    appendUniqueOrReplacement(builder, char, usedChars, leetMap)
                }
                steps.add("4. Уникальный поток + полные замены: '${builder}'")
                
                if (builder.isNotEmpty()) {
                    val firstChar = builder[0]
                    if (firstChar.isLetter() && firstChar.isLowerCase()) {
                        builder.setCharAt(0, firstChar.uppercaseChar())
                        usedChars.remove(firstChar)
                        usedChars.add(firstChar.uppercaseChar())
                    }
                }
                
                if (options.includeServiceCode && options.serviceName.isNotBlank()) {
                    appendUniqueSeparator(builder, usedChars)
                    appendServiceCode(builder, options.serviceName, usedChars)
                }
                
                if (options.includeRotationCode) {
                    appendUniqueSeparator(builder, usedChars)
                    appendRotationCode(builder, options.rotationMonth, options.rotationYear, usedChars)
                }
                
                var password = builder.toString()
                password = sanitizeToUniquePassword(password, options.targetLength)
                steps.add("5. Финальная сборка: '$password'")
                
                if (PasswordValidator.hasDuplicateCharacters(password)) {
                    return null
                }
                
                val hint = buildHint(normalizedPhrase, options, "полные замены")
                val variantName = "AMPG v2 — Уникальный поток + полные замены"
                
                GenerationResult(password, hint, calculateStrength(password), steps, variantName)
            }
            
            VariantStrategy.SERVICE_FIRST -> {
                if (options.serviceName.isNotBlank()) {
                    appendServiceCode(builder, options.serviceName, usedChars)
                    
                    // Заглавная первая буква
                    if (builder.isNotEmpty()) {
                        val firstChar = builder[0]
                        if (firstChar.isLetter() && firstChar.isLowerCase()) {
                            builder.setCharAt(0, firstChar.uppercaseChar())
                            usedChars.remove(firstChar)
                            usedChars.add(firstChar.uppercaseChar())
                        }
                    }
                    
                    appendUniqueSeparator(builder, usedChars)
                    
                    for (char in uniqueFlow) {
                        appendUniqueOrReplacement(builder, char, usedChars, leetMap)
                    }
                    
                    if (options.includeRotationCode) {
                        appendUniqueSeparator(builder, usedChars)
                        appendRotationCode(builder, options.rotationMonth, options.rotationYear, usedChars)
                    }
                    
                    var password = builder.toString()
                    password = sanitizeToUniquePassword(password, options.targetLength)
                    
                    if (PasswordValidator.hasDuplicateCharacters(password)) {
                        return null
                    }
                    
                    val hint = buildHint(normalizedPhrase, options, "сервис в начале")
                    val variantName = "AMPG v2 — Сервис в начале"
                    
                    return GenerationResult(password, hint, calculateStrength(password), steps, variantName)
                }
                null
            }
            
            VariantStrategy.ROTATION_FIRST -> {
                if (options.includeRotationCode) {
                    appendRotationCode(builder, options.rotationMonth, options.rotationYear, usedChars)
                    
                    appendUniqueSeparator(builder, usedChars)
                    
                    for (char in uniqueFlow) {
                        appendUniqueOrReplacement(builder, char, usedChars, leetMap)
                    }
                    
                    if (options.includeServiceCode && options.serviceName.isNotBlank()) {
                        appendUniqueSeparator(builder, usedChars)
                        appendServiceCode(builder, options.serviceName, usedChars)
                    }
                    
                    var password = builder.toString()
                    password = sanitizeToUniquePassword(password, options.targetLength)
                    
                    if (PasswordValidator.hasDuplicateCharacters(password)) {
                        return null
                    }
                    
                    val hint = buildHint(normalizedPhrase, options, "ротация в начале")
                    val variantName = "AMPG v2 — Ротация в начале"
                    
                    return GenerationResult(password, hint, calculateStrength(password), steps, variantName)
                }
                null
            }
            
            VariantStrategy.COMPACT_INITIALS -> {
                val initials = transliteratedWords.mapNotNull { it.firstOrNull() }.joinToString("")
                steps.add("4. Инициалы: '$initials'")
                
                for (char in initials) {
                    appendUniqueOrReplacement(builder, char, usedChars, leetMap)
                }
                
                if (builder.isNotEmpty()) {
                    val firstChar = builder[0]
                    if (firstChar.isLetter() && firstChar.isLowerCase()) {
                        builder.setCharAt(0, firstChar.uppercaseChar())
                        usedChars.remove(firstChar)
                        usedChars.add(firstChar.uppercaseChar())
                    }
                }
                
                if (options.includeServiceCode && options.serviceName.isNotBlank()) {
                    appendUniqueSeparator(builder, usedChars)
                    appendServiceCode(builder, options.serviceName, usedChars)
                }
                
                if (options.includeRotationCode) {
                    appendUniqueSeparator(builder, usedChars)
                    appendRotationCode(builder, options.rotationMonth, options.rotationYear, usedChars)
                }
                
                var password = builder.toString()
                password = sanitizeToUniquePassword(password, options.targetLength)
                
                if (PasswordValidator.hasDuplicateCharacters(password)) {
                    return null
                }
                
                val hint = buildHint(normalizedPhrase, options, "инициалы слов")
                val variantName = "AMPG v2 — Инициалы слов"
                
                GenerationResult(password, hint, calculateStrength(password), steps, variantName)
            }
        }
    }

    // ===== ДОБАВЛЕНИЕ КОДА СЕРВИСА =====
    private fun appendServiceCode(
        builder: StringBuilder,
        serviceName: String,
        usedChars: MutableSet<Char>
    ) {
        val transliterated = transliterate(serviceName.lowercase())
        val cleaned = transliterated.filter { it.isLetterOrDigit() }
        if (cleaned.isEmpty()) return
        
        // Берём уникальные буквы сервиса
        for (char in cleaned) {
            if (builder.length >= 4) break
            appendUniqueOrReplacement(builder, char, usedChars, emptyMap())
        }
        
        // Добавляем длину сервиса
        val lengthDigit = cleaned.length.toString().first()
        appendUniqueOrReplacement(builder, lengthDigit, usedChars, emptyMap())
    }

    // ===== ДОБАВЛЕНИЕ КОДА РОТАЦИИ =====
    private fun appendRotationCode(
        builder: StringBuilder,
        month: Int?,
        year: Int?,
        usedChars: MutableSet<Char>
    ) {
        val currentMonth = month ?: Calendar.getInstance().get(Calendar.MONTH) + 1
        val currentYear = year ?: (Calendar.getInstance().get(Calendar.YEAR) % 100)
        
        val mm = String.format("%02d", currentMonth)
        val yy = String.format("%02d", currentYear)
        
        // Проверяем, есть ли эти цифры уже в пароле
        val allDigits = mm + yy
        val hasConflict = allDigits.any { it in usedChars }
        
        if (hasConflict) {
            // Используем формат QYY
            val quarter = ((currentMonth - 1) / 3) + 1
            appendUniqueOrReplacement(builder, 'Q', usedChars, emptyMap())
            appendUniqueOrReplacement(builder, quarter.toString().first(), usedChars, emptyMap())
            for (char in yy) {
                appendUniqueOrReplacement(builder, char, usedChars, emptyMap())
            }
        } else {
            // Используем формат MMYY
            for (char in mm + yy) {
                appendUniqueOrReplacement(builder, char, usedChars, emptyMap())
            }
        }
    }

    // ===== FALLBACK ВАРИАНТ =====
    private fun generateFallbackVariant(
        options: GenerationOptions,
        normalizedPhrase: String,
        transliterated: String,
        transliteratedWords: List<String>,
        uniqueFlow: String,
        offset: Int
    ): GenerationResult? {
        val useFullLeet = offset % 2 == 0
        val serviceFirst = offset % 3 == 0
        
        val usedChars = mutableSetOf<Char>()
        val builder = StringBuilder()
        val activeLeetMap = if (useFullLeet) leetMap else softLeetMap
        
        if (serviceFirst && options.serviceName.isNotBlank()) {
            appendServiceCode(builder, options.serviceName, usedChars)
            appendUniqueSeparator(builder, usedChars)
        }
        
        for (char in uniqueFlow) {
            appendUniqueOrReplacement(builder, char, usedChars, activeLeetMap)
        }
        
        if (!serviceFirst && options.includeServiceCode && options.serviceName.isNotBlank()) {
            appendUniqueSeparator(builder, usedChars)
            appendServiceCode(builder, options.serviceName, usedChars)
        }
        
        if (options.includeRotationCode) {
            appendUniqueSeparator(builder, usedChars)
            appendRotationCode(builder, options.rotationMonth, options.rotationYear, usedChars)
        }
        
        var password = builder.toString()
        password = sanitizeToUniquePassword(password, options.targetLength)
        
        if (PasswordValidator.hasDuplicateCharacters(password)) {
            return null
        }
        
        val hint = buildHint(normalizedPhrase, options, "вариант №${offset + 1}")
        val variantName = "AMPG v2 — Вариант №${offset + 1}"
        
        return GenerationResult(
            password, hint, calculateStrength(password),
            emptyList(), variantName
        )
    }

    // ===== ПОСТРОЕНИЕ ПОДСКАЗКИ =====
    private fun buildHint(
        normalizedPhrase: String,
        options: GenerationOptions,
        strategyDesc: String
    ): String {
        val phrasePreview = if (normalizedPhrase.length > 30) {
            normalizedPhrase.take(30) + "..."
        } else {
            normalizedPhrase
        }
        
        val parts = mutableListOf<String>()
        parts.add(phrasePreview)
        
        if (options.includeServiceCode && options.serviceName.isNotBlank()) {
            parts.add(options.serviceName)
        }
        
        if (options.includeRotationCode) {
            parts.add("ротация")
        }
        
        parts.add("правило уникального потока")
        parts.add(strategyDesc)
        
        return parts.joinToString(" + ")
    }

    // ===== РАСЧЁТ СЛОЖНОСТИ =====
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

    fun generate(options: GenerationOptions): GenerationResult {
        val variants = generateVariants(options, count = 1)
        return variants.firstOrNull() ?: GenerationResult(
            "Error", "Ошибка генерации", PasswordGenerator.Strength.WEAK, emptyList(), "Error"
        )
    }
}
