package com.securevault.utils

object MnemonicPasswordGenerator {

    enum class SplitMode {
        SINGLE_USER,
        TWO_USERS
    }

    data class GenerationOptions(
        val phrase: String,
        val serviceName: String = "",
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
        val hasUniqueChars: Boolean,
        val splitMode: SplitMode
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

    fun generateVariants(options: GenerationOptions, count: Int = 5): List<GenerationResult> {
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

        for (i in 0 until count) {
            val variantOptions = options.copy(variantOffset = options.variantOffset + i)
            val result = generateSingleVariant(variantOptions, effectiveLength)
            if (result != null) results.add(result)
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

        val seedStr = "${options.phrase}${options.serviceName}${options.rotationMonth}${options.rotationYear}${options.variantOffset}"
        val seedHash = seedStr.hashCode()

        if (options.splitMode == SplitMode.TWO_USERS && words.size >= 2) {
            val halfLength = targetLength / 2
            val part1 = buildRhythmicBlock(words[0], seedHash, halfLength, options.includeLeet)
            val part2 = buildRhythmicBlock(words[1 % words.size], seedHash + 1, halfLength, options.includeLeet)
            
            val password = part1 + part2
            val hasUnique = !options.enforceUniqueChars || !PasswordValidator.hasDuplicateCharacters(password)
            
            if (!hasUnique && options.enforceUniqueChars) return null

            return GenerationResult(
                password = password,
                mnemonicHint = options.phrase.take(30),
                variantName = "Вариант ${options.variantOffset + 1}",
                strength = calculateStrength(password),
                part1 = part1,
                part2 = part2,
                hasUniqueChars = hasUnique,
                splitMode = SplitMode.TWO_USERS
            )
        } else {
            val blocks = words.take(4).mapIndexed { index, word ->
                buildRhythmicBlock(word, seedHash + index, 4, options.includeLeet)
            }
            
            var password = blocks.joinToString("")
            var currentSeed = seedHash + blocks.size
            while (password.length < targetLength) {
                val extraBlock = buildRhythmicBlock(words[0], currentSeed, 4, options.includeLeet)
                password += extraBlock
                currentSeed++
            }
            password = password.take(targetLength)

            val hasUnique = !options.enforceUniqueChars || !PasswordValidator.hasDuplicateCharacters(password)
            if (!hasUnique && options.enforceUniqueChars) return null

            return GenerationResult(
                password = password,
                mnemonicHint = options.phrase.take(30),
                variantName = "Вариант ${options.variantOffset + 1}",
                strength = calculateStrength(password),
                part1 = null,
                part2 = null,
                hasUniqueChars = hasUnique,
                splitMode = SplitMode.SINGLE_USER
            )
        }
    }

    //Убран лишний .append(char)
    private fun buildRhythmicBlock(word: String, seed: Int, maxLength: Int, useLeet: Boolean): String {
        val transliterated = transliterate(word)
        val base = transliterated.take(maxLength)
        
        val result = StringBuilder()
        for ((index, char) in base.withIndex()) {
            if (useLeet && char in leetMap) {
                val replacements = leetMap[char]!!
                val replacementIndex = Math.abs((seed + index) % replacements.size)
                result.append(replacements[replacementIndex])
            } else {
                if (index == 0) result.append(char.uppercaseChar())
                else result.append(char)
            }
        }
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
