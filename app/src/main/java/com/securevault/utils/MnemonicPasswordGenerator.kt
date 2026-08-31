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
        
        val yearMarker = if (options.addYearMarker) {
            val y = (options.year ?: options.rotationYear)?.toString()?.takeLast(2) ?: ""
            if (y.length == 2) {
                if (y[0] == y[1]) return emptyList()
                y
            } else ""
        } else ""
        
        val hasYear = yearMarker.isNotEmpty()
        val reserveLen = if (options.splitMode == SplitMode.TWO_USERS) 4 else 2
        
        val words1 = options.phrase.lowercase().replace(Regex("[^а-яёa-z\\s]"), "").split(Regex("\\s+")).filter { it.length >= 2 }
        val words2 = if (options.splitMode == SplitMode.TWO_USERS) {
            val p2 = options.phrase2 ?: ""
            p2.lowercase().replace(Regex("[^а-яёa-z\\s]"), "").split(Regex("\\s+")).filter { it.length >= 2 }
        } else emptyList()

        if (words1.isEmpty() || (options.splitMode == SplitMode.TWO_USERS && words2.isEmpty())) return emptyList()

        val serviceCandidates = if (options.addServiceMarker && options.serviceName.isNotEmpty()) {
            options.serviceName.map { it.uppercaseChar() }.distinct()
        } else listOf(null)

        for (serviceChar in serviceCandidates) {
            val serviceMarker = serviceChar?.toString() ?: ""
            val hasService = serviceMarker.isNotEmpty()
            val overhead = (if (hasService) 1 else 0) + (if (hasYear) 2 else 0) + reserveLen
            val baseLength = options.targetLength - overhead

            if (baseLength < 4) continue

            for (variantIndex in 0 until count) {
                val usedChars = mutableSetOf<Char>()
                var password = ""
                var explanation = "Фраза: ${options.phrase}\nСтратегия: ${getStrategyName(variantIndex)}\n"

                if (options.splitMode == SplitMode.SINGLE_USER) {
                    if (hasService) {
                        markUsed(serviceMarker.first(), usedChars)
                        password += serviceMarker
                        explanation += "Сервис: $serviceMarker\n"
                    }
                    if (hasYear) {
                        markUsed(yearMarker[0], usedChars)
                        markUsed(yearMarker[1], usedChars)
                    }
                    
                    val existingForBase = "#5" + yearMarker
                    val base = buildBase(words1, baseLength, usedChars, variantIndex, existingForBase) ?: continue
                    password += base.first
                    explanation += base.second
                    
                    password += "#5"
                    markUsed('#', usedChars)
                    markUsed('5', usedChars)
                    explanation += "Резерв AMPG: #5\n"
                    
                    if (hasYear) {
                        password += yearMarker
                        explanation += "Год: $yearMarker\n"
                    }
                    
                    if (password.length == options.targetLength && isValidVariant(password, options.splitMode)) {
                        results.add(GenerationResult(password, options.phrase.take(30), "Вариант ${variantIndex + 1}",
                            PasswordGenerator.Strength.VERY_STRONG, null, null, options.splitMode, explanation, variantIndex))
                    }
                } else {
                    val part1Len = options.targetLength / 2
                    val part2Len = options.targetLength - part1Len
                    
                    var part1 = ""
                    if (hasService) {
                        markUsed(serviceMarker.first(), usedChars)
                        part1 += serviceMarker
                        explanation += "Сервис: $serviceMarker (часть 1)\n"
                    }
                    
                    val part1Existing = (if (hasService) serviceMarker else "") + "%8"
                    val base1Len = part1Len - part1Existing.length
                    val base1 = buildBase(words1, base1Len, usedChars, variantIndex, part1Existing) ?: continue
                    part1 += base1.first
                    explanation += "Часть 1 основа: ${base1.second}\n"
                    
                    part1 += "%8"
                    markUsed('%', usedChars)
                    markUsed('8', usedChars)
                    explanation += "Резерв 1: %8\n"
                    
                    var part2 = ""
                    if (hasYear) {
                        markUsed(yearMarker[0], usedChars)
                        markUsed(yearMarker[1], usedChars)
                    }
                    
                    val part2Existing = "#5" + (if (hasYear) yearMarker else "")
                    val base2Len = part2Len - part2Existing.length
                    val base2 = buildBase(words2, base2Len, usedChars, variantIndex, part2Existing) ?: continue
                    part2 += base2.first
                    explanation += "Часть 2 основа: ${base2.second}\n"
                    
                    part2 += "#5"
                    markUsed('#', usedChars)
                    markUsed('5', usedChars)
                    explanation += "Резерв 2: #5\n"
                    
                    if (hasYear) {
                        part2 += yearMarker
                        explanation += "Год: $yearMarker (часть 2)\n"
                    }
                    
                    password = part1 + part2
                    
                    if (password.length == options.targetLength && part1.length == part1Len && part2.length == part2Len && isValidVariant(password, options.splitMode)) {
                        results.add(GenerationResult(password, options.phrase.take(30), "Вариант ${variantIndex + 1}",
                            PasswordGenerator.Strength.VERY_STRONG, part1, part2, options.splitMode, explanation, variantIndex))
                    }
                }
            }
            
            if (results.isNotEmpty()) return results
        }
        
        return results
    }

    private fun getStrategyName(variantIndex: Int): String = when (variantIndex) {
        0 -> "Стратегия 1 (якорь из 1-й буквы)"
        1 -> "Стратегия 2 (якорь из 2-й буквы)"
        2 -> "Стратегия 3 (якорь из 3-й буквы)"
        else -> "Стандартный"
    }

    // ✅ ПОЛНОСТЬЮ ПЕРЕПИСАН: явный учёт квот на каждом этапе
    private fun buildBase(
        words: List<String>, 
        targetLen: Int, 
        usedChars: MutableSet<Char>, 
        variantOffset: Int,
        existingChars: String = ""
    ): Pair<String, String>? {
        val translitWords = words.map { transliterateWord(it) }.filter { it.isNotEmpty() }
        if (translitWords.isEmpty()) return null

        // === ЭТАП 1: Считаем квоты из existingChars ===
        val existingUpper = existingChars.count { it.isUpperCase() }
        val existingLower = existingChars.count { it.isLowerCase() }
        val existingDigits = existingChars.count { it.isDigit() }
        val existingSpecials = existingChars.count { !it.isLetterOrDigit() }

        // === ЭТАП 2: Находим все источники квот в словах ===
        data class SourceInfo(val char: Char, val replacement: Char?)
        val digitSources = mutableListOf<SourceInfo>()
        val specialSources = mutableListOf<SourceInfo>()
        
        for (translit in translitWords) {
            var i = 0
            while (i < translit.length) {
                val c = translit[i].lowercaseChar()
                val next = if (i + 1 < translit.length) translit[i + 1].lowercaseChar() else ' '
                val key = if (c == 'c' && next == 'h') "ch" else c.toString()
                val replacement = leetMap[key]?.firstOrNull()
                if (replacement != null) {
                    if (replacement.isDigit()) digitSources.add(SourceInfo(c, replacement))
                    if (!replacement.isLetterOrDigit()) specialSources.add(SourceInfo(c, replacement))
                }
                i += if (key == "ch") 2 else 1
            }
        }

        // === ЭТАП 3: Выбираем якоря с защитой источников квот ===
        val anchors = mutableListOf<Char>()
        var upperAnchorsNeeded = maxOf(0, 2 - existingUpper)
        
        // Уникальные символы-источники (без учёта регистра)
        val uniqueDigitSourceChars = digitSources.map { it.char }.toSet()
        val uniqueSpecialSourceChars = specialSources.map { it.char }.toSet()
        
        for (translit in translitWords) {
            var anchorFound = false
            
            val priorityPositions = when (variantOffset) {
                0 -> listOf(0, 1, 2)
                1 -> listOf(1, 0, 2)
                2 -> listOf(2, 1, 0)
                else -> listOf(0, 1, 2)
            }
            
            // Собираем все возможные позиции для якоря в этом слове
            val candidatePositions = priorityPositions + (translit.indices.filter { it !in priorityPositions })
            
            for (pos in candidatePositions) {
                if (pos >= translit.length) continue
                val c = translit[pos]
                if (isUsed(c, usedChars)) continue
                if (anchors.contains(c.uppercaseChar())) continue // Уже взяли такой якорь
                
                val lowerC = c.lowercaseChar()
                
                // Проверяем, не является ли этот символ единственным источником квоты
                val isOnlyDigitSource = (existingDigits + anchors.count { it.isDigit() } < 2) &&
                    uniqueDigitSourceChars.size == 1 && uniqueDigitSourceChars.contains(lowerC)
                val isOnlySpecialSource = (existingSpecials + anchors.count { !it.isLetterOrDigit() } < 2) &&
                    uniqueSpecialSourceChars.size == 1 && uniqueSpecialSourceChars.contains(lowerC)
                
                if (isOnlyDigitSource || isOnlySpecialSource) continue
                
                // Выбираем регистр якоря
                val anchorChar = if (upperAnchorsNeeded > 0) {
                    upperAnchorsNeeded--
                    c.uppercaseChar()
                } else {
                    c.lowercaseChar()
                }
                
                anchors.add(anchorChar)
                markUsed(c, usedChars)
                anchorFound = true
                break
            }
            
            if (!anchorFound) return null
        }

        // === ЭТАП 4: Пересчитываем квоты с учётом existingChars + anchors ===
        val anchorsUpper = anchors.count { it.isUpperCase() }
        val anchorsLower = anchors.count { it.isLowerCase() }
        val anchorsDigits = anchors.count { it.isDigit() }
        val anchorsSpecials = anchors.count { !it.isLetterOrDigit() }
        
        val totalUpper = existingUpper + anchorsUpper
        val totalLower = existingLower + anchorsLower
        val totalDigits = existingDigits + anchorsDigits
        val totalSpecials = existingSpecials + anchorsSpecials
        
        val needMoreUpper = maxOf(0, 2 - totalUpper)
        val needMoreLower = maxOf(0, 2 - totalLower)
        val needMoreDigits = maxOf(0, 2 - totalDigits)
        val needMoreSpecials = maxOf(0, 2 - totalSpecials)

        // === ЭТАП 5: Собираем доступные символы из слов ===
        val anchorLowerSet = anchors.map { it.lowercaseChar() }.toSet()
        val localUsed = usedChars.toMutableSet()
        
        val availableDigits = mutableListOf<Char>()
        val availableSpecials = mutableListOf<Char>()
        val availableLowers = mutableListOf<Char>()
        val availableUppers = mutableListOf<Char>()
        
        for (translit in translitWords) {
            var pos = 0
            while (pos < translit.length) {
                val c = translit[pos]
                val lowerC = c.lowercaseChar()
                
                // Пропускаем символы-якоря
                if (lowerC in anchorLowerSet) {
                    val nextIsH = (pos + 1 < translit.length && lowerC == 'c' && translit[pos + 1].lowercaseChar() == 'h')
                    pos += if (nextIsH) 2 else 1
                    continue
                }
                
                val nextIdx = pos + 1
                val isCh = (nextIdx < translit.length && lowerC == 'c' && translit[nextIdx].lowercaseChar() == 'h')
                val leetKey = if (isCh) "ch" else lowerC.toString()
                val replacement = leetMap[leetKey]?.firstOrNull()
                
                // Добавляем замену, если доступна
                if (replacement != null && !isUsed(replacement, localUsed)) {
                    when {
                        replacement.isDigit() -> availableDigits.add(replacement)
                        !replacement.isLetterOrDigit() -> availableSpecials.add(replacement)
                    }
                    localUsed.add(usedKey(replacement))
                }
                
                // Добавляем оригинальную букву, если доступна
                if (!isUsed(lowerC, usedChars) && !isUsed(lowerC, localUsed)) {
                    if (lowerC.isLowerCase()) availableLowers.add(lowerC)
                    localUsed.add(usedKey(lowerC))
                }
                
                pos += if (isCh) 2 else 1
            }
        }

        // === ЭТАП 6: Проверяем, достаточно ли символов ===
        val lettersToTake = targetLen - anchors.size
        if (lettersToTake < 0) return null
        
        if (availableDigits.size < needMoreDigits ||
            availableSpecials.size < needMoreSpecials ||
            availableLowers.size < needMoreLower ||
            availableUppers.size < needMoreUpper) {
            return null
        }
        
        val totalAvailable = availableDigits.size + availableSpecials.size + availableLowers.size + availableUppers.size
        if (totalAvailable < lettersToTake) return null

        // === ЭТАП 7: Берём обязательные символы для квот ===
        val selected = mutableListOf<Char>()
        val selectedUsed = localUsed.toMutableSet()
        
        for (i in 0 until needMoreUpper) {
            selected.add(availableUppers[i])
            selectedUsed.add(usedKey(availableUppers[i]))
        }
        for (i in 0 until needMoreDigits) {
            selected.add(availableDigits[i])
            selectedUsed.add(usedKey(availableDigits[i]))
        }
        for (i in 0 until needMoreSpecials) {
            selected.add(availableSpecials[i])
            selectedUsed.add(usedKey(availableSpecials[i]))
        }
        for (i in 0 until needMoreLower) {
            selected.add(availableLowers[i])
            selectedUsed.add(usedKey(availableLowers[i]))
        }

        // === ЭТАП 8: Добираем оставшимися символами ===
        val allAvailable = availableUppers + availableDigits + availableSpecials + availableLowers
        for (ch in allAvailable) {
            if (selected.size >= lettersToTake) break
            if (!isUsed(ch, selectedUsed)) {
                selected.add(ch)
                selectedUsed.add(usedKey(ch))
            }
        }

        if (selected.size < lettersToTake) return null

        // === ЭТАП 9: Формируем результат ===
        val result = StringBuilder()
        for (anchor in anchors) result.append(anchor)
        for (ch in selected) result.append(ch)

        // === ЭТАП 10: Финальная проверка квот ===
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
