package com.securevault.utils

/**
 * AMPG v1 — Adaptive Mnemonic Password Generation
 * Адаптивная мнемоническая генерация паролей
 * 
 * Детерминированный алгоритм: одинаковые входные данные = одинаковый пароль.
 * Без скрытой случайности. Пароль должен быть запоминаемым по подсказке.
 */
object MnemonicPasswordGenerator {

    data class GenerationResult(
        val password: String,
        val mnemonicHint: String,
        val rotationSuffix: String,
        val steps: List<String>,
        val strength: Strength,
        val algorithmName: String = "AMPG v1"
    )

    enum class Strength { WEAK, MEDIUM, STRONG, VERY_STRONG }

    data class GenerationParams(
        val phrase: String,
        val serviceName: String,
        val rotationMonth: Int? = null, // null = использовать текущий
        val rotationYear: Int? = null,
        val targetLength: Int = 16,
        val includeLeet: Boolean = true,
        val includeServiceCode: Boolean = true,
        val includeRotationCode: Boolean = true
    )

    // Фиксированные leet-замены (без случайности)
    private val leetMap = mapOf(
        'a' to '@',
        'o' to '0',
        'e' to '3',
        'i' to '1',
        's' to '$'
    )

    // Транслитерация кириллицы в латиницу
    private val translitMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch",
        'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    // Согласные для мнемонического блока
    private val consonants = setOf(
        'b', 'c', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm',
        'n', 'p', 'q', 'r', 's', 't', 'v', 'w', 'x', 'y', 'z'
    )

    fun generate(params: GenerationParams): GenerationResult {
        val steps = mutableListOf<String>()

        // Шаг 1: Очистка фразы
        val cleanedPhrase = params.phrase
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
        steps.add("1. Очистка фразы: '${cleanedPhrase}'")

        // Шаг 2: Разбиение на слова
        val words = cleanedPhrase.split(" ").filter { it.isNotBlank() }
        steps.add("2. Разбиение на слова: ${words.size} слов")

        // Шаг 3: Транслитерация каждого слова
        val transliteratedWords = words.map { word ->
            transliterate(word)
        }
        steps.add("3. Транслитерация: ${transliteratedWords.joinToString(" ")}")

        // Шаг 4: Мнемонические блоки
        val mnemonicBlocks = transliteratedWords.map { word ->
            createMnemonicBlock(word)
        }
        steps.add("4. Мнемонические блоки: ${mnemonicBlocks.joinToString("-")}")

        // Шаг 5: Соединение блоков
        var password = mnemonicBlocks.joinToString("-")
        steps.add("5. Соединение через дефис: $password")

        // Шаг 6: Код сервиса
        var serviceCode = ""
        if (params.includeServiceCode && params.serviceName.isNotBlank()) {
            serviceCode = createServiceCode(params.serviceName)
            password = "$password-$serviceCode"
            steps.add("6. Код сервиса: $serviceCode")
        }

        // Шаг 7: Код ротации
        var rotationCode = ""
        if (params.includeRotationCode) {
            rotationCode = createRotationCode(params.rotationMonth, params.rotationYear)
            password = "$password-$rotationCode"
            steps.add("7. Код ротации: $rotationCode")
        }

        // Шаг 8: Leet-замены (фиксированные, не случайные)
        if (params.includeLeet) {
            password = applyLeet(password)
            steps.add("8. Leet-замены (фиксированные): $password")
        }

        // Шаг 9: Добивание до targetLength понятным суффиксом
        if (password.length < params.targetLength) {
            val suffix = createPaddingSuffix(params.serviceName, password.length, params.targetLength)
            password = "$password$suffix"
            steps.add("9. Добивание суффиксом: $password")
        }

        // Подсказка (не дублирует пароль!)
        val hint = buildHint(params, rotationCode)

        // Оценка сложности
        val strength = calculateStrength(password)

        return GenerationResult(
            password = password,
            mnemonicHint = hint,
            rotationSuffix = rotationCode,
            steps = steps,
            strength = strength,
            algorithmName = "AMPG v1"
        )
    }

    private fun transliterate(text: String): String {
        val result = StringBuilder()
        for (char in text) {
            val lower = char.lowercaseChar()
            if (lower in translitMap) {
                result.append(translitMap[lower])
            } else if (char.isLetter()) {
                result.append(lower)
            }
        }
        return result.toString()
    }

    private fun createMnemonicBlock(word: String): String {
        if (word.isEmpty()) return ""

        val firstChar = word[0].uppercaseChar()
        val consonantsInWord = word.filter { it.lowercaseChar() in consonants }
        
        // Берём первую букву + согласные, ограничиваем 4 символами
        var block = "$firstChar"
        for (ch in consonantsInWord) {
            if (block.length >= 4) break
            if (ch.lowercaseChar() != firstChar.lowercaseChar()) {
                block += ch.lowercaseChar()
            }
        }
        
        // Если блок слишком короткий, добавляем символы из слова
        if (block.length < 2) {
            for (ch in word) {
                if (block.length >= 4) break
                if (ch.lowercaseChar() !in block.lowercase()) {
                    block += ch.lowercaseChar()
                }
            }
        }
        
        return block.take(4)
    }

    private fun createServiceCode(serviceName: String): String {
        val transliterated = transliterate(serviceName.lowercase())
        val cleaned = transliterated.filter { it.isLetterOrDigit() }
        
        if (cleaned.isEmpty()) return "Sv"
        
        // Первая буква заглавная + следующие 1-2 символа + длина
        val firstChar = cleaned[0].uppercaseChar()
        val nextChars = if (cleaned.length > 1) cleaned[1].lowercaseChar() else ""
        val length = cleaned.length
        
        return "$firstChar$nextChars$length"
    }

    private fun createRotationCode(month: Int?, year: Int?): String {
        val currentMonth = month ?: java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        val currentYear = year ?: (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) % 100)
        
        return String.format("%02d%02d", currentMonth, currentYear)
    }

    private fun applyLeet(text: String): String {
        return text.map { char ->
            leetMap[char.lowercaseChar()] ?: char
        }.joinToString("")
    }

    private fun createPaddingSuffix(serviceName: String, currentLength: Int, targetLength: Int): String {
        val needed = targetLength - currentLength
        if (needed <= 0) return ""
        
        // Понятный суффикс: повтор сервисного кода или -Sv
        val serviceCode = createServiceCode(serviceName)
        val suffix = "-$serviceCode"
        
        // Повторяем суффикс пока не достигнем нужной длины
        var result = ""
        while (result.length < needed) {
            result += suffix
        }
        
        return result.take(needed)
    }

    private fun buildHint(params: GenerationParams, rotationCode: String): String {
        val phrasePreview = if (params.phrase.length > 30) {
            params.phrase.take(30) + "..."
        } else {
            params.phrase
        }
        
        val servicePreview = if (params.serviceName.length > 15) {
            params.serviceName.take(15) + "..."
        } else {
            params.serviceName
        }
        
        return "$phrasePreview + $servicePreview + $rotationCode (AMPG v1)"
    }

    private fun calculateStrength(password: String): Strength {
        var score = 0
        if (password.length >= 12) score++
        if (password.length >= 16) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        if (password.contains("-")) score++
        
        return when {
            score >= 5 -> Strength.VERY_STRONG
            score >= 4 -> Strength.STRONG
            score >= 2 -> Strength.MEDIUM
            else -> Strength.WEAK
        }
    }
}
