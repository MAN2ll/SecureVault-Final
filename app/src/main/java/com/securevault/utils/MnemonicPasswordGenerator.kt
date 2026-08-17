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

    // Строгая таблица замен без лишних вариантов
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
                val base = buildBase(words1, baseLength, usedChars, variantIndex, false) ?: continue
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
                val base1 = buildBase(words1, base1Len, usedChars, variantIndex, false) ?: continue
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
                val base2 = buildBase(words2, base2Len, usedChars, variantIndex, true) ?: continue
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
        0 -> "Читаемый (минимальные замены)"
        1 -> "Сложный (максимальные замены)"
        2 -> "Альтернативный (другие замены)"
        else -> "Стандартный"
    }

    // ✅ Полная переработка: каждое слово даёт свой uppercase-якорь
    private fun buildBase(
        words: List<String>,
        targetLen: Int,
        usedChars: MutableSet<Char>,
        variantOffset: Int,
        isPart2: Boolean
    ): Pair<String, String>? {
        val result = StringBuilder()
        val explanation = StringBuilder()
        
        val translitWords = words.map { transliterateWord(it) }.filter { it.isNotEmpty() }
        if (translitWords.isEmpty()) return null
        
        // Находим якоря для КАЖДОГО слова
        val anchors = mutableListOf<Char>()
        for (translit in translitWords) {
            var anchorFound = false
            for (c in translit) {
                if (!isUsed(c, usedChars)) {
                    anchors.add(c.uppercaseChar())
                    markUsed(c, usedChars)
                    explanation.append("${c.uppercaseChar()} ")
                    anchorFound = true
                    break
                }
            }
            if (!anchorFound) return null
        }
        
        // Добавляем все якоря в результат (гарантия минимум N uppercase)
        for (anchor in anchors) {
            result.append(anchor)
        }
        
        val lettersToTake = targetLen - anchors.size
        if (lettersToTake < 0) return null
        
        // ✅ Приоритетные замены для достижения минимума digits/specials
        val priorityReplacements = when {
            isPart2 -> listOf("s" to "$", "l" to "!", "ch" to "4", "o" to "0") // Другой порядок для part2
            variantOffset == 0 -> listOf("o" to "0", "ch" to "4", "l" to "!", "s" to "$")
            variantOffset == 1 -> listOf("a" to "@", "o" to "0", "t" to "7", "ch" to "4", "s" to "$", "i" to "1", "b" to "6", "l" to "!")
            variantOffset == 2 -> listOf("l" to "!", "s" to "$", "t" to "7", "i" to "1")
            else -> emptyList()
        }
        
        var totalTaken = 0
        
        // Проходим по всем словам, сохраняя порядок
        for ((wordIdx, translit) in translitWords.withIndex()) {
            var pos = 0
            
            // Сначала применяем приоритетные замены
            while (totalTaken < lettersToTake && pos < translit.length) {
                val c = translit[pos]
                val lowerC = c.lowercaseChar()
                
                if (lowerC == anchors[wordIdx].lowercaseChar()) { pos++; continue }
                
                val nextIdx = pos + 1
                val isCh = (nextIdx < translit.length && lowerC == 'c' && translit[nextIdx].lowercaseChar() == 'h')
                val leetKey = if (isCh) "ch" else lowerC.toString()
                
                var chosen: Char? = null
                var skipNext = false
                
                for ((key, rep) in priorityReplacements) {
                    if (leetKey == key) {
                        val repChar = rep.first()
                        if (!isUsed(repChar, usedChars)) {
                            chosen = repChar
                            skipNext = isCh
                            break
                        }
                    }
                }
                
                if (chosen != null) {
                    result.append(chosen)
                    markUsed(chosen, usedChars)
                    explanation.append(chosen)
                    totalTaken++
                }
                
                pos += if (skipNext) 2 else 1
            }
            
            // Затем заполняем обычными буквами
            pos = 0
            while (totalTaken < lettersToTake && pos < translit.length) {
                val c = translit[pos]
                val lowerC = c.lowercaseChar()
                
                if (lowerC == anchors[wordIdx].lowercaseChar()) { pos++; continue }
                
                val nextIdx = pos + 1
                val isCh = (nextIdx < translit.length && lowerC == 'c' && translit[nextIdx].lowercaseChar() == 'h')
                
                if (!isUsed(lowerC, usedChars)) {
                    result.append(lowerC)
                    markUsed(lowerC, usedChars)
                    explanation.append(lowerC)
                    totalTaken++
                }
                
                pos += if (isCh) 2 else 1
            }
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
