package com.securevault.utils

import kotlin.random.Random

object PasswordGenerator {
    enum class Strength { WEAK, MEDIUM, STRONG, VERY_STRONG }

    data class Result(val password: String, val strength: Strength)

    fun generate(length: Int, useUpper: Boolean, useDigits: Boolean, useSpecial: Boolean): Result {
        require(length in 8..20) { "Длина должна быть от 8 до 20" }
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val digits = "0123456789"
        val special = "!@#$%^&*()-_=+"
        
        var pool = lower
        if (useUpper) pool += upper
        if (useDigits) pool += digits
        if (useSpecial) pool += special

        var pwd = (1..length).map { pool.random() }.joinToString("")
        // Гарантия наличия хотя бы одного символа каждого выбранного типа
        if (useUpper && pwd.none { it.isUpperCase() }) pwd = pwd.replaceRange(0, 1, upper.random().toString())
        if (useDigits && pwd.none { it.isDigit() }) pwd = pwd.replaceRange(0, 1, digits.random().toString())
        if (useSpecial && pwd.none { it in special }) pwd = pwd.replaceRange(0, 1, special.random().toString())

        val strength = when {
            length >= 16 && useUpper && useDigits && useSpecial -> Strength.VERY_STRONG
            length >= 12 && useUpper && useDigits -> Strength.STRONG
            length >= 10 -> Strength.MEDIUM
            else -> Strength.WEAK
        }
        return Result(pwd, strength)
    }
}
