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
        "a" to "@", "o" to "0", "t" to "7", "ch" to "4",
        "s" to "$", "i" to "1", "b" to "6", "l" to "!"
    )

    private val weakPhrases = setOf(
        "мама мыла раму", "ма мыла раму", "я люблю тебя",
        "мой пароль", "пароль от сайта", "qwerty", "password"
    )

    private fun usedKey(ch: Char): Char = ch.lowercaseChar()
    private fun isUsed(ch: Char, usedChars: Set<Char>): Boolean = usedKey(ch) in usedChars
    private fun markUsed(ch: Char, usedChars: MutableSet<Char>) { usedChars.add(usedKey(ch)) }

    fun generateVariants(options: GenerationOptions, count: Int = 3): List<GenerationResult> {
        val results = mutableListOf<GenerationResult>()
        
        if (options.phrase.lowercase().trim() in weakPhrases) return emptyList()
        if (options.splitMode == SplitMode.TWO_USERS) {
            val phrase2 = options.phrase2 ?: ""
            if (phrase2.lowercase().trim() in weakPhrases) return emptyList()
        }
        
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
                val base = buildBase(words1, baseLength, usedChars, variantIndex, "#5") ?: continue
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
                val part1Existing = (if (hasService) serviceMarker else "") + "%8"
                
                if (hasService && !isUsed(serviceMarker.first(), usedChars)) {
                    part1 += serviceMarker
                    markUsed(serviceMarker.first(), usedChars)
                    explanation += "Сервис: $serviceMarker (часть 1)\n"
                }
                
                val base1Len = part1Len - part1Existing.length
                val base1 = buildBase(words1, base1Len, usedChars, variantIndex, part1Existing) ?: continue
                part1 += base1.first
                explanation += "Часть 1 основа: ${base1.second}\n"
                
                if (!isUsed('%', usedChars) && !isUsed('8', usedChars)) {
                    part1 += "%8"
                    markUsed('%', usedChars)
                    markUsed('8', usedChars)
                    explanation += "Резерв 1: %8\n"
                }
                
                var part2 = ""
                val part2Existing = "#5" + (if (hasYear) yearMarker else "")
                
                val base2Len = part2Len - part2Existing.length
                val base2 = buildBase(words2, base2Len, usedChars, variantIndex, part2Existing) ?: continue
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
        0 -> "Читаемый (минимум замен)"
        1 -> "Сбалансированный (часть замен)"
        2 -> "Сложный (максимум замен)"
        else -> "Стандартный"
    }

    //  ПОЛНАЯ ПЕРЕРАБОТКА: гибкий выбор буквы ИЛИ замены
    private fun buildBase(
        words: List<String>, 
        targetLen: Int, 
        usedChars: MutableSet<Char>, 
        variantOffset: Int,
        existingChars: String = ""
    ): Pair<String, String>? {
        val translitWords = words.map { transliterateWord(it) }.filter { it.isNotEmpty() }
        if (translitWords.isEmpty()) return null

        // 1. Находим якоря для каждого слова
        val anchors = mutableListOf<Char>()
        for ((wIdx, translit) in translitWords.withIndex()) {
            var anchorFound = false
            val startIdx = if (variantOffset == 2 && translit.length > 1) 1 else 0
            
            for (i in startIdx until translit.length) {
                val c = translit[i]
                if (!isUsed(c, usedChars)) {
                    anchors.add(c.uppercaseChar())
                    markUsed(c, usedChars)
                    anchorFound = true
                    break
                }
            }
            if (!anchorFound && variantOffset == 2) {
                val c = translit[0]
                if (!isUsed(c, usedChars)) {
                    anchors.add(c.uppercaseChar())
                    markUsed(c, usedChars)
                    anchorFound = true
                }
            }
            if (!anchorFound) return null
        }

        // 2. Собираем ВСЕ возможные варианты символов (буква ИЛИ замена)
        data class CharOption(val original: Char, val replacement: Char?, val isReplacement: Boolean)
        val allOptions = mutableListOf<CharOption>()
        
        for ((wIdx, translit) in translitWords.withIndex()) {
            val anchorLower = anchors[wIdx].lowercaseChar()
            var pos = 0
            while (pos < translit.length) {
                val c = translit[pos]
                val lowerC = c.lowercaseChar()

                if (lowerC == anchorLower) {
                    val nextIsH = (pos + 1 < translit.length && lowerC == 'c' && translit[pos + 1].lowercaseChar() == 'h')
                    pos += if (nextIsH) 2 else 1
                    continue
                }

                val nextIdx = pos + 1
                val isCh = (nextIdx < translit.length && lowerC == 'c' && translit[nextIdx].lowercaseChar() == 'h')
                val leetKey = if (isCh) "ch" else lowerC.toString()
                val replacement = leetMap[leetKey]

                // Добавляем оригинальную букву
                if (!isUsed(lowerC, usedChars)) {
                    allOptions.add(CharOption(lowerC, null, false))
                }
                
                // Добавляем замену, если есть
                if (replacement != null && !isUsed(replacement.first(), usedChars)) {
                    allOptions.add(CharOption(lowerC, replacement.first(), true))
                }

                pos += if (isCh) 2 else 1
            }
        }

        val lettersToTake = targetLen - anchors.size
        if (lettersToTake < 0) return null

        // 3. Выбираем символы с приоритетом под квоты
        val selected = mutableListOf<Char>()
        val localUsed = usedChars.toMutableSet()
        
        // Считаем квоты от existingChars и якорей
        val existingUpper = existingChars.count { it.isUpperCase() }
        val existingLower = existingChars.count { it.isLowerCase() }
        val existingDigits = existingChars.count { it.isDigit() }
        val existingSpecials = existingChars.count { !it.isLetterOrDigit() }
        
        var needDigits = maxOf(0, 2 - existingDigits)
        var needSpecials = maxOf(0, 2 - existingSpecials)
        var needLower = maxOf(0, 2 - existingLower)
        
        // Сначала берём замены, которые дают недостающие квоты
        for (option in allOptions) {
            if (selected.size >= lettersToTake) break
            
            val ch = option.replacement ?: option.original
            if (isUsed(ch, localUsed)) continue
            
            val chIsDigit = ch.isDigit()
            val chIsSpecial = !ch.isLetterOrDigit()
            val chIsLower = ch.isLowerCase()
            
            val shouldTake = (chIsDigit && needDigits > 0) || 
                           (chIsSpecial && needSpecials > 0) || 
                           (chIsLower && needLower > 0)
            
            if (shouldTake) {
                selected.add(ch)
                localUsed.add(usedKey(ch))
                if (chIsDigit) needDigits--
                if (chIsSpecial) needSpecials--
                if (chIsLower) needLower--
            }
        }
        
        // Если не набрали, добираем любыми доступными символами
        if (selected.size < lettersToTake) {
            for (option in allOptions) {
                if (selected.size >= lettersToTake) break
                
                val ch = option.replacement ?: option.original
                if (!isUsed(ch, localUsed)) {
                    selected.add(ch)
                    localUsed.add(usedKey(ch))
                }
            }
        }

        if (selected.size < lettersToTake) return null

        // 4. Формируем результат
        val result = StringBuilder()
        for (anchor in anchors) result.append(anchor)
        for (ch in selected) result.append(ch)

        // 5. Финальная проверка квот
        val fullPart = result.toString() + existingChars
        val upper = fullPart.count { it.isUpperCase() }
        val lower = fullPart.count { it.isLowerCase() }
        val digits = fullPart.count { it.isDigit() }
        val specials = fullPart.count { !it.isLetterOrDigit() }

        if (upper < 2 || lower < 2 || digits < 2 || specials < 2) return null

        for (ch in selected) markUsed(ch, usedChars)

        val explanation = StringBuilder()
        for (anchor in anchors) explanation.append("$anchor ")
        for (ch in selected) explanation.append(ch)
        explanation.append("\n")

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
