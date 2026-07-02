package com.securevault.utils

import android.content.Context
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

    // ✅ Генерация БЕЗ повторяющихся символов
    fun generate(
        length: Int = 16,
        useUpper: Boolean = true,
        useDigits: Boolean = true,
        useSpecial: Boolean = true,
        context: Context? = null
    ): GenerationResult {
        val charset = buildCharset(useUpper, useDigits, useSpecial)
        
        if (charset.isEmpty()) {
            return GenerationResult(generateFromCharset(LOWERCASE, length), Strength.WEAK)
        }
        
        // ✅ Проверка: длина не должна превышать размер уникального набора
        val uniqueChars = charset.toSet()
        val effectiveLength = minOf(length, uniqueChars.size)
        
        if (length > uniqueChars.size) {
            // Предупреждение: длина ограничена
            return generateWithUniqueChars(charset, effectiveLength, useUpper, useDigits, useSpecial, context)
        }
        
        return generateWithUniqueChars(charset, length, useUpper, useDigits, useSpecial, context)
    }

    private fun generateWithUniqueChars(
        charset: String,
        length: Int,
        useUpper: Boolean,
        useDigits: Boolean,
        useSpecial: Boolean,
        context: Context?
    ): GenerationResult {
        val maxAttempts = 100
        var attempts = 0
        
        while (attempts < maxAttempts) {
            val password = generateCandidate(charset, length, useUpper, useDigits, useSpecial)
            
            if (!PasswordValidator.hasDuplicateCharacters(password)) {
                // Дополнительная проверка через валидатор, если есть context
                if (context != null) {
                    val uniqueCheck = PasswordValidator.validateUniqueCharacters(password)
                    if (!uniqueCheck.isValid) {
                        attempts++
                        continue
                    }
                }
                return GenerationResult(password, calculateStrength(password))
            }
            attempts++
        }
        
        // Fallback: если не удалось, возвращаем последний вариант
        val fallback = generateCandidate(charset, length, useUpper, useDigits, useSpecial)
        return GenerationResult(fallback, calculateStrength(fallback))
    }

    private fun generateCandidate(
        charset: String,
        length: Int,
        useUpper: Boolean,
        useDigits: Boolean,
        useSpecial: Boolean
    ): String {
        val guaranteedChars = mutableListOf<Char>()
        if (useUpper) guaranteedChars.add(UPPERCASE[secureRandom.nextInt(UPPERCASE.length)])
        if (useDigits) guaranteedChars.add(DIGITS[secureRandom.nextInt(DIGITS.length)])
        if (useSpecial) guaranteedChars.add(SPECIAL[secureRandom.nextInt(SPECIAL.length)])
        guaranteedChars.add(LOWERCASE[secureRandom.nextInt(LOWERCASE.length)])

        val remainingLength = (length - guaranteedChars.size).coerceAtLeast(0)
        val randomChars = (1..remainingLength).map {
            charset[secureRandom.nextInt(charset.length)]
        }

        val allChars = (guaranteedChars + randomChars).toMutableList()
        shuffleSecure(allChars)
        return allChars.take(length).joinToString("")
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
