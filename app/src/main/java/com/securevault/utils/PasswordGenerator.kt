package com.securevault.utils

import android.content.Context
import java.security.SecureRandom

object PasswordGenerator {

    enum class Strength {
        WEAK, MEDIUM, STRONG, VERY_STRONG
    }

    data class GenerationResult(
        val password: String,
        val strength: Strength
    )

    private val secureRandom = SecureRandom()

    fun generate(
        length: Int,
        useUpper: Boolean,
        useDigits: Boolean,
        useSpecial: Boolean,
        context: Context
    ): GenerationResult {
        var attempts = 0
        while (attempts < 100) {
            val password = generateSecureString(length, useUpper, useDigits, useSpecial)
            if (isValidPassword(password)) {
                return GenerationResult(password, calculateStrength(password))
            }
            attempts++
        }
        // Fallback
        val pwd = generateSecureString(length, useUpper, useDigits, useSpecial)
        return GenerationResult(pwd, calculateStrength(pwd))
    }

    // Случайный пароль из двух частей
    fun generateTwoPart(
        length: Int,
        useUpper: Boolean,
        useDigits: Boolean,
        useSpecial: Boolean,
        context: Context
    ): GenerationResult {
        if (length < 16) return generate(length, useUpper, useDigits, useSpecial, context)
        
        val halfLen = length / 2
        var attempts = 0
        
        while (attempts < 200) {
            val part1 = generateSecureString(halfLen, useUpper, useDigits, useSpecial)
            val part2 = generateSecureString(length - halfLen, useUpper, useDigits, useSpecial)
            val full = part1 + part2
            
            if (isValidTwoPart(full, halfLen)) {
                return GenerationResult(full, calculateStrength(full))
            }
            attempts++
        }
        
        // Fallback
        val pwd = generateSecureString(length, useUpper, useDigits, useSpecial)
        return GenerationResult(pwd, calculateStrength(pwd))
    }

    //  Случайный пароль с якорным словом
    fun generateWithAnchor(
        anchorWord: String,
        length: Int,
        useUpper: Boolean,
        useDigits: Boolean,
        useSpecial: Boolean,
        context: Context
    ): GenerationResult? {
        if (anchorWord.isBlank() || anchorWord.length >= length) return null
        
        val leetMap = mapOf(
            'а' to "@", 'a' to "@",
            'о' to "0", 'o' to "0",
            'т' to "7", 't' to "7",
            'ч' to "4",
            'с' to "$", 's' to "$",
            'и' to "1", 'i' to "1", 'й' to "1",
            'б' to "6", 'b' to "6",
            'л' to "!", 'l' to "!"
        )
        
        val usedChars = mutableSetOf<Char>()
        val anchorBlock = StringBuilder()
        
        // Первая буква — всегда заглавный якорь
        val firstChar = anchorWord.first().uppercaseChar()
        if (!usedChars.contains(firstChar.lowercaseChar())) {
            anchorBlock.append(firstChar)
            usedChars.add(firstChar.lowercaseChar())
        }
        
        // Остальные буквы — с заменами по таблице
        for (i in 1 until anchorWord.length) {
            val c = anchorWord[i]
            val lowerC = c.lowercaseChar()
            val replacement = leetMap[lowerC]
            
            var chosen = lowerC
            if (replacement != null) {
                val repChar = replacement.first()
                if (!usedChars.contains(repChar)) {
                    chosen = repChar
                }
            }
            
            if (!usedChars.contains(chosen)) {
                anchorBlock.append(chosen)
                usedChars.add(chosen)
            }
        }
        
        if (anchorBlock.length >= length) return null
        
        // Добор случайными символами
        var attempts = 0
        while (attempts < 200) {
            val randomPart = generateSecureString(length - anchorBlock.length, useUpper, useDigits, useSpecial)
            val full = anchorBlock.toString() + randomPart
            val lower = full.lowercase()
            
            if (lower.length == lower.toSet().size && isValidPassword(full)) {
                return GenerationResult(full, calculateStrength(full))
            }
            attempts++
        }
        
        return null
    }

    private fun generateSecureString(length: Int, useUpper: Boolean, useDigits: Boolean, useSpecial: Boolean): String {
        val chars = buildCharPool(useUpper, useDigits, useSpecial)
        if (chars.isEmpty()) return ""
        
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
        
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        
        return hasUpper && hasLower && hasDigit && hasSpecial
    }

    private fun isValidTwoPart(password: String, halfLen: Int): Boolean {
        val lower = password.lowercase()
        if (lower.length != lower.toSet().size) return false
        
        val part1 = password.substring(0, halfLen)
        val part2 = password.substring(halfLen)
        
        return checkPartComplexity(part1) && checkPartComplexity(part2)
    }

    private fun checkPartComplexity(part: String): Boolean {
        val upper = part.count { it.isUpperCase() }
        val lower = part.count { it.isLowerCase() }
        val digit = part.count { it.isDigit() }
        val special = part.count { !it.isLetterOrDigit() }
        return upper >= 2 && lower >= 2 && digit >= 2 && special >= 2
    }

    fun calculateStrength(password: String): Strength {
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
            4 -> Strength.VERY_STRONG
            3 -> Strength.STRONG
            2 -> Strength.MEDIUM
            else -> Strength.WEAK
        }
    }
}
