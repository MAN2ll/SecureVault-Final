package com.securevault.utils

object PasswordGenerator {
    enum class Strength { WEAK, MEDIUM, STRONG, VERY_STRONG }
    data class Result(val password: String, val strength: Strength)

    fun generate(length: Int = 16, useUpper: Boolean = true, useDigits: Boolean = true, useSpecial: Boolean = true): Result {
        val safeLength = length.coerceIn(8, 20)
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val digits = "0123456789"
        val special = "!@#$%^&*()-_=+[]{}|;:,.<>?"
        
        var pool = lower
        if (useUpper) pool += upper
        if (useDigits) pool += digits
        if (useSpecial) pool += special

        var pwd = (1..safeLength).map { pool.random() }.joinToString("")
        if (useUpper && pwd.none { it.isUpperCase() }) pwd = pwd.replaceRange(0, 1, upper.random().toString())
        if (useDigits && pwd.none { it.isDigit() }) pwd = pwd.replaceRange(0, 1, digits.random().toString())
        if (useSpecial && pwd.none { it in special }) pwd = pwd.replaceRange(0, 1, special.random().toString())

        val strength = when {
            safeLength >= 16 && useUpper && useDigits && useSpecial -> Strength.VERY_STRONG
            safeLength >= 12 && useUpper && useDigits -> Strength.STRONG
            safeLength >= 10 -> Strength.MEDIUM
            else -> Strength.WEAK
        }
        return Result(pwd, strength)
    }

    fun generateFromMnemonic(phrase: String, length: Int = 12, useUpper: Boolean = true, useDigits: Boolean = true, useSpecial: Boolean = true): Result {
        val words = phrase.trim().split(Regex("\\s+"))
        var base = words.joinToString("") { it.firstOrNull()?.toString()?.uppercase() ?: "" }
        base = base.replace("А", "A").replace("В", "B").replace("С", "C").replace("Е", "E")
                   .replace("К", "K").replace("М", "M").replace("Н", "H").replace("О", "O")
                   .replace("Р", "P").replace("Т", "T").replace("Х", "X").ifEmpty { "Pwd" }
        if (!useUpper) base = base.lowercase()
        if (useDigits) base += (10..99).random().toString()
        if (useSpecial) base += listOf("!", "@", "#", "$").random()
        val safeLength = length.coerceIn(8, 20)
        val password = if (base.length > safeLength) base.take(safeLength) else base.padEnd(safeLength, 'x')
        return Result(password, Strength.MEDIUM)
    }
}
