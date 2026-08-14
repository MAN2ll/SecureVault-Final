package com.securevault.utils

import android.content.Context
import java.security.SecureRandom

object PasswordGenerator {
    enum class Strength { WEAK, MEDIUM, STRONG, VERY_STRONG }
    data class GenerationResult(val password: String, val strength: Strength, val explanation: String = "")

    private val secureRandom = SecureRandom()
    
    private val translitMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 
        'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

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
            val part1 = buildValidHalf(halfLen, useUpper, useDigits, useSpecial, mutableSetOf())
            if (part1 == null) { attempts++; continue }
            
            val usedInPart1 = part1.map { it.lowercaseChar() }.toMutableSet()
            val part2 = buildValidHalf(length - halfLen, useUpper, useDigits, useSpecial, usedInPart1)
            
            if (part2 != null) {
                val full = part1 + part2
                val lower = full.lowercase()
                if (lower.length == lower.toSet().size && isValidPassword(full)) {
                    return GenerationResult(full, calculateStrength(full), "Пароль разделен на две части. Каждая часть содержит минимум 2 заглавные, 2 строчные, 2 цифры и 2 спецсимвола. Повторов во всем пароле нет.")
                }
            }
            attempts++
        }
        return null
    }

    private fun buildValidHalf(length: Int, useUpper: Boolean, useDigits: Boolean, useSpecial: Boolean, usedChars: MutableSet<Char>): String? {
        val upperPool = ('A'..'Z').toMutableList()
        val lowerPool = ('a'..'z').toMutableList()
        val digitPool = ('0'..'9').toMutableList()
        val specialPool = listOf('!', '@', '#', '$', '%', '^', '&', '*', '?').toMutableList()

        val chosen = mutableSetOf<Char>()
        
        // Гарантированный выбор уникальных символов каждого типа
        repeat(2) {
            val c = pickUnique(upperPool, usedChars, chosen) ?: return null
            chosen.add(c); usedChars.add(c.lowercaseChar())
        }
        repeat(2) {
            val c = pickUnique(lowerPool, usedChars, chosen) ?: return null
            chosen.add(c); usedChars.add(c.lowercaseChar())
        }
        repeat(2) {
            val c = pickUnique(digitPool, usedChars, chosen) ?: return null
            chosen.add(c); usedChars.add(c.lowercaseChar())
        }
        repeat(2) {
            val c = pickUnique(specialPool, usedChars, chosen) ?: return null
            chosen.add(c); usedChars.add(c.lowercaseChar())
        }

        val all = mutableListOf<Char>().apply {
            if (useUpper) addAll(upperPool)
            addAll(lowerPool); addAll(digitPool); addAll(specialPool)
        }
        
        var attempts = 0
        while (chosen.size < length && attempts < 100) {
            val c = all[secureRandom.nextInt(all.size)]
            if (!usedChars.contains(c.lowercaseChar())) {
                chosen.add(c)
                usedChars.add(c.lowercaseChar())
            }
            attempts++
        }
        
        return if (chosen.size == length) chosen.shuffled(secureRandom).joinToString("") else null
    }

    private fun pickUnique(pool: MutableList<Char>, usedChars: Set<Char>, chosen: Set<Char>): Char? {
        val available = pool.filter { !usedChars.contains(it.lowercaseChar()) && !chosen.contains(it) }
        return if (available.isNotEmpty()) available[secureRandom.nextInt(available.size)] else null
    }

    fun generateWithAnchor(anchorWord: String, length: Int, useUpper: Boolean, useDigits: Boolean, useSpecial: Boolean, context: Context, addService: Boolean = false, serviceName: String = "", addYear: Boolean = false, year: Int? = null): GenerationResult? {
        if (anchorWord.isBlank() || anchorWord.length >= length) return null
        
        val leetMap = mapOf("a" to "@", "o" to "0", "t" to "7", "ch" to "4", "s" to "$", "i" to "1", "b" to "6", "l" to "!")
        val usedChars = mutableSetOf<Char>()
        val anchorBlock = StringBuilder()
        val explanation = StringBuilder("Якорное слово: $anchorWord\nЯкорный блок: ")

        val transliterated = anchorWord.lowercase().map { translitMap[it] ?: it.toString() }.joinToString("").replace(Regex("[^a-z]"), "")
        if (transliterated.isEmpty()) return null

        var pos = 0
        while (pos < transliterated.length && anchorBlock.length < length) {
            val c = transliterated[pos]
            val lowerC = c.lowercaseChar()
            
            var chosen = if (pos == 0) c.uppercaseChar() else lowerC
            val leetKey = if (lowerC == 'c' && pos + 1 < transliterated.length && transliterated[pos + 1] == 'h') "ch" else lowerC.toString()
            val replacement = leetMap[leetKey]
            
            if (replacement != null && pos > 0) {
                val repChar = replacement.first()
                if (!usedChars.contains(repChar)) chosen = repChar
            }
            
            if (!usedChars.contains(chosen.lowercaseChar())) {
                anchorBlock.append(chosen)
                usedChars.add(chosen.lowercaseChar())
                
                if (pos == 0) explanation.append("$chosen — первая буква\n")
                else if (replacement != null && chosen != lowerC) explanation.append("$lowerC -> $chosen\n")
                else explanation.append("$chosen — сохранённая уникальная буква\n")
            }
            
            pos += if (leetKey == "ch") 2 else 1
        }

        if (anchorBlock.isEmpty()) return null

        val overhead = (if (addService && serviceName.isNotEmpty()) 1 else 0) + (if (addYear && year != null) 2 else 0)
        val randomLen = length - anchorBlock.length - overhead
        if (randomLen < 0) return null

        var attempts = 0
        while (attempts < 500) {
            val randomPart = generateSecureString(randomLen, useUpper, useDigits, useSpecial)
            val servicePart = if (addService && serviceName.isNotEmpty()) serviceName.first().uppercaseChar().toString() else ""
            val yearPart = if (addYear && year != null) year.toString().takeLast(2) else ""
            
            val full = servicePart + anchorBlock.toString() + randomPart + yearPart
            val lower = full.lowercase()
            
            if (lower.length == lower.toSet().size && isValidPassword(full)) {
                return GenerationResult(full, calculateStrength(full), "${explanation.toString()}Остальные символы добавлены случайно.")
            }
            attempts++
        }
        return null
    }

    private fun generateSecureString(length: Int, useUpper: Boolean, useDigits: Boolean, useSpecial: Boolean): String {
        val chars = mutableListOf<Char>().apply {
            addAll(('a'..'z'))
            if (useUpper) addAll(('A'..'Z'))
            if (useDigits) addAll(('0'..'9'))
            if (useSpecial) addAll(listOf('!', '@', '#', '$', '%', '^', '&', '*', '?'))
        }
        return (1..length).map { chars[secureRandom.nextInt(chars.size)] }.joinToString("")
    }

    private fun isValidPassword(password: String): Boolean {
        val lower = password.lowercase()
        if (lower.length != lower.toSet().size) return false
        return password.any { it.isUpperCase() } && password.any { it.isLowerCase() } && 
               password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() }
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
