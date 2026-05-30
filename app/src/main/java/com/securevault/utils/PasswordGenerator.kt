package com.securevault.utils

import kotlin.random.Random

/**
 * Умный генератор паролей с поддержкой мнемоники и персональными настройками
 */
object PasswordGenerator {
    
    //  Словарь русских слов для мнемонических паролей (локальный, без интернета)
    private val MNEMONIC_WORDS = listOf(
        "кошка", "гора", "солнце", "река", "лес", "небо", "ветер", "дождь",
        "огонь", "вода", "земля", "луна", "звезда", "птица", "рыба", "цветок",
        "дерево", "камень", "море", "песок", "снег", "туча", "радуга", "тень",
        "свет", "тишина", "музыка", "книга", "ручка", "стол", "окно", "дверь",
        "ключ", "замок", "часы", "карта", "путь", "мечта", "сила", "ум",
        "сердце", "душа", "смех", "друг", "семья", "дом", "мир", "жизнь"
    )
    
    //  Наборы символов
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "23456789"  // Исключаем 0 и 1
    private const val SPECIAL = "!@#$%^&*()-_=+[]{}|;:,.<>?"
    
    //  Символы, которые легко перепутать (исключаются при опции)
    private const val SIMILAR = "O0Il1"
    
    /**
     * Настройки генерации
     */
    data class GeneratorOptions(
        val length: Int = 16,                    // Длина пароля (8-64)
        val useUppercase: Boolean = true,        // Заглавные буквы
        val useDigits: Boolean = true,           // Цифры
        val useSpecial: Boolean = false,         // Спецсимволы
        val excludeSimilar: Boolean = true,      // Исключить 0/O, 1/l, I
        val mnemonicMode: Boolean = false,       // Режим "запоминаемый пароль"
        val mnemonicWordCount: Int = 4           // Количество слов в мнемонике (3-5)
    )
    
    /**
     * Результат генерации
     */
    data class GenerationResult(
        val password: String,
        val strength: Strength,
        val mnemonicHint: String? = null  // Подсказка для запоминания
    )
    
    /**
     * Уровень сложности пароля
     */
    enum class Strength(val score: Int, val colorHex: String) {
        WEAK(1, "#FF6B6B"),      // Красный
        MEDIUM(2, "#FFB74D"),    // Оранжевый
        STRONG(3, "#4CAF50"),    // Зелёный
        VERY_STRONG(4, "#2E7D32") // Тёмно-зелёный
    }
    
    /**
     * Генерирует пароль по настройкам
     */
    fun generate(options: GeneratorOptions = GeneratorOptions()): GenerationResult {
        return if (options.mnemonicMode) {
            generateMnemonic(options)
        } else {
            generateRandom(options)
        }
    }
    
     *  Режим: случайный пароль
     */
    private fun generateRandom(options: GeneratorOptions): GenerationResult {
        var charset = LOWERCASE
        if (options.useUppercase) charset += if (options.excludeSimilar) {
            UPPERCASE.filter { it !in SIMILAR }
        } else {
            UPPERCASE
        }
        if (options.useDigits) charset += if (options.excludeSimilar) {
            DIGITS  // уже без 0 и 1
        } else {
            DIGITS + "01"
        }
        if (options.useSpecial) charset += SPECIAL
        if (options.excludeSimilar) {
            charset = charset.filter { it !in SIMILAR }
        }
        
        // Гарантируем наличие хотя бы одного символа из каждого выбранного набора
        val password = StringBuilder()
        if (options.useUppercase) password.append(charset.firstOrNull { it in UPPERCASE } ?: 'A')
        if (options.useDigits) password.append(charset.firstOrNull { it.isDigit() } ?: '2')
        if (options.useSpecial) password.append(SPECIAL.random())
        
        // Заполняем оставшуюся длину
        while (password.length < options.length.coerceIn(8, 64)) {
            password.append(charset.random())
        }
        
        //  ИСПРАВЛЕНО: shuffled() возвращает List<Char> → joinToString("")
        val shuffled = password.toString().toList().shuffled().joinToString("")
        
        return GenerationResult(
            password = shuffled,
            strength = calculateStrength(shuffled, options)
        )
    }
    
    /**
     *  Режим: мнемонический пароль (3-4 осмысленных слова)
     */
    private fun generateMnemonic(options: GeneratorOptions): GenerationResult {
        val wordCount = options.mnemonicWordCount.coerceIn(3, 5)
        val words = MutableList(wordCount) { MNEMONIC_WORDS.random() }
        
        // Добавляем разделитель и суффикс для сложности
        val separator = if (options.useSpecial) listOf("_", "-", ".", "!").random() else "_"
        val suffix = if (options.useDigits) Random.nextInt(2, 99).toString() else ""
        
        val password = words.joinToString(separator) + suffix
        
        // Подсказка для запоминания
        val hint = words.joinToString(" ") + if (suffix.isNotEmpty()) ", $suffix" else ""
        
        return GenerationResult(
            password = password,
            strength = calculateStrength(password, options),
            mnemonicHint = hint
        )
    }
    
    /**
     *  Расчёт сложности пароля
     */
    private fun calculateStrength(password: String, options: GeneratorOptions): Strength {
        var score = 0
        
        // Длина
        score += when {
            password.length >= 20 -> 2
            password.length >= 12 -> 1
            else -> 0
        }
        
        // Разнообразие символов
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        
        // Мнемоника добавляет +1 за запоминаемость
        if (options.mnemonicMode) score++
        
        return when {
            score >= 6 -> Strength.VERY_STRONG
            score >= 4 -> Strength.STRONG
            score >= 2 -> Strength.MEDIUM
            else -> Strength.WEAK
        }
    }
    
    /**
     *  Массовая генерация (создать N вариантов)
     */
    fun generateBatch(count: Int = 5, options: GeneratorOptions = GeneratorOptions()): List<GenerationResult> {
        return List(count) { generate(options) }
    }
    
    /**
     *  Фильтрация похожих символов из строки
     */
    fun removeSimilarChars(text: String): String {
        return text.filter { it !in SIMILAR }
    }
}
