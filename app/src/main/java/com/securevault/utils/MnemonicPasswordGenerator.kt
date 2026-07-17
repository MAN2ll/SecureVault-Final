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

    data class BlockInfo(
        val word: String,
        val block: String,
        val anchorReason: String,
        val preservedChars: List<Char>,
        val replacedChars: Map<Char, Char>
    )

    data class AnchorResult(
        val char: Char,
        val reason: String
    )

    private val leetMap = mapOf(
        'a' to listOf('@', '4'),
        'b' to listOf('6', '8'),
        'e' to listOf('3'),
        'i' to listOf('1', '!'),
        'l' to listOf('1', '!', '3'),
        'o' to listOf('0', '9'),
        's' to listOf('$', '5'),
        't' to listOf('7'),
        'y' to listOf('4')
    )

    private val digitPool = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    private val specialPool = listOf('@', '#', '$', '!', '%', '&', '*', '?')

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

    private fun findAnchor(transliteratedWord: String, globalUsedChars: MutableSet<Char>): AnchorResult? {
        for ((index, c) in transliteratedWord.withIndex()) {
            val upper = c.uppercaseChar()
            if (!globalUsedChars.contains(upper.lowercaseChar())) {
                globalUsedChars.add(upper.lowercaseChar())
                val reason = if (index == 0) "первая буква слова свободна" else "первая буква занята, выбрана буква '$c'"
                return AnchorResult(upper, reason)
            }
        }
        for ((index, c) in transliteratedWord.withIndex()) {
            val lowerC = c.lowercaseChar()
            if (lowerC in leetMap) {
                for (leet in leetMap[lowerC]!!) {
                    if (!globalUsedChars.contains(leet)) {
                        globalUsedChars.add(leet)
                        return AnchorResult(leet, "все буквы слова заняты, использована замена '$leet' для '$c'")
                    }
                }
            }
        }
        val reservePool = listOf('!', '#', '%', '&', '?', '*')
        for (sym in reservePool) {
            if (!globalUsedChars.contains(sym)) {
                globalUsedChars.add(sym)
                return AnchorResult(sym, "все буквы и замены заняты, использован резервный символ '$sym'")
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
            return checkPartComplexity(password.substring(0, half)) && checkPartComplexity(password.substring(half))
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

                val blockInfo = buildRhythmicBlockWithQuota(word, currentSeed, blockLength, globalUsedChars, segment1Quota, options.variantOffset) ?: return null
                blockInfos.add(blockInfo)
                passwordBuilder.append(blockInfo.block)
                currentSeed += 1
                wordIndex++
            }

            while (passwordBuilder.length < targetLength && wordIndex < 100) {
                val word = words[wordIndex % words.size]
                val charsNeeded = targetLength - passwordBuilder.length
                val blockLength = minOf(4, charsNeeded)

                val blockInfo = buildRhythmicBlockWithQuota(word, currentSeed, blockLength, globalUsedChars, segment2Quota, options.variantOffset) ?: return null
                blockInfos.add(blockInfo)
                passwordBuilder.append(blockInfo.block)
                currentSeed += 1
                wordIndex++
            }

            var password = passwordBuilder.toString()
            password = repairSegment(password, 0, halfLength, segment1Quota, globalUsedChars, currentSeed, options.variantOffset) ?: return null
            password = repairSegment(password, halfLength, targetLength, segment2Quota, globalUsedChars, currentSeed + 1000, options.variantOffset) ?: return null

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

                val blockInfo = buildRhythmicBlockWithQuota(word, currentSeed, blockLength, globalUsedChars, quota, options.variantOffset) ?: return null
                blockInfos.add(blockInfo)
                passwordBuilder.append(blockInfo.block)
                currentSeed += 1
                wordIndex++
            }

            var password = passwordBuilder.toString()
            password = repairSegment(password, 0, targetLength, quota, globalUsedChars, currentSeed, options.variantOffset) ?: return null

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

    // Добавлен variantOffset для детерминированного разнообразия при выборе замен
    private fun buildRhythmicBlockWithQuota(
        word: String, seed: Int, targetLength: Int,
        globalUsedChars: MutableSet<Char>, quota: SegmentQuota,
        variantOffset: Int
    ): BlockInfo? {
        val transliterated = transliterate(word)
        if (transliterated.isEmpty()) return null

        val anchorResult = findAnchor(transliterated, globalUsedChars) ?: return null
        val result = StringBuilder()
        result.append(anchorResult.char)
        
        when {
            anchorResult.char.isUpperCase() -> quota.upperCount++
            anchorResult.char.isDigit() -> quota.digitCount++
            !anchorResult.char.isLetterOrDigit() -> quota.specialCount++
        }

        val anchorIndex = transliterated.indexOfFirst { 
            it.uppercaseChar() == anchorResult.char.uppercaseChar() || 
            (it.lowercaseChar() in leetMap && leetMap[it.lowercaseChar()]!!.contains(anchorResult.char))
        }

        var charsAdded = 1
        var currentIndex = 0
        val preserved = mutableListOf<Char>()
        val replaced = mutableMapOf<Char, Char>()
        
        while (charsAdded < targetLength) {
            val c = transliterated[currentIndex % transliterated.length]
            val lowerC = c.lowercaseChar()
            val isRepeat = globalUsedChars.contains(lowerC)
            
            var chosenChar: Char? = null
            
            if (!isRepeat) {
                chosenChar = lowerC
                globalUsedChars.add(lowerC)
                preserved.add(lowerC)
            } else {
                val leetOptions = leetMap[lowerC] ?: emptyList()
                // ✅ Детерминированный сдвиг начала поиска в зависимости от variantOffset
                val leetStart = if (leetOptions.isNotEmpty()) positiveMod(variantOffset, leetOptions.size) else 0
                
                for (i in 0 until leetOptions.size) {
                    val leet = leetOptions[positiveMod(leetStart + i, leetOptions.size)]
                    if (!globalUsedChars.contains(leet)) {
                        chosenChar = leet
                        globalUsedChars.add(leet)
                        break
                    }
                }
                
                if (chosenChar == null) {
                    val reserveStart = positiveMod(variantOffset, specialPool.size + digitPool.size)
                    val fallbackPool = digitPool + specialPool
                    for (i in 0 until fallbackPool.size) {
                        val res = fallbackPool[positiveMod(reserveStart + i, fallbackPool.size)]
                        if (!globalUsedChars.contains(res)) {
                            chosenChar = res
                            globalUsedChars.add(res)
                            break
                        }
                    }
                }
                
                if (chosenChar != null) {
                    replaced[lowerC] = chosenChar
                }
            }
            
            if (chosenChar != null) {
                result.append(chosenChar)
                when {
                    chosenChar.isUpperCase() -> quota.upperCount++
                    chosenChar.isDigit() -> quota.digitCount++
                    !chosenChar.isLetterOrDigit() -> quota.specialCount++
                }
                charsAdded++
            } else {
                break 
            }
            currentIndex++
        }
        
        if (charsAdded < targetLength) return null
        
        return BlockInfo(word, result.toString(), anchorResult.reason, preserved, replaced)
    }

    // Добавлен variantOffset для детерминированного разнообразия при ремонте сегмента
    private fun repairSegment(
        password: String, startIdx: Int, endIdx: Int,
        initialQuota: SegmentQuota, globalUsedChars: MutableSet<Char>, 
        seed: Int, variantOffset: Int
    ): String? {
        val chars = password.toCharArray()
        val segmentLength = endIdx - startIdx
        if (segmentLength <= 0) return password

        val maxRepairAttempts = segmentLength * 10
        var attempts = 0
        var repairSeed = seed
        var quota = calculateSegmentQuota(chars, startIdx, endIdx)

        while (quota.digitCount < 2 && attempts < maxRepairAttempts) {
            // ✅ Сдвиг позиции поиска
            val pos = positiveMod(repairSeed + variantOffset, segmentLength) + startIdx
            val currentChar = chars[pos]
            if (currentChar.isLetter()) {
                val oldNormalized = currentChar.lowercaseChar()
                val digitStart = positiveMod(variantOffset, digitPool.size)
                for (i in 0 until digitPool.size) {
                    val digit = digitPool[positiveMod(digitStart + i, digitPool.size)]
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
            val pos = positiveMod(repairSeed + variantOffset, segmentLength) + startIdx
            val currentChar = chars[pos]
            if (currentChar.isLetterOrDigit()) {
                val oldNormalized = currentChar.lowercaseChar()
                val specialStart = positiveMod(variantOffset, specialPool.size)
                for (i in 0 until specialPool.size) {
                    val special = specialPool[positiveMod(specialStart + i, specialPool.size)]
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
            val pos = positiveMod(repairSeed + variantOffset, segmentLength) + startIdx
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

        if (quota.digitCount < 2 || quota.specialCount < 2 || quota.upperCount < 2) return null
        return String(chars)
    }

    private fun buildHumanReadableExplanation(
        options: GenerationOptions,
        password: String,
        blockInfos: List<BlockInfo>,
        targetLength: Int
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Как запомнить пароль")
        sb.appendLine()
        sb.appendLine("Фраза:")
        sb.appendLine(options.phrase)
        sb.appendLine()
        
        sb.appendLine("Скелет:")
        sb.appendLine(blockInfos.joinToString(" / ") { it.word })
        sb.appendLine()
        
        val allPreserved = blockInfos.flatMap { it.preservedChars }.distinct().sorted()
        if (allPreserved.isNotEmpty()) {
            sb.appendLine("Сохранены уникальные буквы:")
            sb.appendLine(allPreserved.joinToString(", "))
            sb.appendLine()
        }
        
        val allReplaced = blockInfos.flatMap { it.replacedChars.entries }.distinctBy { it.key }
        if (allReplaced.isNotEmpty()) {
            sb.appendLine("Заменены повторы:")
            allReplaced.forEach { (orig, repl) -> sb.appendLine("$orig -> $repl") }
            sb.appendLine()
        }
        
        sb.appendLine("Якоря слов:")
        blockInfos.forEach { info -> sb.appendLine("${info.word} -> ${info.block.first()}") }
        sb.appendLine()
        
        sb.appendLine("Пояснение:")
        val customAnchors = blockInfos.filter { it.anchorReason.contains("первая буква занята") || it.anchorReason.contains("все буквы слова заняты") }
        if (customAnchors.isNotEmpty()) {
            customAnchors.forEach { info ->
                sb.appendLine("• '${info.word}' начинается с '${info.block.first()}', потому что ${info.anchorReason.lowercase()}.")
            }
        } else {
            sb.appendLine("• Все слова смогли использовать свои первые буквы в качестве якорей.")
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
