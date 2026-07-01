package com.securevault.utils

import java.security.SecureRandom

object PasswordGenerator {

    enum class Strength { WEAK, MEDIUM, STRONG, VERY_STRONG }

    data class GenerationResult(
        val password: String,
        val strength: Strength
    )

    private val secureRandom = SecureRandom()

    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SPECIAL = "!@#$%^&*()-_=+[]{}|;:,.<>?"

    fun generate(
        length: Int = 16,
        useUpper: Boolean = true,
        useDigits: Boolean = true,
        useSpecial: Boolean = true
    ): GenerationResult {
        val charset = buildCharset(useUpper, useDigits, useSpecial)
        
        if (charset.isEmpty()) {
            return GenerationResult(generateFromCharset(LOWERCASE, length), Strength.WEAK)
        }

        // Гарантируем наличие хотя бы одного символа каждого выбранного типа
        val guaranteedChars = mutableListOf<Char>()
        if (useUpper) guaranteedChars.add(UPPERCASE[secureRandom.nextInt(UPPERCASE.length)])
        if (useDigits) guaranteedChars.add(DIGITS[secureRandom.nextInt(DIGITS.length)])
        if (useSpecial) guaranteedChars.add(SPECIAL[secureRandom.nextInt(SPECIAL.length)])
        guaranteedChars.add(LOWERCASE[secureRandom.nextInt(LOWERCASE.length)])

        // Оставшуюся длину заполняем случайными символами
        val remainingLength = (length - guaranteedChars.size).coerceAtLeast(0)
        val randomChars = (1..remainingLength).map {
            charset[secureRandom.nextInt(charset.length)]
        }

        // Объединяем и перемешиваем
        val allChars = (guaranteedChars + randomChars).toMutableList()
        shuffleSecure(allChars)

        val password = allChars.take(length).joinToString("")
        val strength = calculateStrength(password)

        return GenerationResult(password, strength)
    }

    private fun buildCharset(useUpper: Boolean, useDigits: Boolean, useSpecial: Boolean): String {
        val sb = StringBuilder(LOWERCASE)
        if (useUpper) sb.append(UPPERCASE)
        if (useDigits) sb.append(DIGITS)
        if (useSpecial) sb.append(SPECIAL)
        return sb.toString()
    }

    private fun generateFromCharset(charset: String, length: Int): String {
        return (1..length).map {
            charset[secureRandom.nextInt(charset.length)]
        }.joinToString("")
    }

    private fun shuffleSecure(list: MutableList<Char>) {
        for (i in list.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temp = list[i]
            list[i] = list[j]
            list[j] = temp
        }
    }

    private fun calculateStrength(password: String): Strength {
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.length >= 16) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        return when {
            score >= 6 -> Strength.VERY_STRONG
            score >= 4 -> Strength.STRONG
            score >= 2 -> Strength.MEDIUM
            else -> Strength.WEAK
        }
    }
}
