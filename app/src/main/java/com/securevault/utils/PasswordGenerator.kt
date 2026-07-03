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
        
        //  Более надёжная генерация с fallback
        return try {
            generateWithUniqueChars(charset, length, useUpper, useDigits, useSpecial, context)
        } catch (e: Exception) {
            //  Fallback: если не удалось сгенерировать без повторов, генерируем обычный пароль
            // Это крайний случай, но лучше вернуть пароль с возможными повторами, чем ничего
            val fallback = generateFromCharset(charset, length)
            GenerationResult(fallback, calculateStrength(fallback))
        }
    }

    private fun generateWithUniqueChars(
        charset: String,
        length: Int,
        useUpper: Boolean,
        useDigits: Boolean,
        useSpecial: Boolean,
        context: Context?
    ): GenerationResult {
        //  Группируем символы по "семействам" (A и a — одно семейство)
        val families = groupIntoFamilies(charset)
        
        //  Ограничиваем длину доступным числом семейств
        val effectiveLength = minOf(length, families.size)
        
        val maxAttempts = 500 //  Увеличено с 100 до 500
        var attempts = 0
        
        while (attempts < maxAttempts) {
            val password = generateCandidateFromFamilies(families, effectiveLength, useUpper, useDigits, useSpecial)
            
            //  Дополнительная проверка
            if (!PasswordValidator.hasDuplicateCharacters(password)) {
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
        
        //  Если не удалось сгенерировать за 500 попыток — выбрасываем исключение
        // Оно будет перехвачено в generate() и использован fallback
        throw IllegalStateException("Не удалось сгенерировать пароль без повторяющихся символов")
    }

    //  Группировка символов по семействам
    private fun groupIntoFamilies(charset: String): List<List<Char>> {
        val familyMap = mutableMapOf<Char, MutableList<Char>>()
        
        for (char in charset) {
            val familyKey = char.lowercaseChar()
            if (familyMap.containsKey(familyKey)) {
                familyMap[familyKey]!!.add(char)
            } else {
                familyMap[familyKey] = mutableListOf(char)
            }
        }
        
        return familyMap.values.toList()
    }

    // Генерация из семейств (гарантирует уникальность)
    private fun generateCandidateFromFamilies(
        families: List<List<Char>>,
        length: Int,
        useUpper: Boolean,
        useDigits: Boolean,
        useSpecial: Boolean
    ): String {
        val result = mutableListOf<Char>()
        val availableFamilies = families.toMutableList()
        
        //  Гарантированные символы (по одному из каждого типа)
        val guaranteedFamilies = mutableListOf<List<Char>>()
        
        if (useUpper) {
            val upperFamily = availableFamilies.find { family ->
                family.any { it.isUpperCase() && it.isLetter() }
            }
            if (upperFamily != null) {
                guaranteedFamilies.add(upperFamily)
                availableFamilies.remove(upperFamily)
            }
        }
        
        if (useDigits) {
            val digitFamily = availableFamilies.find { family ->
                family.any { it.isDigit() }
            }
            if (digitFamily != null) {
                guaranteedFamilies.add(digitFamily)
                availableFamilies.remove(digitFamily)
            }
        }
        
        if (useSpecial) {
            val specialFamily = availableFamilies.find { family ->
                family.any { !it.isLetterOrDigit() }
            }
            if (specialFamily != null) {
                guaranteedFamilies.add(specialFamily)
                availableFamilies.remove(specialFamily)
            }
        }
        
        val lowerFamily = availableFamilies.find { family ->
            family.any { it.isLowerCase() && it.isLetter() }
        }
        if (lowerFamily != null) {
            guaranteedFamilies.add(lowerFamily)
            availableFamilies.remove(lowerFamily)
        }
        
        //  Выбираем по одному символу из каждого гарантированного семейства
        for (family in guaranteedFamilies) {
            if (result.size >= length) break
            result.add(family[secureRandom.nextInt(family.size)])
        }
        
        //  Дополняем случайными символами из оставшихся семейств
        val remainingLength = (length - result.size).coerceAtLeast(0)
        val shuffledFamilies = availableFamilies.shuffled(secureRandom)
        
        for (family in shuffledFamilies) {
            if (result.size >= length) break
            result.add(family[secureRandom.nextInt(family.size)])
        }
        
        // Перемешиваем результат
        shuffleSecure(result)
        
        return result.take(length).joinToString("")
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
