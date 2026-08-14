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

    private val leetMap = mapOf("a" to "@", "o" to "0", "t" to "7", "ch" to "4", "s" to "$", "i" to "1", "b" to "6", "l" to "!")
    private val translitMap = mapOf('а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya")

    fun generateVariants(options: GenerationOptions, count: Int = 3): List<GenerationResult> {
        val results = mutableListOf<GenerationResult>()
        val serviceMarker = if (options.addServiceMarker && options.serviceName.isNotEmpty()) options.serviceName.first().uppercaseChar().toString() else ""
        val yearMarker = if (options.addYearMarker) (options.year ?: options.rotationYear)?.toString()?.takeLast(2) ?: "" else ""
        
        val hasService = serviceMarker.isNotEmpty()
        val hasYear = yearMarker.isNotEmpty() && yearMarker.first() != yearMarker.last()
        
        val reserveLen = if (options.splitMode == SplitMode.TWO_USERS) 4 else 2
        val overhead = (if (hasService) 1 else 0) + (if (hasYear) 2 else 0) + reserveLen
        val baseLength = options.targetLength - overhead

        if (baseLength < 4) return emptyList()

        val words1 = options.phrase.lowercase().replace(Regex("[^а-яёa-z\\s]"), "").split(Regex("\\s+")).filter { it.length >= 2 }
        val words2 = if (options.splitMode == SplitMode.TWO_USERS) {
            val p2 = options.phrase2 ?: options.phrase
            p2.lowercase().replace(Regex("[^а-яёa-z\\s]"), "").split(Regex("\\s+")).filter { it.length >= 2 }
        } else emptyList()

        if (words1.isEmpty() || (options.splitMode == SplitMode.TWO_USERS && words2.isEmpty())) return emptyList()

        for (variantIndex in 0 until count) {
            val usedChars = mutableSetOf<Char>()
            var password = ""
            var explanation = "Фраза: ${options.phrase}\n"

            if (hasService && !usedChars.contains(serviceMarker.first().lowercaseChar())) {
                password += serviceMarker
                usedChars.add(serviceMarker.first().lowercaseChar())
                explanation += "Сервис: $serviceMarker\n"
            }

            if (options.splitMode == SplitMode.SINGLE_USER) {
                val base = buildBase(words1, baseLength, usedChars, variantIndex, "#5") ?: continue
                password += base.first
                explanation += base.second
                if (!usedChars.contains('#') && !usedChars.contains('5')) {
                    password += "#5"; usedChars.add('#'); usedChars.add('5')
                    explanation += "Резерв AMPG: #5\n"
                }
            } else {
                val halfLen = baseLength / 2
                val base1 = buildBase(words1, halfLen, usedChars, variantIndex, "%8") ?: continue
                val base2 = buildBase(words2, baseLength - halfLen, usedChars, variantIndex + 100, "#5") ?: continue
                
                password += base1.first
                explanation += "Часть 1: ${base1.second}"
                if (!usedChars.contains('%') && !usedChars.contains('8')) {
                    password += "%8"; usedChars.add('%'); usedChars.add('8')
                    explanation += "Резерв 1: %8\n"
                }
                
                password += base2.first
                explanation += "Часть 2: ${base2.second}"
                if (!usedChars.contains('#') && !usedChars.contains('5')) {
                    password += "#5"; usedChars.add('#'); usedChars.add('5')
                    explanation += "Резерв 2: #5\n"
                }
            }

            if (hasYear) {
                val y1 = yearMarker.first(); val y2 = yearMarker.last()
                if (!usedChars.contains(y1) && !usedChars.contains(y2)) {
                    password += yearMarker; usedChars.add(y1); usedChars.add(y2)
                    explanation += "Год: $yearMarker\n"
                } else {
                    continue // Не удалось добавить год без повторов, пробуем следующий variantOffset
                }
            }

            if (password.length == options.targetLength && isValidVariant(password, options.splitMode)) {
                results.add(GenerationResult(
                    password = password, mnemonicHint = options.phrase.take(30), variantName = "Вариант ${variantIndex + 1}",
                    strength = PasswordGenerator.Strength.VERY_STRONG,
                    part1 = if (options.splitMode == SplitMode.TWO_USERS) password.substring(0, password.length / 2) else null,
                    part2 = if (options.splitMode == SplitMode.TWO_USERS) password.substring(password.length / 2) else null,
                    splitMode = options.splitMode, explanation = explanation, variantOffset = variantIndex
                ))
            }
        }
        return results
    }

    private fun buildBase(words: List<String>, targetLen: Int, usedChars: MutableSet<Char>, variantOffset: Int, reserve: String): Pair<String, String>? {
        val result = StringBuilder()
        val explanation = StringBuilder()
        val charsPerWord = targetLen / words.size
        var remainder = targetLen % words.size

        for ((wIndex, word) in words.withIndex()) {
            val len = charsPerWord + (if (wIndex < remainder) 1 else 0)
            val translit = word.map { translitMap[it] ?: it.toString() }.joinToString("")
            if (translit.isEmpty()) return null

            var anchorFound = false
            for (c in translit) {
                if (!usedChars.contains(c.lowercaseChar())) {
                    result.append(c.uppercaseChar()); usedChars.add(c.lowercaseChar())
                    explanation.append("${c.uppercaseChar()}(якорь)"); anchorFound = true; break
                }
            }
            if (!anchorFound) return null

            var pos = 1
            while (result.length < (if (wIndex == words.lastIndex) targetLen else (wIndex + 1) * charsPerWord + minOf(wIndex + 1, remainder)) && pos < translit.length) {
                val c = translit[pos]
                val lowerC = c.lowercaseChar()
                
                var chosen = lowerC
                val leetKey = if (lowerC == 'c' && pos + 1 < translit.length && translit[pos + 1] == 'h') "ch" else lowerC.toString()
                val replacement = leetMap[leetKey]
                
                if (replacement != null) {
                    // ✅ variantOffset сдвигает выбор замены, гарантируя разные варианты!
                    val repIndex = (variantOffset + pos) % replacement.length
                    val repChar = replacement[repIndex]
                    if (!usedChars.contains(repChar)) chosen = repChar
                }
                
                if (!usedChars.contains(chosen)) {
                    result.append(chosen); usedChars.add(chosen); explanation.append(chosen)
                    if (leetKey == "ch") pos++ // Пропускаем 'h'
                }
                pos++
            }
            explanation.append(" ")
        }
        return Pair(result.toString(), explanation.toString())
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
