package com.securevault.utils

object MnemonicPasswordGenerator {

    enum class SplitMode {
        SINGLE_USER,
        TWO_USERS
    }

    data class GenerationOptions(
        val phrase: String,
        val serviceName: String = "",
        val username: String = "",
        val profileId: Int? = null,
        val targetLength: Int = 16,
        val rotationMonth: Int? = null,
        val rotationYear: Int? = null,
        val variantOffset: Int = 0,
        val splitMode: SplitMode = SplitMode.SINGLE_USER
    )

    data class GenerationResult(
        val password: String,
        val mnemonicHint: String,
        val variantName: String,
        val strength: PasswordGenerator.Strength,
        val part1: String?,
        val part2: String?,
        val splitMode: SplitMode,
        val explanation: String
    )

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

    private val digitPool = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    private val specialPool = listOf('@', '#', '$', '!', '%', '&', '*', '?')

    data class SegmentQuota(
        var upperCount: Int = 0,
        var digitCount: Int = 0,
        var specialCount: Int = 0
    )

    fun isValidVariant(password: String, splitMode: SplitMode): Boolean {
        if (password.isEmpty()) return false
        
        val lowerPassword = password.lowercase()
        if (lowerPassword.length != lowerPassword.toSet().size) return false

        if (splitMode == SplitMode.TWO_USERS) {
            if (password.length % 2 != 0) return false
            val half = password.length / 2
            val part1 = password.substring(0, half)
            val part2 = password.substring(half)
            return checkPartComplexity(part1) && checkPartComplexity(part2)
        } else {
            return checkPartComplexity(password)
        }
    }

    private fun checkPartComplexity(part: String): Boolean {
        val upper = part.count { it.isUpperCase() }
        val digit = part.count { it.isDigit() }
        val special = part.count { !it.isLetterOrDigit() }
        return upper >= 2 && digit >= 2 && special >= 2
    }

    fun generateVariants(options: GenerationOptions, count: Int = 3): List<GenerationResult> {
        val results = mutableListOf<GenerationResult>()
        val effectiveLength = if (options.splitMode == SplitMode.TWO_USERS) {
            when {
                options.targetLength <= 16 -> 16
                options.targetLength <= 18 -> 18
                else -> 20
            }
        } else {
            options.targetLength.coerceAtLeast(12)
        }

        var currentOffset = options.variantOffset
        val maxAttempts = 300

        while (results.size < count && currentOffset < options.variantOffset + maxAttempts) {
            val variantOptions = options.copy(variantOffset = currentOffset)
            val result = generateSingleVariant(variantOptions, effectiveLength)

            if (result != null && isValidVariant(result.password, options.splitMode)) {
                if (results.none { it.password == result.password }) {
                    results.add(result)
                }
            }
            currentOffset++
        }
        return results
    }

    private fun generateSingleVariant(options: GenerationOptions, targetLength: Int): GenerationResult? {
        val words = options.phrase.lowercase()
            .replace(Regex("[^а-яёa-z\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .distinct()

        if (words.isEmpty()) return null

        //  Seed теперь включает targetLength и splitMode
        val seedStr = "${options.phrase}${options.serviceName}${options.username}${options.profileId}${options.rotationMonth}${options.rotationYear}${targetLength}${options.splitMode}${options.variantOffset}"
        val seedHash = seedStr.hashCode()

        val globalUsedChars = mutableSetOf<Char>()
        val explanation = StringBuilder()
        explanation.append("Фраза: ${options.phrase}\n")
        explanation.append("Транслитерация: ${transliterate(options.phrase)}\n")
        explanation.append("Режим: ${if (options.splitMode == SplitMode.TWO_USERS) "Двухпользовательский" else "Обычный"}\n")
        explanation.append("Внутренние факторы: сервис, логин, профиль, ротация, длина и режим влияют на позиции замен внутри блоков.\n")

        val passwordBuilder = StringBuilder()
        var currentSeed = seedHash
        var wordIndex = 0

        if (options.splitMode == SplitMode.TWO_USERS) {
            val halfLength = targetLength / 2
            val segment1Quota = SegmentQuota()
            val segment2Quota = SegmentQuota()

            // Собираем первую половину
            while (passwordBuilder.length < halfLength && wordIndex < 100) {
                val word = words[wordIndex % words.size]
                val charsNeeded = halfLength - passwordBuilder.length
                val blockLength = minOf(4, charsNeeded)
                
                val blockResult = buildRhythmicBlockWithQuota(
                    word, currentSeed, blockLength, 
                    globalUsedChars, segment1Quota, explanation
                )
                if (blockResult == null) return null
                
                passwordBuilder.append(blockResult)
                currentSeed += 1
                wordIndex++
            }

            // Собираем вторую половину
            while (passwordBuilder.length < targetLength && wordIndex < 100) {
                val word = words[wordIndex % words.size]
                val charsNeeded = targetLength - passwordBuilder.length
                val blockLength = minOf(4, charsNeeded)
                
                val blockResult = buildRhythmicBlockWithQuota(
                    word, currentSeed, blockLength, 
                    globalUsedChars, segment2Quota, explanation
                )
                if (blockResult == null) return null
                
                passwordBuilder.append(blockResult)
                currentSeed += 1
                wordIndex++
            }

            var password = passwordBuilder.toString()
            
            //  Deterministic repair для первой половины
            password = repairSegment(password, 0, halfLength, segment1Quota, globalUsedChars, currentSeed)
            //  Deterministic repair для второй половины
            password = repairSegment(password, halfLength, targetLength, segment2Quota, globalUsedChars, currentSeed + 1000)
            
            if (password.length != targetLength) return null

            return GenerationResult(
                password = password,
                mnemonicHint = options.phrase.take(30),
                variantName = "Вариант ${options.variantOffset + 1}",
                strength = calculateStrength(password),
                part1 = password.substring(0, halfLength),
                part2 = password.substring(halfLength),
                splitMode = SplitMode.TWO_USERS,
                explanation = explanation.toString()
            )
        } else {
            // SINGLE_USER режим
            val quota = SegmentQuota()

            while (passwordBuilder.length < targetLength && wordIndex < 100) {
                val word = words[wordIndex % words.size]
                val charsNeeded = targetLength - passwordBuilder.length
                val blockLength = minOf(4, charsNeeded)
                
                val blockResult = buildRhythmicBlockWithQuota(
                    word, currentSeed, blockLength, 
                    globalUsedChars, quota, explanation
                )
                if (blockResult == null) return null
                
                passwordBuilder.append(blockResult)
                currentSeed += 1
                wordIndex++
            }

            var password = passwordBuilder.toString()
            password = repairSegment(password, 0, targetLength, quota, globalUsedChars, currentSeed)
            
            if (password.length != targetLength) return null

            return GenerationResult(
                password = password,
                mnemonicHint = options.phrase.take(30),
                variantName = "Вариант ${options.variantOffset + 1}",
                strength = calculateStrength(password),
                part1 = null,
                part2 = null,
                splitMode = SplitMode.SINGLE_USER,
                explanation = explanation.toString()
            )
        }
    }

    private fun buildRhythmicBlockWithQuota(
        word: String, seed: Int, targetLength: Int,
        globalUsedChars: MutableSet<Char>, quota: SegmentQuota,
        explanation: StringBuilder
    ): String? {
        val transliterated = transliterate(word)
        val base = transliterated.take(targetLength)
        val result = StringBuilder()

        for ((index, char) in base.withIndex()) {
            val isUpperCase = index == 0
            val baseChar = if (isUpperCase) char.uppercaseChar() else char
            
            val candidates = mutableListOf<Char>()
            
            //  Приоритет кандидатов зависит от квот
            if (quota.digitCount < 2) {
                candidates.addAll(digitPool)
            }
            if (quota.specialCount < 2) {
                candidates.addAll(specialPool)
            }
            
            if (char.lowercaseChar() in leetMap) {
                candidates.addAll(leetMap[char.lowercaseChar()]!!)
            }
            candidates.add(baseChar)
            if (isUpperCase && quota.upperCount < 2) {
                candidates.add(0, char.uppercaseChar())
            }

            val startIndex = (seed + index + candidates.size) % candidates.size
            var added = false
            
            for (i in 0 until candidates.size) {
                val candidate = candidates[(startIndex + i) % candidates.size]
                if (!globalUsedChars.contains(candidate.lowercaseChar())) {
                    result.append(candidate)
                    globalUsedChars.add(candidate.lowercaseChar())
                    
                    // Обновляем квоты
                    when {
                        candidate.isUpperCase() -> quota.upperCount++
                        candidate.isDigit() -> quota.digitCount++
                        !candidate.isLetterOrDigit() -> quota.specialCount++
                    }
                    
                    added = true
                    break
                }
            }

            if (!added) {
                val fallbacks = digitPool + specialPool
                val fbStartIndex = (seed + index + 100) % fallbacks.size
                for (i in 0 until fallbacks.size) {
                    val fb = fallbacks[(fbStartIndex + i) % fallbacks.size]
                    if (!globalUsedChars.contains(fb)) {
                        result.append(fb)
                        globalUsedChars.add(fb)
                        if (fb.isDigit()) quota.digitCount++
                        else quota.specialCount++
                        added = true
                        break
                    }
                }
            }

            if (!added) return null
        }
        
        explanation.append("Блок '${word}' -> ${result}\n")
        return result.toString()
    }

    private fun repairSegment(
        password: String, startIdx: Int, endIdx: Int,
        quota: SegmentQuota, globalUsedChars: MutableSet<Char>, seed: Int
    ): String {
        val chars = password.toCharArray()
        var repairSeed = seed

        //  Ремонт недостающих цифр
        while (quota.digitCount < 2) {
            val pos = (repairSeed % (endIdx - startIdx)) + startIdx
            val currentChar = chars[pos]
            
            if (currentChar.isLetter()) {
                for (digit in digitPool) {
                    if (!globalUsedChars.contains(digit)) {
                        globalUsedChars.remove(currentChar.lowercaseChar())
                        chars[pos] = digit
                        globalUsedChars.add(digit)
                        quota.digitCount++
                        break
                    }
                }
            }
            repairSeed++
        }

        //  Ремонт недостающих спецсимволов
        while (quota.specialCount < 2) {
            val pos = (repairSeed % (endIdx - startIdx)) + startIdx
            val currentChar = chars[pos]
            
            if (currentChar.isLetterOrDigit()) {
                for (special in specialPool) {
                    if (!globalUsedChars.contains(special)) {
                        globalUsedChars.remove(currentChar.lowercaseChar())
                        chars[pos] = special
                        globalUsedChars.add(special)
                        quota.specialCount++
                        break
                    }
                }
            }
            repairSeed++
        }

        //  Ремонт недостающих заглавных
        while (quota.upperCount < 2) {
            val pos = (repairSeed % (endIdx - startIdx)) + startIdx
            val currentChar = chars[pos]
            
            if (currentChar.isLowerCase()) {
                val upper = currentChar.uppercaseChar()
                if (!globalUsedChars.contains(upper.lowercaseChar())) {
                    globalUsedChars.remove(currentChar.lowercaseChar())
                    chars[pos] = upper
                    globalUsedChars.add(upper.lowercaseChar())
                    quota.upperCount++
                }
            }
            repairSeed++
        }

        return String(chars)
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
        return text.map { char -> map[char] ?: char.toString() }.joinToString("")
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
