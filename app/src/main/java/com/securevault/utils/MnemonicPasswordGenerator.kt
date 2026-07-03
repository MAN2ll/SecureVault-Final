package com.securevault.utils

import java.util.Calendar

/**
 * AMPG v2 — Unique Mnemonic Flow
 * Адаптивная мнемоническая генерация паролей с уникальным потоком символов.
 * 
 * Идея: алгоритм идёт по фразе слева направо и берёт буквы, которых ещё не было.
 * Некоторые гласные заменяются цифрами/символами по фиксированным правилам.
 * Благодаря этому пароль связан с фразой, но содержит меньше повторов.
 */
object MnemonicPasswordGenerator {

    data class GenerationResult(
        val password: String,
        val mnemonicHint: String,
        val strength: PasswordGenerator.Strength,
        val steps: List<String>,
        val variantName: String,
        val hasDuplicates: Boolean = false
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

    // ===== AMPG v2: фиксированные замены =====
    private val leetMap = mapOf(
        'a' to '@',
        'o' to '0',
        'e' to '3',
        'i' to '1',
        'u' to '^',
        's' to '$',
        'y' to '7'
    )

    // Мягкие замены (только гласные)
    private val softLeetMap = mapOf(
        'a' to '@',
        'o' to '0',
        'e' to '3',
        'i' to '1'
    )

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

    // ===== СТРАТЕГИИ ВАРИАНТОВ AMPG v2 =====
    enum class VariantStrategy {
        UNIQUE_FLOW_SOFT,      // Уникальный поток + мягкие замены
        UNIQUE_FLOW_FULL,      // Уникальный поток + полные замены
        SERVICE_FIRST,         // Сервис в начале
        ROTATION_FIRST,        // Ротация перед сервисом
        COMPACT_INITIALS       // Компактный из первых букв слов
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

    // ===== НОРМАЛИЗАЦИЯ ФРАЗЫ =====
    private fun normalizePhrase(phrase: String): String {
        return phrase.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    // ===== ПОСТРОЕНИЕ УНИКАЛЬНОГО ПОТОКА =====
    /**
     * Проходит по фразе слева направо и берёт только те буквы, которых ещё не было.
     * Сравнение без учёта регистра.
     */
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

    // ===== ПРИМЕНЕНИЕ LEEТ-ЗАМЕН =====
    private fun applyLeet(text: String, full: Boolean): String {
        val map = if (full) leetMap else softLeetMap
        return text.map { char ->
            map[char.lowercaseChar()] ?: char
        }.joinToString("")
    }

    // ===== КАПИТАЛИЗАЦИЯ =====
    private fun capitalizeFirst(text: String): String {
        if (text.isEmpty()) return text
        return text[0].uppercaseChar() + text.substring(1)
    }

    private fun capitalizeEachBlock(text: String, separator: Char = '-'): String {
        return text.split(separator).joinToString(separator.toString()) { part ->
            if (part.isEmpty()) part else part[0].uppercaseChar() + part.substring(1)
        }
    }

    // ===== КОД СЕРВИСА (уникальные символы) =====
    private fun buildServiceCode(serviceName: String, usedChars: Set<Char>): String {
        if (serviceName.isBlank()) return ""
        
        val transliterated = transliterate(serviceName.lowercase())
        val cleaned = transliterated.filter { it.isLetterOrDigit() }
        if (cleaned.isEmpty()) return ""
        
        val result = StringBuilder()
        val localUsed = usedChars.toMutableSet()
        
        // Берём уникальные буквы сервиса
        for (char in cleaned) {
            val lower = char.lowercaseChar()
            if (lower !in localUsed && result.length < 3) {
                result.append(lower)
                localUsed.add(lower)
            }
        }
        
        // Добавляем длину сервиса, если цифра ещё не использовалась
        val lengthDigit = cleaned.length.toString().first()
        if (lengthDigit !in localUsed) {
            result.append(lengthDigit)
            localUsed.add(lengthDigit)
        } else {
            // Берём ближайшую свободную цифру
            for (d in 1..9) {
                val dChar = d.toString().first()
                if (dChar !in localUsed) {
                    result.append(dChar)
                    localUsed.add(dChar)
                    break
                }
            }
        }
        
        return result.toString()
    }

    // ===== КОД РОТАЦИИ =====
    private fun buildRotationCode(month: Int?, year: Int?, usedChars: Set<Char>): String {
        val currentMonth = month ?: Calendar.getInstance().get(Calendar.MONTH) + 1
        val currentYear = year ?: (Calendar.getInstance().get(Calendar.YEAR) % 100)
        
        val mm = String.format("%02d", currentMonth)
        val yy = String.format("%02d", currentYear)
        
        // Проверяем, есть ли эти цифры уже в пароле
        val allDigits = mm + yy
        val hasConflict = allDigits.any { it in usedChars }
        
        return if (hasConflict) {
            // Используем формат QYY (квартал + год)
            val quarter = ((currentMonth - 1) / 3) + 1
            "Q$quarter$yy"
        } else {
            "$mm$yy"
        }
    }

    // ===== ДОБИВАНИЕ ДО TARGET LENGTH =====
    private fun padToLength(password: String, targetLength: Int, usedChars: Set<Char>): String {
        if (password.length >= targetLength) return password
        
        val localUsed = usedChars.toMutableSet()
        val result = StringBuilder(password)
        
        for (char in paddingChars) {
            if (result.length >= targetLength) break
            val lower = char.lowercaseChar()
            if (lower !in localUsed && char !in localUsed) {
                result.append(char)
                localUsed.add(char)
                localUsed.add(lower)
            }
        }
        
        return result.toString()
    }

    // ===== ПОДСЧЁТ ПОВТОРОВ =====
    private fun countDuplicates(password: String): Boolean {
        val seen = mutableSetOf<Char>()
        for (char in password) {
            val lower = char.lowercaseChar()
            if (lower in seen) return true
            seen.add(lower)
        }
        return false
    }

    // ===== ГЛАВНЫЙ МЕТОД: ГЕНЕРАЦИЯ ВАРИАНТОВ =====
    fun generateVariants(options: GenerationOptions, count: Int = 5): List<GenerationResult> {
        val results = mutableListOf<GenerationResult>()
        
        // 1. Нормализация
        val normalizedPhrase = normalizePhrase(options.phrase)
        if (normalizedPhrase.isBlank()) return emptyList()
        
        // 2. Транслитерация
        val transliterated = transliterate(normalizedPhrase)
        val words = normalizedPhrase.split(" ").filter { it.isNotBlank() }
        val transliteratedWords = words.map { transliterate(it) }
        
        // 3. Уникальный поток
        val uniqueFlow = buildUniqueFlow(transliterated)
        
        // 4. Генерируем 5 вариантов разными стратегиями
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
            
            if (result != null) {
                results.add(result)
            }
        }
        
        // Если вариантов меньше 5, пробуем с variantOffset
        var offset = options.variantOffset
        var attempts = 0
        while (results.size < count && attempts < 10) {
            val fallback = generateFallbackVariant(
                options = options,
                normalizedPhrase = normalizedPhrase,
                transliterated = transliterated,
                transliteratedWords = transliteratedWords,
                uniqueFlow = uniqueFlow,
                offset = offset
            )
            if (fallback != null && results.none { it.password == fallback.password }) {
                results.add(fallback)
            }
            offset++
            attempts++
        }
        
        return results
    }

    // ===== ГЕНЕРАЦИЯ С КОНКРЕТНОЙ СТРАТЕГИЕЙ =====
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
        
        return when (strategy) {
            VariantStrategy.UNIQUE_FLOW_SOFT -> {
                // Мягкие замены (только гласные)
                val leeted = applyLeet(uniqueFlow, full = false)
                steps.add("4. Мягкие замены: '$leeted'")
                
                for (c in leeted) usedChars.add(c.lowercaseChar())
                
                val capitalized = capitalizeFirst(leeted)
                val parts = mutableListOf(capitalized)
                
                if (options.includeServiceCode && options.serviceName.isNotBlank()) {
                    val serviceCode = buildServiceCode(options.serviceName, usedChars)
                    if (serviceCode.isNotBlank()) {
                        parts.add(serviceCode)
                        for (c in serviceCode) usedChars.add(c.lowercaseChar())
                    }
                    steps.add("5. Код сервиса: '$serviceCode'")
                }
                
                if (options.includeRotationCode) {
                    val rotationCode = buildRotationCode(options.rotationMonth, options.rotationYear, usedChars)
                    parts.add(rotationCode)
                    for (c in rotationCode) usedChars.add(c.lowercaseChar())
                    steps.add("6. Код ротации: '$rotationCode'")
                }
                
                var password = parts.joinToString("-")
                password = padToLength(password, options.targetLength, usedChars)
                steps.add("7. Сборка: '$password'")
                
                val hasDuplicates = countDuplicates(password)
                val hint = buildHint(normalizedPhrase, options, "мягкие замены")
                val variantName = "AMPG v2 — Уникальный поток + мягкие замены"
                
                GenerationResult(password, hint, calculateStrength(password), steps, variantName, hasDuplicates)
            }
            
            VariantStrategy.UNIQUE_FLOW_FULL -> {
                // Полные замены (гласные + s, y)
                val leeted = applyLeet(uniqueFlow, full = true)
                steps.add("4. Полные замены: '$leeted'")
                
                for (c in leeted) usedChars.add(c.lowercaseChar())
                
                val capitalized = capitalizeFirst(leeted)
                val parts = mutableListOf(capitalized)
                
                if (options.includeServiceCode && options.serviceName.isNotBlank()) {
                    val serviceCode = buildServiceCode(options.serviceName, usedChars)
                    if (serviceCode.isNotBlank()) {
                        parts.add(serviceCode)
                        for (c in serviceCode) usedChars.add(c.lowercaseChar())
                    }
                    steps.add("5. Код сервиса: '$serviceCode'")
                }
                
                if (options.includeRotationCode) {
                    val rotationCode = buildRotationCode(options.rotationMonth, options.rotationYear, usedChars)
                    parts.add(rotationCode)
                    for (c in rotationCode) usedChars.add(c.lowercaseChar())
                    steps.add("6. Код ротации: '$rotationCode'")
                }
                
                var password = parts.joinToString("-")
                password = padToLength(password, options.targetLength, usedChars)
                steps.add("7. Сборка: '$password'")
                
                val hasDuplicates = countDuplicates(password)
                val hint = buildHint(normalizedPhrase, options, "полные замены")
                val variantName = "AMPG v2 — Уникальный поток + полные замены"
                
                GenerationResult(password, hint, calculateStrength(password), steps, variantName, hasDuplicates)
            }
            
            VariantStrategy.SERVICE_FIRST -> {
                // Сервис в начале
                if (options.serviceName.isNotBlank()) {
                    val serviceCode = buildServiceCode(options.serviceName, usedChars)
                    if (serviceCode.isNotBlank()) {
                        val capitalizedService = capitalizeFirst(serviceCode)
                        for (c in capitalizedService) usedChars.add(c.lowercaseChar())
                        
                        val leeted = applyLeet(uniqueFlow, full = true)
                        for (c in leeted) usedChars.add(c.lowercaseChar())
                        
                        val parts = mutableListOf(capitalizedService, capitalizeFirst(leeted))
                        
                        if (options.includeRotationCode) {
                            val rotationCode = buildRotationCode(options.rotationMonth, options.rotationYear, usedChars)
                            parts.add(rotationCode)
                            for (c in rotationCode) usedChars.add(c.lowercaseChar())
                        }
                        
                        var password = parts.joinToString("-")
                        password = padToLength(password, options.targetLength, usedChars)
                        
                        val hasDuplicates = countDuplicates(password)
                        val hint = buildHint(normalizedPhrase, options, "сервис в начале")
                        val variantName = "AMPG v2 — Сервис в начале"
                        
                        return GenerationResult(password, hint, calculateStrength(password), steps, variantName, hasDuplicates)
                    }
                }
                null
            }
            
            VariantStrategy.ROTATION_FIRST -> {
                // Ротация в начале
                if (options.includeRotationCode) {
                    val rotationCode = buildRotationCode(options.rotationMonth, options.rotationYear, usedChars)
                    for (c in rotationCode) usedChars.add(c.lowercaseChar())
                    
                    val leeted = applyLeet(uniqueFlow, full = true)
                    for (c in leeted) usedChars.add(c.lowercaseChar())
                    
                    val parts = mutableListOf(rotationCode, capitalizeFirst(leeted))
                    
                    if (options.includeServiceCode && options.serviceName.isNotBlank()) {
                        val serviceCode = buildServiceCode(options.serviceName, usedChars)
                        if (serviceCode.isNotBlank()) {
                            parts.add(capitalizeFirst(serviceCode))
                        }
                    }
                    
                    var password = parts.joinToString("-")
                    password = padToLength(password, options.targetLength, usedChars)
                    
                    val hasDuplicates = countDuplicates(password)
                    val hint = buildHint(normalizedPhrase, options, "ротация в начале")
                    val variantName = "AMPG v2 — Ротация в начале"
                    
                    return GenerationResult(password, hint, calculateStrength(password), steps, variantName, hasDuplicates)
                }
                null
            }
            
            VariantStrategy.COMPACT_INITIALS -> {
                // Компактный: первые буквы слов + замены
                val initials = transliteratedWords.mapNotNull { it.firstOrNull() }.joinToString("")
                steps.add("4. Инициалы: '$initials'")
                
                val leeted = applyLeet(initials, full = true)
                for (c in leeted) usedChars.add(c.lowercaseChar())
                
                val capitalized = capitalizeFirst(leeted)
                val parts = mutableListOf(capitalized)
                
                if (options.includeServiceCode && options.serviceName.isNotBlank()) {
                    val serviceCode = buildServiceCode(options.serviceName, usedChars)
                    if (serviceCode.isNotBlank()) {
                        parts.add(serviceCode)
                        for (c in serviceCode) usedChars.add(c.lowercaseChar())
                    }
                }
                
                if (options.includeRotationCode) {
                    val rotationCode = buildRotationCode(options.rotationMonth, options.rotationYear, usedChars)
                    parts.add(rotationCode)
                    for (c in rotationCode) usedChars.add(c.lowercaseChar())
                }
                
                var password = parts.joinToString("-")
                password = padToLength(password, options.targetLength, usedChars)
                
                val hasDuplicates = countDuplicates(password)
                val hint = buildHint(normalizedPhrase, options, "инициалы слов")
                val variantName = "AMPG v2 — Инициалы слов"
                
                GenerationResult(password, hint, calculateStrength(password), steps, variantName, hasDuplicates)
            }
        }
    }

    // ===== FALLBACK ВАРИАНТ (с offset) =====
    private fun generateFallbackVariant(
        options: GenerationOptions,
        normalizedPhrase: String,
        transliterated: String,
        transliteratedWords: List<String>,
        uniqueFlow: String,
        offset: Int
    ): GenerationResult? {
        // Применяем разные комбинации в зависимости от offset
        val useFullLeet = offset % 2 == 0
        val serviceFirst = offset % 3 == 0
        
        val usedChars = mutableSetOf<Char>()
        val leeted = applyLeet(uniqueFlow, full = useFullLeet)
        for (c in leeted) usedChars.add(c.lowercaseChar())
        
        val parts = mutableListOf<String>()
        
        if (serviceFirst && options.serviceName.isNotBlank()) {
            val serviceCode = buildServiceCode(options.serviceName, usedChars)
            if (serviceCode.isNotBlank()) {
                parts.add(capitalizeFirst(serviceCode))
                for (c in serviceCode) usedChars.add(c.lowercaseChar())
            }
        }
        
        parts.add(capitalizeFirst(leeted))
        
        if (!serviceFirst && options.includeServiceCode && options.serviceName.isNotBlank()) {
            val serviceCode = buildServiceCode(options.serviceName, usedChars)
            if (serviceCode.isNotBlank()) {
                parts.add(serviceCode)
                for (c in serviceCode) usedChars.add(c.lowercaseChar())
            }
        }
        
        if (options.includeRotationCode) {
            val rotationCode = buildRotationCode(options.rotationMonth, options.rotationYear, usedChars)
            parts.add(rotationCode)
        }
        
        var password = parts.joinToString("-")
        password = padToLength(password, options.targetLength, usedChars)
        
        val hasDuplicates = countDuplicates(password)
        val hint = buildHint(normalizedPhrase, options, "вариант №${offset + 1}")
        val variantName = "AMPG v2 — Вариант №${offset + 1}"
        
        return GenerationResult(
            password, hint, calculateStrength(password),
            emptyList(), variantName, hasDuplicates
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

    // ===== СТАРЫЙ МЕТОД generate (для обратной совместимости) =====
    fun generate(options: GenerationOptions): GenerationResult {
        val variants = generateVariants(options, count = 1)
        return variants.firstOrNull() ?: GenerationResult(
            "Error", "Ошибка генерации", PasswordGenerator.Strength.WEAK, emptyList(), "Error"
        )
    }
}
