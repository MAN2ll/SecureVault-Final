package com.securevault.utils

object MnemonicPasswordGenerator {
    enum class SplitMode { SINGLE_USER, TWO_USERS }

    data class GenerationOptions(
        val phrase: String, val phrase2: String? = null, val serviceName: String = "",
        val username: String = "", val profileId: Int? = null, val targetLength: Int = 16,
        val rotationMonth: Int? = null, val rotationYear: Int? = null, val variantOffset: Int = 0,
        val splitMode: SplitMode = SplitMode.SINGLE_USER, val year: Int? = null,
        val addServiceMarker: Boolean = false, val addYearMarker: Boolean = false
    )

    data class GenerationResult(
        val password: String, val mnemonicHint: String, val variantName: String,
        val strength: PasswordGenerator.Strength, val part1: String?, val part2: String?,
        val splitMode: SplitMode, val explanation: String, val variantOffset: Int = 0
    )

    private val translitMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "",
        'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    private val leetMap = mapOf(
        "a" to listOf("@", "4"), "o" to listOf("0", "9"), "t" to listOf("7"),
        "ch" to listOf("4"), "s" to listOf("$", "5"), "i" to listOf("1", "!"),
        "l" to listOf("!", "1"), "b" to listOf("6", "8")
    )

    private val weakPhrases = setOf(
        "мама мыла раму", "ма мыла раму", "я люблю тебя",
        "мой пароль", "пароль от сайта", "qwerty", "password"
    )

    // ✅ БЕЗОПАСНЫЕ HELPER-ФУНКЦИИ ДЛЯ РАБОТЫ С Char и Set<Char>
    private fun usedKey(ch: Char): Char = ch.lowercaseChar()
    private fun isUsed(ch: Char, usedChars: Set<Char>): Boolean = usedKey(ch) in usedChars
    private fun markUsed(ch: Char, usedChars: MutableSet<Char>) { usedChars.add(usedKey(ch)) }
    private fun hasUsedChars(text: String, usedChars: Set<Char>): Boolean = text.any { it.lowercaseChar() in usedChars }
    private fun markUsedText(text: String, usedChars: MutableSet<Char>) { text.forEach { usedChars.add(it.lowercaseChar()) } }

    fun generateVariants(options: GenerationOptions, count: Int = 3): List<GenerationResult> {
        val results = mutableListOf<GenerationResult>()
        if (options.phrase.lowercase().trim() in weakPhrases) return emptyList()
        
        val serviceMarker = if (options.addServiceMarker && options.serviceName.isNotEmpty()) {
            options.serviceName.first().uppercaseChar().toString()
        } else ""
        
        val yearMarker = if (options.addYearMarker) {
            val y = (options.year ?: options.rotationYear)?.toString()?.takeLast(2) ?: ""
            if (y.length == 2 && y[0] != y[1]) y else ""
        } else ""
        
        val hasService = serviceMarker.isNotEmpty()
        val hasYear = yearMarker.isNotEmpty()
        val reserveLen = if (options.splitMode == SplitMode.TWO_USERS) 4 else 2
        val overhead = (if (hasService) 1 else 0) + (if (hasYear) 2 else 0) + reserveLen
        val baseLength = options.targetLength - overhead

        if (baseLength < 4) return emptyList()

        val words1 = options.phrase.lowercase().replace(Regex("[^а-яёa-z\\s]"), "").split(Regex("\\s+")).filter { it.length >= 2 }
        val words2 = if (options.splitMode == SplitMode.TWO_USERS) {
            val p2 = options.phrase2 ?: ""
            p2.lowercase().replace(Regex("[^а-яёa-z\\s]"), "").split(Regex("\\s+")).filter { it.length >= 2 }
        } else emptyList()

        if (words1.isEmpty() || (options.splitMode == SplitMode.TWO_USERS && words2.isEmpty())) return emptyList()

        for (variantIndex in 0 until count) {
            val usedChars = mutableSetOf<Char>()
            var password = ""
            var explanation = "Фраза: ${options.phrase}\nСтратегия: ${getStrategyName(variantIndex)}\n"

            if (options.splitMode == SplitMode.SINGLE_USER) {
                if (hasService && !isUsed(serviceMarker.first(), usedChars)) {
                    password += serviceMarker
                    markUsed(serviceMarker.first(), usedChars)
                    explanation += "Сервис: $serviceMarker\n"
                }
                val base = buildBase(words1, baseLength, usedChars, variantIndex) ?: continue
                password += base.first
                explanation += base.second
                if (!isUsed('#', usedChars) && !isUsed('5', usedChars)) {
                    password += "#5"
                    markUsed('#', usedChars)
                    markUsed('5', usedChars)
                    explanation += "Резерв AMPG: #5\n"
                }
                if (hasYear) {
                    val y1 = yearMarker[0]
                    val y2 = yearMarker[1]
                    if (!isUsed(y1, usedChars) && !isUsed(y2, usedChars)) {
                        password += yearMarker
                        markUsed(y1, usedChars)
                        markUsed(y2, usedChars)
                        explanation += "Год: $yearMarker\n"
                    } else {
                        continue
                    }
                }
                if (password.length == options.targetLength && isValidVariant(password, options.splitMode)) {
                    results.add(GenerationResult(password, options.phrase.take(30), "Вариант ${variantIndex + 1}",
                        PasswordGenerator.Strength.VERY_STRONG, null, null, options.splitMode, explanation, variantIndex))
                }
            } else {
                val part1Len = options.targetLength / 2
                val part2Len = options.targetLength - part1Len
                var part1 = ""
                if (hasService && !isUsed(serviceMarker.first(), usedChars)) {
                    part1 += serviceMarker
                    markUsed(serviceMarker.first(), usedChars)
                    explanation += "Сервис: $serviceMarker (часть 1)\n"
                }
                val base1Len = part1Len - (if (hasService) 1 else 0) - 2
                val base1 = buildBase(words1, base1Len, usedChars, variantIndex) ?: continue
                part1 += base1.first
                explanation += "Часть 1 основа: ${base1.second}\n"
                if (!isUsed('%', usedChars) && !isUsed('8', usedChars)) {
                    part1 += "%8"
                    markUsed('%', usedChars)
                    markUsed('8', usedChars)
                    explanation += "Резерв 1: %8\n"
                }
                var part2 = ""
                val base2Len = part2Len - 2 - (if (hasYear) 2 else 0)
                val base2 = buildBase(words2, base2Len, usedChars, variantIndex + 100) ?: continue
                part2 += base2.first
                explanation += "Часть 2 основа: ${base2.second}\n"
                if (!isUsed('#', usedChars) && !isUsed('5', usedChars)) {
                    part2 += "#5"
                    markUsed('#', usedChars)
                    markUsed('5', usedChars)
                    explanation += "Резерв 2: #5\n"
                }
                if (hasYear) {
                    val y1 = yearMarker[0]
                    val y2 = yearMarker[1]
                    if (!isUsed(y1, usedChars) && !isUsed(y2, usedChars)) {
                        part2 += yearMarker
                        markUsed(y1, usedChars)
                        markUsed(y2, usedChars)
                        explanation += "Год: $yearMarker (часть 2)\n"
                    } else {
                        continue
                    }
                }
                password = part1 + part2
                if (password.length == options.targetLength && part1.length == part1Len && part2.length == part2Len && isValidVariant(password, options.splitMode)) {
                    results.add(GenerationResult(password, options.phrase.take(30), "Вариант ${variantIndex + 1}",
                        PasswordGenerator.Strength.VERY_STRONG, part1, part2, options.splitMode, explanation, variantIndex))
                }
            }
        }
        return results
    }

    private fun getStrategyName(variantIndex: Int): String = when (variantIndex) {
        0 -> "Минимальные замены (максимально читаемый)"
        1 -> "Средние замены (баланс)"
        2 -> "Максимальные замены (больше спецсимволов)"
        else -> "Стандартный"
    }

    private fun buildBase(words: List<String>, targetLen: Int, usedChars: MutableSet<Char>, variantOffset: Int): Pair<String, String>? {
        val result = StringBuilder()
        val explanation = StringBuilder()
        val translit = words.joinToString("") { transliterateWord(it) }
        if (translit.isEmpty()) return null

        val firstWordTranslit = transliterateWord(words[0])
        var anchorFound = false
        for (c in firstWordTranslit) {
            if (!isUsed(c, usedChars)) {
                result.append(c.uppercaseChar())
                markUsed(c, usedChars)
                explanation.append("${c.uppercaseChar()}(якорь)")
                anchorFound = true
                break
            }
        }
        if (!anchorFound) return null

        var pos = 0
        val maxIterations = translit.length * 4
        while (result.length < targetLen && pos < maxIterations) {
            val idx = pos % translit.length
            val c = translit[idx]
            val lowerC = c.lowercaseChar()
            
            val nextIdx = (pos + 1) % translit.length
            val twoChar = if (lowerC == 'c' && translit[nextIdx].lowercaseChar() == 'h') "ch" else ""
            val leetKey = if (twoChar == "ch") "ch" else lowerC.toString()
            val replacements = leetMap[leetKey]
            
            var chosen: Char? = null
            var skipNext = false

            when (variantOffset) {
                0 -> {
                    if (!isUsed(lowerC, usedChars)) chosen = lowerC
                    else chosen = replacements?.firstOrNull { !isUsed(it.first(), usedChars) }?.first()
                }
                1 -> {
                    val isVowel = lowerC in "aeiou"
                    if (isVowel && replacements != null) chosen = replacements.firstOrNull { !isUsed(it.first(), usedChars) }?.first()
                    if (chosen == null && !isUsed(lowerC, usedChars)) chosen = lowerC
                    else if (chosen == null) chosen = replacements?.firstOrNull { !isUsed(it.first(), usedChars) }?.first()
                }
                2 -> {
                    if (replacements != null) {
                        val prefs = if (replacements.size > 1) listOf(replacements[1], replacements[0]) else replacements
                        chosen = prefs.firstOrNull { !isUsed(it.first(), usedChars) }?.first()
                    }
                    if (chosen == null && !isUsed(lowerC, usedChars)) chosen = lowerC
                }
            }

            if (chosen != null) {
                result.append(chosen)
                markUsed(chosen, usedChars)
                explanation.append(chosen)
                if (twoChar == "ch") skipNext = true
            }
            pos++
            if (skipNext) pos++
        }
        if (result.length < targetLen) return null
        return Pair(result.toString(), explanation.toString())
    }

    private fun transliterateWord(word: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < word.length) {
            val c = word[i]
            val translit = translitMap[c]
            if (translit != null) result.append(translit)
            i++
        }
        return result.toString()
    }

    private fun isValidVariant(password: String, splitMode: SplitMode): Boolean {
        val lower = password.lowercase()
        if (lower.length != lower.toSet().size) return false
        if (splitMode == SplitMode.TWO_USERS) {
            val half = password.length / 2
            return checkPart(password.substring(0, half)) && checkPart(password.substring(half))
        }
        return checkPart(password)
    }

    private fun checkPart(part: String): Boolean {
        return part.count { it.isUpperCase() } >= 2 && part.count { it.isLowerCase() } >= 2 &&
               part.count { it.isDigit() } >= 2 && part.count { !it.isLetterOrDigit() } >= 2
    }
}
