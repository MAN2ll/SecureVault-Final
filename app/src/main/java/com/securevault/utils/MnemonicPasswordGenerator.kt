package com.securevault.utils

import java.util.Calendar

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
        'a' to '@', 'o' to '0', 'e' to '3', 'i' to '1',
        'u' to '^', 's' to '$', 'y' to '7'
    )

    private val softLeetMap = mapOf(
        'a' to '@', 'o' to '0', 'e' to '3', 'i' to '1'
    )

    private val extendedLeetMap = mapOf(
        'a' to '@', 'o' to '0', 'e' to '3', 'i' to '1',
        'u' to '^', 's' to '$', 'y' to '7', 't' to '+',
        'l' to '!', 'b' to '8', 'g' to '9', 'z' to '2'
    )

    private val separators = listOf('-', '.', '_', '~')
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

    private fun appendUniqueOrReplacement(
        builder: StringBuilder,
        rawChar: Char,
        usedChars: MutableSet<Char>,
        leetMap: Map<Char, Char>
    ) {
        val lower = rawChar.lowercaseChar()
        
        if (lower !in usedChars && rawChar !in usedChars) {
            builder.append(rawChar)
            usedChars.add(lower)
            usedChars.add(rawChar)
            return
        }
        
        val replacement = leetMap[lower]
        if (replacement != null && replacement !in usedChars) {
            builder.append(replacement)
            usedChars.add(replacement)
            usedChars.add(replacement.lowercaseChar())
            return
        }
    }

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
    }

    // Распределённые замены по всему паролю
    private fun applyDistributedReplacements(
        source: String,
        usedChars: MutableSet<Char>,
        targetRatio: Double = 0.30,
        variantOffset: Int = 0
    ): String {
        if (source.isEmpty()) return source
        
        val targetReplacementCount = maxOf(1, (source.length * targetRatio).toInt())
        val result = StringBuilder()
        val localUsed = usedChars.toMutableSet()
        
        val positions = mutableListOf<Int>()
        val third = source.length / 3
        
        for (i in 0 until third) {
            if ((i + variantOffset) % 3 == 0 && positions.size < targetReplacementCount) {
                positions.add(i)
            }
        }
        
        for (i in third until 2 * third) {
            if ((i + variantOffset) % 3 == 0 && positions.size < targetReplacementCount) {
                positions.add(i)
            }
        }
        
        for (i in 2 * third until source.length) {
            if ((i + variantOffset) % 3 == 0 && positions.size < targetReplacementCount) {
                positions.add(i)
            }
        }
        
        for (i in source.indices) {
            val char = source[i]
            val lower = char.lowercaseChar()
            
            if (i in positions) {
                val replacement = extendedLeetMap[lower]
                if (replacement != null && replacement !in localUsed) {
                    result.append(replacement)
                    localUsed.add(replacement)
                    localUsed.add(replacement.lowercaseChar())
                } else if (lower !in localUsed) {
                    result.append(char)
                    localUsed.add(lower)
                    localUsed.add(char)
                }
            } else {
                if (lower !in localUsed && char !in localUsed) {
                    result.append(char)
                    localUsed.add(lower)
                    localUsed.add(char)
                }
            }
        }
        
        usedChars.clear()
        usedChars.addAll(localUsed)
        
        return result.toString()
    }

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
                val replacement = extendedLeetMap[lower]
                if (replacement != null && replacement !in usedChars) {
                    builder.append(replacement)
                    usedChars.add(replacement)
                    usedChars.add(replacement.lowercaseChar())
                }
            }
        }
        
        return padToLength(builder.toString(), targetLength, usedChars)
    }

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
            
            if (result != null && !PasswordValidator.hasDuplicateCharacters(result.password)) {
                results.add(result)
            }
        }
        
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
        
        return when (strategy) {
            VariantStrategy.UNIQUE_FLOW_SOFT -> {
                val processedFlow = applyDistributedReplacements(uniqueFlow, usedChars, 0.30, options.variantOffset)
                builder.append(processedFlow)
                steps.add("4. Уникальный поток + распределённые замены: '${builder}'")
                
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
                
                val hint = buildHint(normalizedPhrase, options, "мягкие замены")
                val variantName = "AMPG v2 — Уникальный поток + мягкие замены"
                
                GenerationResult(password, hint, calculateStrength(password), steps, variantName)
            }
            
            VariantStrategy.UNIQUE_FLOW_FULL -> {
                val processedFlow = applyDistributedReplacements(uniqueFlow, usedChars, 0.30, options.variantOffset + 1)
                builder.append(processedFlow)
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
                    
                    if (builder.isNotEmpty()) {
                        val firstChar = builder[0]
                        if (firstChar.isLetter() && firstChar.isLowerCase()) {
                            builder.setCharAt(0, firstChar.uppercaseChar())
                            usedChars.remove(firstChar)
                            usedChars.add(firstChar.uppercaseChar())
                        }
                    }
                    
                    appendUniqueSeparator(builder, usedChars)
                    
                    val processedFlow = applyDistributedReplacements(uniqueFlow, usedChars, 0.30, options.variantOffset + 2)
                    builder.append(processedFlow)
                    
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
                    
                    val processedFlow = applyDistributedReplacements(uniqueFlow, usedChars, 0.30, options.variantOffset + 3)
                    builder.append(processedFlow)
                    
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
                
                val processedFlow = applyDistributedReplacements(initials, usedChars, 0.30, options.variantOffset + 4)
                builder.append(processedFlow)
                
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

    private fun appendServiceCode(
        builder: StringBuilder,
        serviceName: String,
        usedChars: MutableSet<Char>
    ) {
        val transliterated = transliterate(serviceName.lowercase())
        val cleaned = transliterated.filter { it.isLetterOrDigit() }
        if (cleaned.isEmpty()) return
        
        for (char in cleaned) {
            if (builder.length >= 4) break
            appendUniqueOrReplacement(builder, char, usedChars, emptyMap())
        }
        
        val lengthDigit = cleaned.length.toString().first()
        appendUniqueOrReplacement(builder, lengthDigit, usedChars, emptyMap())
    }

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
        
        val allDigits = mm + yy
        val hasConflict = allDigits.any { it in usedChars }
        
        if (hasConflict) {
            val quarter = ((currentMonth - 1) / 3) + 1
            appendUniqueOrReplacement(builder, 'Q', usedChars, emptyMap())
            appendUniqueOrReplacement(builder, quarter.toString().first(), usedChars, emptyMap())
            for (char in yy) {
                appendUniqueOrReplacement(builder, char, usedChars, emptyMap())
            }
        } else {
            for (char in mm + yy) {
                appendUniqueOrReplacement(builder, char, usedChars, emptyMap())
            }
        }
    }

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
        
        if (serviceFirst && options.serviceName.isNotBlank()) {
            appendServiceCode(builder, options.serviceName, usedChars)
            appendUniqueSeparator(builder, usedChars)
        }
        
        val processedFlow = applyDistributedReplacements(uniqueFlow, usedChars, 0.30, offset)
        builder.append(processedFlow)
        
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
