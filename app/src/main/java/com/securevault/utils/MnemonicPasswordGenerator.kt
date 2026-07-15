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
        val includeLeet: Boolean = true,
        val rotationMonth: Int? = null,
        val rotationYear: Int? = null,
        val variantOffset: Int = 0,
        val splitMode: SplitMode = SplitMode.SINGLE_USER,
        val enforceUniqueChars: Boolean = true
    )

    data class GenerationResult(
        val password: String,
        val mnemonicHint: String,
        val variantName: String,
        val strength: PasswordGenerator.Strength,
        val part1: String?,
        val part2: String?,
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
        val maxAttempts = 150

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

        val seedStr = "${options.phrase}${options.serviceName}${options.username}${options.profileId}${options.rotationMonth}${options.rotationYear}${options.variantOffset}"
        val seedHash = seedStr.hashCode()

        val explanation = StringBuilder()
        explanation.append("Фраза: ${options.phrase}\n")
        explanation.append("Транслитерация: ${transliterate(options.phrase)}\n")
        explanation.append("Внутренние факторы: сервис, логин, профиль и ротация влияют на позиции замен внутри блоков. Отдельные технические хвосты не используются.\n")

        if (options.splitMode == SplitMode.TWO_USERS && words.size >= 2) {
            val halfLength = targetLength / 2
            val part1 = buildRhythmicBlock(words[0], seedHash, halfLength, options.includeLeet, options.enforceUniqueChars, explanation)
            val part2 = buildRhythmicBlock(words[1 % words.size], seedHash + 1, halfLength, options.includeLeet, options.enforceUniqueChars, explanation)
            
            val password = part1 + part2

            return GenerationResult(
                password = password,
                mnemonicHint = options.phrase.take(30),
                variantName = "Вариант ${options.variantOffset + 1}",
                strength = calculateStrength(password),
                part1 = part1,
                part2 = part2,
                explanation = explanation.toString()
            )
        } else {
            val blocks = words.take(4).mapIndexed { index, word ->
                buildRhythmicBlock(word, seedHash + index, 4, options.includeLeet, options.enforceUniqueChars, explanation)
            }
            
            var password = blocks.joinToString("")
            var currentSeed = seedHash + blocks.size
            while (password.length < targetLength) {
                val extraBlock = buildRhythmicBlock(words[0], currentSeed, 4, options.includeLeet, options.enforceUniqueChars, explanation)
                password += extraBlock
                currentSeed++
            }
            password = password.take(targetLength)

            return GenerationResult(
                password = password,
                mnemonicHint = options.phrase.take(30),
                variantName = "Вариант ${options.variantOffset + 1}",
                strength = calculateStrength(password),
                part1 = null,
                part2 = null,
                explanation = explanation.toString()
            )
        }
    }

    private fun buildRhythmicBlock(word: String, seed: Int, maxLength: Int, useLeet: Boolean, enforceUnique: Boolean, explanation: StringBuilder): String {
        val transliterated = transliterate(word)
        val base = transliterated.take(maxLength)
        
        val result = StringBuilder()
        val usedChars = mutableSetOf<Char>()
        
        explanation.append("Блок '$word' -> ")
        
        for ((index, char) in base.withIndex()) {
            if (useLeet && char in leetMap) {
                val replacements = leetMap[char]!!
                var replacementAdded = false
                for (attempt in 0 until replacements.size) {
                    val replacementIndex = Math.abs((seed + index + attempt) % replacements.size)
                    val replacement = replacements[replacementIndex]
                    
                    if (!enforceUnique || !usedChars.contains(replacement.lowercaseChar())) {
                        result.append(replacement)
                        usedChars.add(replacement.lowercaseChar())
                        replacementAdded = true
                        break
                    }
                }
                
                if (!replacementAdded) {
                    val finalChar = if (index == 0) char.uppercaseChar() else char
                    result.append(finalChar)
                    usedChars.add(finalChar.lowercaseChar())
                }
            } else {
                val finalChar = if (index == 0) char.uppercaseChar() else char
                if (!enforceUnique || !usedChars.contains(finalChar.lowercaseChar())) {
                    result.append(finalChar)
                    usedChars.add(finalChar.lowercaseChar())
                } else {
                    val fallbackChars = listOf('1', '2', '3', '@', '$', '!')
                    val fallbackChar = fallbackChars.firstOrNull { !usedChars.contains(it) }
                    if (fallbackChar != null) {
                        result.append(fallbackChar)
                        usedChars.add(fallbackChar)
                    } else {
                        result.append(finalChar)
                    }
                }
            }
        }
        explanation.append(result.toString())
        if (base.length < 4) explanation.append(" (дополнено)")
        explanation.append("\n")
        
        return result.toString()
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
