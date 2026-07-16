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
        val explanation: String,
        val variantOffset: Int = 0
    )

    //  Информация о блоке для объяснения
    data class BlockInfo(
        val word: String,
        val block: String,
        val reason: String
    )

    //  Результат выбора якоря
    data class AnchorResult(
        val char: Char,
        val reason: String
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
    private val reserveSymbols = listOf('!', '#', '%', '&', '?', '*')

    data class SegmentQuota(
        var upperCount: Int = 0,
        var digitCount: Int = 0,
        var specialCount: Int = 0
    )

    private fun positiveMod(value: Int, size: Int): Int {
        return Math.floorMod(value, size)
    }

    private fun calculateSegmentQuota(chars: CharArray, startIdx: Int, endIdx: Int): SegmentQuota {
        val quota = SegmentQuota()
        for (i in startIdx until endIdx) {
            val c = chars[i]
            when {
                c.isUpperCase() -> quota.upperCount++
                c.isDigit() -> quota.digitCount++
                !c.isLetterOrDigit() -> quota.specialCount++
            }
        }
        return quota
    }

    private fun determineScheme(variantOffset: Int): String {
        return when (positiveMod(variantOffset, 3)) {
            0 -> "Гласные"
            1 -> "Края блоков"
            else -> "Смешанная"
        }
    }

    private fun describeScheme(scheme: String): String {
        return when (scheme) {
            "Гласные" -> "Усилены замены на гласных: o → 0, a/я → @, e → 3."
            "Края блоков" -> "Усилены начало и конец каждого блока."
            else -> "Используются и гласные, и края блоков, и добор сложности."
        }
    }

    // ✅ НОВОЕ: Логика выбора якоря слова
    private fun findAnchor(transliteratedWord: String, globalUsedChars: MutableSet<Char>): AnchorResult? {
        // 1. Пробуем заглавные буквы из слова слева направо
        for (c in transliteratedWord) {
            val upper = c.uppercaseChar()
            if (!globalUsedChars.contains(upper.lowercaseChar())) {
                globalUsedChars.add(upper.lowercaseChar())
                val reason = if (c.lowercaseChar() == transliteratedWord.first().lowercaseChar()) {
                    "первая буква слова свободна"
                } else {
                    "первая буква уже использована, выбрана следующая свободная буква '$c'"
                }
                return AnchorResult(upper, reason)
            }
        }
        
        // 2. Пробуем понятные leet-замены букв этого слова
        for (c in transliteratedWord) {
            val lowerC = c.lowercaseChar()
            if (lowerC in leetMap) {
                for (leetChar in leetMap[lowerC]!!) {
                    if (!globalUsedChars.contains(leetChar)) {
                        globalUsedChars.add(leetChar)
                        return AnchorResult(leetChar, "все буквы слова заняты, использована leet-замена '$leetChar' для буквы '$c'")
                    }
                }
            }
        }
        
        // 3. Крайний случай: резервный символ
        for (sym in reserveSymbols) {
            if (!globalUsedChars.contains(sym)) {
                globalUsedChars.add(sym)
                return AnchorResult(sym, "все буквы и их замены заняты, использован резервный символ '$sym'")
            }
        }
        
        return null
    }

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

        val seedStr = "${options.phrase}${options.serviceName}${options.username}${options.profileId}${options.rotationMonth}${options.rotationYear}${targetLength}${options.splitMode}${options.variantOffset}"
        val seedHash = seedStr.hashCode()

        val globalUsedChars = mutableSetOf<Char>()
        val passwordBuilder = StringBuilder()
        var currentSeed = seedHash
        var wordIndex = 0

        val blockInfos = mutableListOf<BlockInfo>()

        if (options.splitMode == SplitMode.TWO_USERS) {
            val halfLength = targetLength / 2
            val segment1Quota = SegmentQuota()
            val segment2Quota = SegmentQuota()

            while (passwordBuilder.length < halfLength && wordIndex < 100) {
                val word = words[wordIndex % words.size]
                val charsNeeded = halfLength - passwordBuilder.length
                val blockLength = minOf(4, charsNeeded)

                val blockInfo = buildRhythmicBlockWithQuota(
                    word, currentSeed, blockLength,
                    globalUsedChars, segment1Quota
                ) ?: return null

                blockInfos.add(blockInfo)
                passwordBuilder.append(blockInfo.block)
                currentSeed += 1
                wordIndex++
            }

            while (passwordBuilder.length < targetLength && wordIndex < 100) {
                val word = words[wordIndex % words.size]
                val charsNeeded = targetLength - passwordBuilder.length
                val blockLength = minOf(4, charsNeeded)

                val blockInfo = buildRhythmicBlockWithQuota(
                    word, currentSeed, blockLength,
                    globalUsedChars, segment2Quota
                ) ?: return null

                blockInfos.add(blockInfo)
                passwordBuilder.append(blockInfo.block)
                currentSeed += 1
                wordIndex++
            }

            var password = passwordBuilder.toString()

            password = repairSegment(password, 0, halfLength, segment1Quota, globalUsedChars, currentSeed) ?: return null
            password = repairSegment(password, halfLength, targetLength, segment2Quota, globalUsedChars, currentSeed + 1000) ?: return null

            if (password.length != targetLength) return null

            return GenerationResult(
                password = password,
                mnemonicHint = options.phrase.take(30),
                variantName = "Вариант ${options.variantOffset + 1}",
                strength = calculateStrength(password),
                part1 = password.substring(0, halfLength),
                part2 = password.substring(halfLength),
                splitMode = SplitMode.TWO_USERS,
                explanation = buildHumanReadableExplanation(options, password, blockInfos, targetLength),
                variantOffset = options.variantOffset
            )
        } else {
            val quota = SegmentQuota()

            while (passwordBuilder.length < targetLength && wordIndex < 100) {
                val word = words[wordIndex % words.size]
                val charsNeeded = targetLength - passwordBuilder.length
                val blockLength = minOf(4, charsNeeded)

                val blockInfo = buildRhythmicBlockWithQuota(
                    word, currentSeed, blockLength,
                    globalUsedChars, quota
                ) ?: return null

                blockInfos.add(blockInfo)
                passwordBuilder.append(blockInfo.block)
                currentSeed += 1
                wordIndex++
            }

            var password = passwordBuilder.toString()
            password = repairSegment(password, 0, targetLength, quota, globalUsedChars, currentSeed) ?: return null

            if (password.length != targetLength) return null

            return GenerationResult(
                password = password,
                mnemonicHint = options.phrase.take(30),
                variantName = "Вариант ${options.variantOffset + 1}",
                strength = calculateStrength(password),
                part1 = null,
                part2 = null,
                splitMode = SplitMode.SINGLE_USER,
                explanation = buildHumanReadableExplanation(options, password, blockInfos, targetLength),
                variantOffset = options.variantOffset
            )
        }
    }

    //  Генерация блока с осмысленным якорем
    private fun buildRhythmicBlockWithQuota(
        word: String, seed: Int, targetLength: Int,
        globalUsedChars: MutableSet<Char>, quota: SegmentQuota
    ): BlockInfo? {
        val transliterated = transliterate(word)
        val base = transliterated.take(targetLength)
        if (base.isEmpty()) return null

        val anchorResult = findAnchor(transliterated, globalUsedChars) ?: return null
        
        val result = StringBuilder()
        result.append(anchorResult.char)
        
        when {
            anchorResult.char.isUpperCase() -> quota.upperCount++
            anchorResult.char.isDigit() -> quota.digitCount++
            !anchorResult.char.isLetterOrDigit() -> quota.specialCount++
        }

        for ((index, char) in base.drop(1).withIndex()) {
            val actualIndex = index + 1
            val baseChar = char 
            
            val candidates = mutableListOf<Char>()
            if (quota.digitCount < 2) candidates.addAll(digitPool)
            if (quota.specialCount < 2) candidates.addAll(specialPool)
            
            if (char.lowercaseChar() in leetMap) {
                candidates.addAll(leetMap[char.lowercaseChar()]!!)
            }
            candidates.add(baseChar)
            
            val startIndex = positiveMod(seed + actualIndex + candidates.size, candidates.size)
            var added = false
            
            for (i in 0 until candidates.size) {
                val candidate = candidates[positiveMod(startIndex + i, candidates.size)]
                if (!globalUsedChars.contains(candidate.lowercaseChar())) {
                    result.append(candidate)
                    globalUsedChars.add(candidate.lowercaseChar())
                    
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
                val fbStartIndex = positiveMod(seed + actualIndex + 100, fallbacks.size)
                for (i in 0 until fallbacks.size) {
                    val fb = fallbacks[positiveMod(fbStartIndex + i, fallbacks.size)]
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
        
        return BlockInfo(word, result.toString(), anchorResult.reason)
    }

    private fun repairSegment(
        password: String, startIdx: Int, endIdx: Int,
        initialQuota: SegmentQuota, globalUsedChars: MutableSet<Char>, seed: Int
    ): String? {
        val chars = password.toCharArray()
        val segmentLength = endIdx - startIdx
        if (segmentLength <= 0) return password

        val maxRepairAttempts = segmentLength * 10
        var attempts = 0
        var repairSeed = seed

        var quota = calculateSegmentQuota(chars, startIdx, endIdx)

        while (quota.digitCount < 2 && attempts < maxRepairAttempts) {
            val pos = positiveMod(repairSeed, segmentLength) + startIdx
            val currentChar = chars[pos]

            if (currentChar.isLetter()) {
                val oldNormalized = currentChar.lowercaseChar()
                for (digit in digitPool) {
                    if (!globalUsedChars.contains(digit)) {
                        globalUsedChars.remove(oldNormalized)
                        chars[pos] = digit
                        globalUsedChars.add(digit)
                        quota = calculateSegmentQuota(chars, startIdx, endIdx)
                        break
                    }
                }
            }
            repairSeed++
            attempts++
        }

        while (quota.specialCount < 2 && attempts < maxRepairAttempts) {
            val pos = positiveMod(repairSeed, segmentLength) + startIdx
            val currentChar = chars[pos]

            if (currentChar.isLetterOrDigit()) {
                val oldNormalized = currentChar.lowercaseChar()
                for (special in specialPool) {
                    if (!globalUsedChars.contains(special)) {
                        globalUsedChars.remove(oldNormalized)
                        chars[pos] = special
                        globalUsedChars.add(special)
                        quota = calculateSegmentQuota(chars, startIdx, endIdx)
                        break
                    }
                }
            }
            repairSeed++
            attempts++
        }

        while (quota.upperCount < 2 && attempts < maxRepairAttempts) {
            val pos = positiveMod(repairSeed, segmentLength) + startIdx
            val currentChar = chars[pos]

            if (currentChar.isLowerCase()) {
                val upper = currentChar.uppercaseChar()
                val oldNormalized = currentChar.lowercaseChar()
                val newNormalized = upper.lowercaseChar()

                if (newNormalized == oldNormalized || !globalUsedChars.contains(newNormalized)) {
                    globalUsedChars.remove(oldNormalized)
                    chars[pos] = upper
                    globalUsedChars.add(newNormalized)
                    quota = calculateSegmentQuota(chars, startIdx, endIdx)
                }
            }
            repairSeed++
            attempts++
        }

        if (quota.digitCount < 2 || quota.specialCount < 2 || quota.upperCount < 2) {
            return null
        }

        return String(chars)
    }

    //  Человеко-читаемое объяснение с якорями
    private fun buildHumanReadableExplanation(
        options: GenerationOptions,
        password: String,
        blockInfos: List<BlockInfo>,
        targetLength: Int
    ): String {
        val sb = StringBuilder()
        val scheme = determineScheme(options.variantOffset)

        sb.appendLine("Как запомнить пароль")
        sb.appendLine()
        sb.appendLine("Фраза-подсказка:")
        sb.appendLine(options.phrase)
        sb.appendLine()

        sb.appendLine("Скелет фразы:")
        for (info in blockInfos) {
            sb.appendLine("${info.word} → ${info.block.first()}")
        }
        sb.appendLine()

        sb.appendLine("Почему выбраны такие якоря:")
        for (info in blockInfos) {
            sb.appendLine("- ${info.word}: ${info.reason}")
        }
        sb.appendLine()

        sb.appendLine("Что изменилось:")
        sb.appendLine("1. В качестве первого символа блока выбрана свободная буква из слова (или её замена).")
        sb.appendLine("2. Похожие буквы заменены на цифры: o → 0, e → 3, t → 7, b → 8.")
        sb.appendLine("3. Похожие буквы заменены на символы: a/я → @, s/ш → $, i/l → !.")
        sb.appendLine("4. Сервис, логин, профиль и ротация влияют на выбор позиций замен, но не добавляются отдельным хвостом.")
        sb.appendLine()

        sb.appendLine("Почему есть неожиданные символы:")
        sb.appendLine("Пароль должен быть стойким: без повторов, с заглавными буквами, цифрами и спецсимволами. Поэтому некоторые символы выбраны не буквально из фразы, а как безопасные замены внутри блоков.")
        sb.appendLine()

        sb.appendLine("Блоки:")
        for (info in blockInfos) {
            sb.appendLine("${info.word} → ${info.block}")
        }
        sb.appendLine()

        sb.appendLine("Итоговый пароль:")
        sb.appendLine(password)

        if (options.splitMode == SplitMode.TWO_USERS) {
            sb.appendLine()
            sb.appendLine("Режим для двух пользователей:")
            sb.appendLine("Полный пароль делится на две равные части:")
            sb.appendLine()
            val half = password.length / 2
            sb.appendLine("User A: ${password.substring(0, half)}")
            sb.appendLine("User B: ${password.substring(half)}")
            sb.appendLine()
            sb.appendLine("Каждая часть отдельно содержит:")
            sb.appendLine("• минимум 2 заглавные буквы;")
            sb.appendLine("• минимум 2 цифры;")
            sb.appendLine("• минимум 2 спецсимвола.")
            sb.appendLine()
            sb.appendLine("Это один пароль, разделённый пополам, а не два разных пароля.")
        }

        return sb.toString()
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
