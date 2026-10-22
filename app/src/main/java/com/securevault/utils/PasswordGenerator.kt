package com.securevault.utils

import java.security.SecureRandom

object PasswordGenerator {
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SPECIALS = "!@#$%^&*()-_=+[]{}|;:,.<>?"
    private val random = SecureRandom()
    enum class Strength { WEAK, MEDIUM, STRONG, VERY_STRONG }
    data class GenerationResult(val password: String, val explanation: String = "")
    data class AnchorGenerationResult(val password: String, val explanation: String)

    private fun categories(lower: Boolean, upper: Boolean, digits: Boolean, special: Boolean) =
        listOfNotNull(LOWER.takeIf { lower }, UPPER.takeIf { upper },
            DIGITS.takeIf { digits }, SPECIALS.takeIf { special })

    private fun build(length: Int, groups: List<String>, parts: Int, quota: Int): String {
        require(groups.isNotEmpty()) { "Выберите хотя бы одну категорию" }
        require(length in 1..56 && length % parts == 0) { "Некорректная длина пароля" }
        val half = length / parts
        require(half >= quota * groups.size) { "Для каждой части нужно минимум ${quota * groups.size} символов" }
        val pool = groups.joinToString("")
        require(length <= pool.lowercase().toSet().size) { "Не хватает уникальных символов выбранных категорий" }
        val used = mutableSetOf<Char>()
        val result = List(parts) { mutableListOf<Char>() }
        fun pick(group: String): Char {
            val available = group.filter { it.lowercaseChar() !in used }
            require(available.isNotEmpty()) { "Не хватает уникальных символов" }
            return available[random.nextInt(available.length)].also { used.add(it.lowercaseChar()) }
        }
        // Сначала резервируем квоты всех частей, затем заполняем остаток.
        for (part in result) for (group in groups) repeat(quota) { part.add(pick(group)) }
        for (part in result) {
            while (part.size < half) part.add(pick(pool))
            part.shuffle(random)
        }
        return result.joinToString("") { it.joinToString("") }
    }
    fun generate(length: Int, useLower: Boolean, useUpper: Boolean,
                 useDigits: Boolean, useSpecials: Boolean): Result<GenerationResult> = runCatching {
        GenerationResult(build(length, categories(useLower, useUpper, useDigits, useSpecials), 1, 1))
    }
    fun generateTwoPart(length: Int, useLower: Boolean, useUpper: Boolean,
                        useDigits: Boolean, useSpecials: Boolean): Result<GenerationResult> = runCatching {
        GenerationResult(build(length, categories(useLower, useUpper, useDigits, useSpecials), 2, 2))
    }
    private val translit = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya")
    private fun latin(word: String) = word.lowercase().map { translit[it] ?: it.toString() }.joinToString("")
    fun generateWithAnchor(anchorWord: String, totalLength: Int, useLower: Boolean,
        useUpper: Boolean, useDigits: Boolean, useSpecials: Boolean,
        addService: Boolean = false, serviceName: String = "", addYear: Boolean = false,
        year: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    ): Result<AnchorGenerationResult> = runCatching {
        require(anchorWord.isNotBlank()) { "Введите якорное слово" }
        val groups = categories(useLower, useUpper, useDigits, useSpecials)
        val pool = groups.joinToString("")
        require(groups.isNotEmpty() && totalLength in 1..56) { "Проверьте длину и категории" }
        require(totalLength <= pool.lowercase().toSet().size) { "Не хватает уникальных символов" }
        val result = mutableListOf<Char>()
        val used = mutableSetOf<Char>()
        val leet = mapOf('a' to '@', 'o' to '0', 't' to '7', 's' to '$', 'i' to '1', 'b' to '6', 'l' to '!')
        fun add(ch: Char) {
            require(ch in pool) { "Якорь или маркер не соответствует выбранным категориям" }
            require(used.add(ch.lowercaseChar())) { "Маркер повторяет символ якоря" }
            result.add(ch)
        }
        for (ch in latin(anchorWord)) {
            if (ch !in LOWER) continue
            val candidate = if (result.isEmpty() && useUpper) ch.uppercaseChar()
                else leet[ch]?.takeIf { it in pool && it.lowercaseChar() !in used }
                    ?: if (useLower) ch else ch.uppercaseChar()
            if (candidate.lowercaseChar() !in used) add(candidate)
        }
        require(result.isNotEmpty()) { "Якорь должен содержать буквы" }
        if (addService) {
            val first = latin(serviceName).firstOrNull { it in LOWER }
                ?: error("Введите название сервиса для маркера")
            add(if (useUpper) first.uppercaseChar() else first)
        }
        if (addYear) year.toString().takeLast(2).forEach { add(it) }
        val missing = groups.filter { group -> result.none { it in group } }
        require(result.size + missing.size <= totalLength) {
            "Якорь с маркерами слишком длинный: увеличьте длину или сократите якорь"
        }
        fun pick(group: String) {
            val available = group.filter { it.lowercaseChar() !in used }
            require(available.isNotEmpty()) { "Не хватает уникальных символов" }
            add(available[random.nextInt(available.length)])
        }
        missing.forEach { pick(it) }
        while (result.size < totalLength) pick(pool)
        AnchorGenerationResult(result.joinToString(""), "Якорь: ${anchorWord.trim()}; длина: $totalLength")
    }
}
