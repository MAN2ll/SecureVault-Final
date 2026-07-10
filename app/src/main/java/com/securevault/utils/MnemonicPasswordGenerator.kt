package com.securevault.utils

import kotlin.random.Random

object MnemonicPasswordGenerator {

    // Настройки генерации
    data class GenerationOptions(
        val phrase: String,
        val serviceName: String,
        val targetLength: Int = 16,
        val includeLeet: Boolean = true,
        val includeServiceCode: Boolean = true,
        val includeRotationCode: Boolean = true,
        val rotationMonth: Int? = null,
        val rotationYear: Int? = null,
        val variantOffset: Int = 0,
        val separator: String = "", //  разделитель (по умолчанию пустой)
        val enforceUniqueChars: Boolean = true
    )

    data class GenerationResult(
        val password: String,
        val mnemonicHint: String,
        val variantName: String,
        val strength: PasswordGenerator.Strength,
        val part1: String,
        val part2: String,
        val hasUniqueChars: Boolean
    )

    //  Пулы символов
    private val uppercaseLetters = ('A'..'Z').toList()
    private val lowercaseLetters = ('a'..'z').toList()
    private val digits = ('0'..'9').toList()
    private val specialChars = listOf('@', '$', '!', '#', '%', '&', '*', '_', '-')

    //  Leet-замены
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

    //  Генерация всех вариантов с разными offset
    fun generateVariants(options: GenerationOptions, count: Int = 5): List<GenerationResult> {
        val results = mutableListOf<GenerationResult>()

        for (i in 0 until count) {
            val variantOptions = options.copy(variantOffset = options.variantOffset + i)
            try {
                val result = generateSingleVariant(variantOptions)
                if (result != null) {
                    results.add(result)
                }
            } catch (e: Exception) {
                // Пропускаем неудачные варианты
            }
        }

        return results
    }

    //  Генерация одного варианта
    private fun generateSingleVariant(options: GenerationOptions): GenerationResult? {
        val words = extractWords(options.phrase)
        if (words.size < 2) return null

        //  Берём 2 слова из фразы
        val word1 = words[options.variantOffset % words.size]
        val word2 = words[(options.variantOffset + 1) % words.size]

        //  Генерируем две части отдельно
        val usedChars = mutableSetOf<Char>()
        val part1 = generatePart(word1, usedChars, options, isFirstPart = true) ?: return null
        val part2 = generatePart(word2, usedChars, options, isFirstPart = false) ?: return null

        //  Соединяем без разделителя (или с указанным)
        val password = part1 + options.separator + part2

        //  Проверка уникальности
        val hasUniqueChars = !PasswordValidator.hasDuplicateCharacters(password)

        if (options.enforceUniqueChars && !hasUniqueChars) {
            return null // Пропускаем вариант с повторами
        }

        // Подсказка
        val hint = "${words.joinToString(" ").take(30)}..."

        // Название варианта
        val variantName = "Вариант ${options.variantOffset + 1}"

        // Сложность
        val strength = calculateStrength(password)

        return GenerationResult(
            password = password,
            mnemonicHint = hint,
            variantName = variantName,
            strength = strength,
            part1 = part1,
            part2 = part2,
            hasUniqueChars = hasUniqueChars
        )
    }

    //  Извлечение слов из фразы
    private fun extractWords(phrase: String): List<String> {
        return phrase
            .lowercase()
            .replace(Regex("[^а-яёa-z\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .distinct()
    }

    //  Генерация одной части пароля
    private fun generatePart(
        word: String,
        usedChars: MutableSet<Char>,
        options: GenerationOptions,
        isFirstPart: Boolean
    ): String? {
        val transliterated = transliterate(word)
        val chars = mutableListOf<Char>()

        //  Первая буква заглавная
        val firstChar = transliterated.first().uppercaseChar()
        if (firstChar !in usedChars) {
            chars.add(firstChar)
            usedChars.add(firstChar)
        } else {
            // Заменяем на свободную заглавную
            val freeUppercase = uppercaseLetters.firstOrNull { it !in usedChars } ?: return null
            chars.add(freeUppercase)
            usedChars.add(freeUppercase)
        }

        //  Обрабатываем остальные буквы слова
        for (i in 1 until transliterated.length) {
            val char = transliterated[i]
            if (char in usedChars) {
                // Leet-замена для повторов
                if (options.includeLeet && char in leetMap) {
                    val replacements = leetMap[char]!!
                    val replacement = replacements.firstOrNull { it !in usedChars }
                    if (replacement != null) {
                        chars.add(replacement)
                        usedChars.add(replacement)
                        continue
                    }
                }
                // Пропускаем символ, если не можем заменить
                continue
            }

            // Leet-замена с вероятностью 50%
            if (options.includeLeet && char in leetMap && Random.nextBoolean()) {
                val replacements = leetMap[char]!!
                val replacement = replacements.firstOrNull { it !in usedChars }
                if (replacement != null) {
                    chars.add(replacement)
                    usedChars.add(replacement)
                    continue
                }
            }

            chars.add(char)
            usedChars.add(char)
        }

        //  Добавляем цифры и спецсимволы
        // Минимум 2 цифры и 2 спецсимвола на часть
        val targetDigits = 2
        val targetSpecials = 2

        var addedDigits = 0
        var addedSpecials = 0

        // Добавляем цифры
        for (digit in digits.shuffled()) {
            if (addedDigits >= targetDigits) break
            if (digit !in usedChars) {
                // Вставляем в случайную позицию
                val insertPos = Random.nextInt(1, chars.size)
                chars.add(insertPos, digit)
                usedChars.add(digit)
                addedDigits++
            }
        }

        // Добавляем спецсимволы
        for (special in specialChars.shuffled()) {
            if (addedSpecials >= targetSpecials) break
            if (special !in usedChars) {
                val insertPos = Random.nextInt(1, chars.size)
                chars.add(insertPos, special)
                usedChars.add(special)
                addedSpecials++
            }
        }

        // Если не удалось добавить достаточно символов — возвращаем null
        if (addedDigits < targetDigits || addedSpecials < targetSpecials) {
            return null
        }

        return chars.joinToString("")
    }

    //  Транслитерация
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

        return text.map { char ->
            if (char in map) map[char]!! else char.toString()
        }.joinToString("")
    }

    //  Расчёт сложности
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
