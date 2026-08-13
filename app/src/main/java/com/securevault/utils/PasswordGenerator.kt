package com.securevault.utils

import android.content.Context
import java.security.SecureRandom

object PasswordGenerator {
    enum class Strength { WEAK, MEDIUM, STRONG, VERY_STRONG }
    data class GenerationResult(val password: String, val strength: Strength, val explanation: String = "")

    private val secureRandom = SecureRandom()

    fun generate(length: Int, useUpper: Boolean, useDigits: Boolean, useSpecial: Boolean, context: Context): GenerationResult {
        var attempts = 0
        while (attempts < 200) {
            val pwd = generateSecureString(length, useUpper, useDigits, useSpecial)
            if (isValidPassword(pwd)) return GenerationResult(pwd, calculateStrength(pwd))
            attempts++
        }
        throw IllegalStateException("Не удалось сгенерировать валидный пароль")
    }

    fun generateTwoPart(length: Int, useUpper: Boolean, useDigits: Boolean, useSpecial: Boolean, context: Context): GenerationResult? {
        if (length !in listOf(16, 18, 20)) return null
        val halfLen = length / 2
        var attempts = 0
        
        while (attempts < 500) {
            val part1 = generateSecureString(halfLen, useUpper, useDigits, useSpecial)
            val part2 = generateSecureString(length - halfLen, useUpper, useDigits, useSpecial)
            val full = part1 + part2
            
            if (isValidTwoPart(full, halfLen)) {
                return GenerationResult(full, calculateStrength(full), "Пароль разделен на две части по $halfLen символов. Каждая часть содержит минимум 2 заглавные, 2 строчные, 2 цифры и 2 спецсимвола. Повторов во всем пароле нет.")
            }
            attempts++
        }
        return null
    }

    fun generateWithAnchor(anchorWord: String, length: Int, useUpper: Boolean, useDigits: Boolean, useSpecial: Boolean, context: Context): GenerationResult? {
        if (anchorWord.isBlank() || anchorWord.length >= length) return null
        
        val leetMap = mapOf('а' to "@", 'a' to "@", 'о' to "0", 'o' to "0", 'т' to "7", 't' to "7", 'ч' to "4", 'с' to "$", 's' to "$", 'и' to "1", 'i' to "1", 'й' to "1", 'б' to "6", 'b' to "6", 'л' to "!", 'l' to "!")
        val usedChars = mutableSetOf<Char>()
        val anchorBlock = StringBuilder()
        val explanation = StringBuilder("Якорь: '$anchorWord' -> ")

        val transliterated = anchorWord.lowercase().replace(Regex("[^а-яёa-z]"), "") { 
            val map = mapOf('а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya")
            map[it.value] ?: it.value 
        }

        if (transliterated.isEmpty()) return null

        for ((i, c) in transliterated.withIndex()) {
            val lowerC = c.lowercaseChar()
            val replacement = leetMap[lowerC]
            var chosen = if (i == 0) c.uppercaseChar() else lowerC
            
            if (replacement != null && i > 0) {
                val repChar = replacement.first()
                if (!usedChars.contains(repChar)) chosen = repChar
            }
            
            if (!usedChars.contains(chosen.lowercaseChar())) {
                anchorBlock.append(chosen)
                usedChars.add(chosen.lowercaseChar())
                explanation.append(chosen)
            }
        }

        if (anchorBlock.length >= length) return null

        var attempts = 0
        while (attempts < 500) {
            val randomPart = generateSecureString(length - anchorBlock.length, useUpper, useDigits, useSpecial)
            val full = anchorBlock.toString() + randomPart
            val lower = full.lowercase()
            
            if (lower.length == lower.toSet().size && isValidPassword(full)) {
                return GenerationResult(full, calculateStrength(full), "${explanation.toString()}. Остальная часть дополнена криптостойким случайным набором символов.")
            }
            attempts++
        }
        return null
    }

    private fun generateSecureString(length: Int, useUpper: Boolean, useDigits: Boolean, useSpecial: Boolean): String {
        val chars = buildCharPool(useUpper, useDigits, useSpecial)
        return (1..length).map { chars[secureRandom.nextInt(chars.size)] }.joinToString("")
    }

    private fun buildCharPool(useUpper: Boolean, useDigits: Boolean, useSpecial: Boolean): List<Char> {
        val pool = mutableListOf<Char>()
        pool.addAll(('a'..'z'))
        if (useUpper) pool.addAll(('A'..'Z'))
        if (useDigits) pool.addAll(('0'..'9'))
        if (useSpecial) pool.addAll(listOf('!', '@', '#', '$', '%', '^', '&', '*', '?'))
        return pool
    }

    private fun isValidPassword(password: String): Boolean {
        val lower = password.lowercase()
        if (lower.length != lower.toSet().size) return false
        return password.any { it.isUpperCase() } && password.any { it.isLowerCase() } && 
               password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() }
    }

    private fun isValidTwoPart(password: String, halfLen: Int): Boolean {
        val lower = password.lowercase()
        if (lower.length != lower.toSet().size) return false
        return checkPartComplexity(password.substring(0, halfLen)) && checkPartComplexity(password.substring(halfLen))
    }

    private fun checkPartComplexity(part: String): Boolean {
        return part.count { it.isUpperCase() } >= 2 && part.count { it.isLowerCase() } >= 2 &&
               part.count { it.isDigit() } >= 2 && part.count { !it.isLetterOrDigit() } >= 2
    }

    private fun calculateStrength(password: String): Strength {
        val score = when {
            password.length >= 16 && password.any { it.isUpperCase() } && password.any { it.isLowerCase() } && password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() } -> 4
            password.length >= 12 && password.any { it.isUpperCase() } && password.any { it.isLowerCase() } && password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() } -> 3
            else -> 2
        }
        return when (score) { 4 -> Strength.VERY_STRONG; 3 -> Strength.STRONG; else -> Strength.MEDIUM }
    }
}
