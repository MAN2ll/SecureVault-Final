package com.securevault.utils

object MnemonicPasswordGenerator {
    enum class SplitMode { SINGLE_USER, TWO_USERS }

    data class GenerationOptions(
        val phrase: String,
        val phrase2: String? = null,
        val serviceName: String = "",
        val username: String = "",
        val profileId: Int? = null,
        val targetLength: Int = 16,
        val rotationMonth: Int? = null,
        val rotationYear: Int? = null,
        val variantOffset: Int = 0,
        val splitMode: SplitMode = SplitMode.SINGLE_USER,
        val year: Int? = null,
        val addServiceMarker: Boolean = false,
        val addYearMarker: Boolean = false
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

    // Таблица транслитерации (включая двухбуквенные последовательности)
    private val translitMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    // Таблица замен (включая двухбуквенные последовательности)
    private val leetMap = mapOf(
        "a" to "@", "o" to "0", "t" to "7", "ch" to "4",
        "s" to "$", "i" to "1", "b" to "6", "l" to "!"
    )

    fun generateVariants(options: GenerationOptions, count: Int = 3): List<GenerationResult> {
        val results = mutableListOf<GenerationResult>()
        
        // Маркеры сервиса и года
        val serviceMarker = if (options.addServiceMarker && options.serviceName.isNotEmpty()) {
            options.serviceName.first().uppercaseChar().toString()
        } else ""
        
        val yearMarker = if (options.addYearMarker) {
            val y = (options.year ?: options.rotationYear)?.toString()?.takeLast(2) ?: ""
            if (y.length == 2 && y[0] != y[1]) y else ""
        } else ""
        
        val hasService = serviceMarker.isNotEmpty()
        val hasYear = yearMarker.isNotEmpty()
        
        // Резервы
        val reserveLen = if (options.splitMode == SplitMode.TWO_USERS) 4 else 2
        val overhead = (if (hasService) 1 else 0) + (if (hasYear) 2 else 0) + reserveLen
        val baseLength = options.targetLength - overhead

        if (baseLength < 4) return emptyList()

        // Разбиение на слова
        val words1 = options.phrase.lowercase()
            .replace(Regex("[^а-яёa-z\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            
        val words2 = if (options.splitMode == SplitMode.TWO_USERS) {
            val p2 = options.phrase2 ?: ""
            p2.lowercase()
                .replace(Regex("[^а-яёa-z\\s]"), "")
                .split(Regex("\\s+"))
                .filter { it.length >= 2 }
        } else emptyList()

        if (words1.isEmpty()) return emptyList()
        if (options.splitMode == SplitMode.TWO_USERS && words2.isEmpty()) return emptyList()

        // Генерация вариантов
        for (variantIndex in 0 until count) {
            val usedChars = mutableSetOf<Char>()
            var password = ""
            var explanation = "Фраза: ${options.phrase}\n"

            // Добавляем маркер сервиса
            if (hasService && !usedChars.contains(serviceMarker.first().lowercaseChar())) {
                password += serviceMarker
                usedChars.add(serviceMarker.first().lowercaseChar())
                explanation += "Сервис: $serviceMarker\n"
            }

            if (options.splitMode == SplitMode.SINGLE_USER) {
                val base = buildBase(words1, baseLength, usedChars, variantIndex, "#5") ?: continue
                password += base.first
                explanation += base.second
                
                // Добавляем резерв
                if (!usedChars.contains('#') && !usedChars.contains('5')) {
                    password += "#5"
                    usedChars.add('#')
                    usedChars.add('5')
                    explanation += "Резерв AMPG: #5\n"
                }
            } else {
                // TWO_USERS: две половины
                val halfLen = baseLength / 2
                val base1 = buildBase(words1, halfLen, usedChars, variantIndex, "%8") ?: continue
                val base2 = buildBase(words2, baseLength - halfLen, usedChars, variantIndex + 100, "#5") ?: continue
                
                password += base1.first
                explanation += "Часть 1: ${base1.second}\n"
                
                if (!usedChars.contains('%') && !usedChars.contains('8')) {
                    password += "%8"
                    usedChars.add('%')
                    usedChars.add('8')
                    explanation += "Резерв 1: %8\n"
                }
                
                password += base2.first
                explanation += "Часть 2: ${base2.second}\n"
                
                if (!usedChars.contains('#') && !usedChars.contains('5')) {
                    password += "#5"
                    usedChars.add('#')
                    usedChars.add('5')
                    explanation += "Резерв 2: #5\n"
                }
            }

            // Добавляем маркер года
            if (hasYear) {
                val y1 = yearMarker[0]
                val y2 = yearMarker[1]
                if (!usedChars.contains(y1) && !usedChars.contains(y2)) {
                    password += yearMarker
                    usedChars.add(y1)
                    usedChars.add(y2)
                    explanation += "Год: $yearMarker\n"
                } else {
                    continue // Не удалось добавить год без повторов
                }
            }

            // Проверка валидности
            if (password.length == options.targetLength && isValidVariant(password, options.splitMode)) {
                results.add(GenerationResult(
                    password = password,
                    mnemonicHint = options.phrase.take(30),
                    variantName = "Вариант ${variantIndex + 1}",
                    strength = PasswordGenerator.Strength.VERY_STRONG,
                    part1 = if (options.splitMode == SplitMode.TWO_USERS) password.substring(0, password.length / 2) else null,
                    part2 = if (options.splitMode == SplitMode.TWO_USERS) password.substring(password.length / 2) else null,
                    splitMode = options.splitMode,
                    explanation = explanation,
                    variantOffset = variantIndex
                ))
            }
        }
        
        return results
    }

    private fun buildBase(
        words: List<String>,
        targetLen: Int,
        usedChars: MutableSet<Char>,
        variantOffset: Int,
        reserve: String
    ): Pair<String, String>? {
        val result = StringBuilder()
        val explanation = StringBuilder()
        val charsPerWord = targetLen / words.size
        var remainder = targetLen % words.size

        for ((wIndex, word) in words.withIndex()) {
            val len = charsPerWord + (if (wIndex < remainder) 1 else 0)
            
            // Транслитерация слова
            val translit = transliterateWord(word)
            if (translit.isEmpty()) return null

            // Находим якорь (первую свободную букву)
            var anchorFound = false
            for (c in translit) {
                if (!usedChars.contains(c.lowercaseChar())) {
                    result.append(c.uppercaseChar())
                    usedChars.add(c.lowercaseChar())
                    explanation.append("${c.uppercaseChar()}(якорь)")
                    anchorFound = true
                    break
                }
            }
            if (!anchorFound) return null

            // Обрабатываем остальные символы
            var pos = 1
            val targetLength = if (wIndex == words.lastIndex) targetLen 
                              else (wIndex + 1) * charsPerWord + minOf(wIndex + 1, remainder)
            
            while (result.length < targetLength && pos < translit.length) {
                val c = translit[pos]
                val lowerC = c.lowercaseChar()
                
                // Проверяем двухбуквенную последовательность (ch, sh, sch)
                val twoChar = if (pos + 1 < translit.length) "${lowerC}${translit[pos + 1].lowercaseChar()}" else ""
                val threeChar = if (pos + 2 < translit.length) "${twoChar}${translit[pos + 2].lowercaseChar()}" else ""
                
                var chosen: Char? = null
                var skipChars = 0
                
                // Применяем замены с учётом variantOffset
                val replacement = when {
                    threeChar.length == 3 && leetMap.containsKey(threeChar) -> {
                        leetMap[threeChar]
                    }
                    twoChar.length == 2 && leetMap.containsKey(twoChar) -> {
                        leetMap[twoChar]
                    }
                    leetMap.containsKey(lowerC.toString()) -> {
                        leetMap[lowerC.toString()]
                    }
                    else -> null
                }
                
                if (replacement != null) {
                    // variantOffset определяет, какую замену использовать
                    val repIndex = (variantOffset + pos) % replacement.length
                    val repChar = replacement[repIndex]
                    if (!usedChars.contains(repChar)) {
                        chosen = repChar
                        skipChars = if (twoChar.length == 2 && leetMap.containsKey(twoChar)) 1
                                   else if (threeChar.length == 3 && leetMap.containsKey(threeChar)) 2
                                   else 0
                    }
                }
                
                // Если замена не подошла, берём оригинальный символ
                if (chosen == null) {
                    if (!usedChars.contains(lowerC)) {
                        chosen = lowerC
                    }
                }
                
                if (chosen != null) {
                    result.append(chosen)
                    usedChars.add(chosen.lowercaseChar())
                    explanation.append(chosen)
                    pos += skipChars + 1
                } else {
                    pos++
                }
            }
            explanation.append(" ")
        }
        
        return Pair(result.toString(), explanation.toString())
    }

    private fun transliterateWord(word: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < word.length) {
            val c = word[i]
            val translit = translitMap[c]
            if (translit != null) {
                result.append(translit)
            }
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
        return part.count { it.isUpperCase() } >= 2 &&
               part.count { it.isLowerCase() } >= 2 &&
               part.count { it.isDigit() } >= 2 &&
               part.count { !it.isLetterOrDigit() } >= 2
    }
}
