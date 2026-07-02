package com.securevault.utils

import com.securevault.data.Entry
import java.security.MessageDigest

/**
 * Валидатор паролей: проверка уникальности символов, повторов, 60% уникальности.
 */
object PasswordValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Проверка на повторяющиеся символы (без учёта регистра).
     */
    fun hasDuplicateCharacters(password: String): Boolean {
        val seen = mutableSetOf<Char>()
        for (char in password.lowercase()) {
            if (char.isWhitespace()) continue
            if (!seen.add(char)) return true
        }
        return false
    }

    /**
     * Валидация уникальности символов.
     */
    fun validateUniqueCharacters(password: String): ValidationResult {
        return if (hasDuplicateCharacters(password)) {
            ValidationResult(false, "Пароль содержит повторяющиеся символы. Создайте другой пароль.")
        } else {
            ValidationResult(true)
        }
    }

    /**
     * Построение fingerprint пароля (SHA-256) для сравнения.
     */
    fun buildPasswordFingerprint(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Проверка, использовался ли пароль для данной записи.
     */
    fun wasPasswordUsedForEntry(entry: Entry, password: String): Boolean {
        val newFingerprint = buildPasswordFingerprint(password)
        
        // Проверяем текущий пароль
        if (entry.passwordFingerprint == newFingerprint) return true
        
        // Проверяем историю
        val history = entry.getPasswordHistory()
        return history.any { it.passwordFingerprint == newFingerprint }
    }

    /**
     * Валидация, что пароль не использовался для данной записи.
     */
    fun validatePasswordNotReusedForEntry(entry: Entry, newPassword: String): ValidationResult {
        return if (wasPasswordUsedForEntry(entry, newPassword)) {
            ValidationResult(false, "Этот пароль уже использовался для данного сервиса. Выберите другой пароль.")
        } else {
            ValidationResult(true)
        }
    }

    /**
     * Расчёт процента отличия между двумя паролями.
     * Возвращает процент отличия (0-100).
     */
    fun calculatePasswordDifferencePercent(oldPassword: String, newPassword: String): Int {
        val old = oldPassword.lowercase().replace(" ", "")
        val new = newPassword.lowercase().replace(" ", "")
        
        if (old.isEmpty() || new.isEmpty()) return 100
        
        // 1. Сравнение по позициям
        val maxLength = maxOf(old.length, new.length)
        val minLength = minOf(old.length, new.length)
        var positionMatches = 0
        for (i in 0 until minLength) {
            if (old[i] == new[i]) positionMatches++
        }
        val positionScore = 1.0 - (positionMatches.toDouble() / maxLength)
        
        // 2. Сравнение множеств символов
        val oldSet = old.toSet()
        val newSet = new.toSet()
        val intersection = oldSet.intersect(newSet).size
        val union = oldSet.union(newSet).size
        val jaccardSimilarity = if (union == 0) 0.0 else intersection.toDouble() / union
        
        // 3. Сравнение пар символов (bigrams)
        val oldBigrams = getBigrams(old)
        val newBigrams = getBigrams(new)
        val bigramIntersection = oldBigrams.intersect(newBigrams).size
        val bigramUnion = oldBigrams.union(newBigrams).size
        val bigramSimilarity = if (bigramUnion == 0) 0.0 else bigramIntersection.toDouble() / bigramUnion
        
        // 4. Разница в длине
        val lengthDiff = kotlin.math.abs(old.length - new.length).toDouble() / maxLength
        
        // Итоговая оценка (взвешенная)
        val similarity = positionScore * 0.4 + jaccardSimilarity * 0.3 + bigramSimilarity * 0.2 + lengthDiff * 0.1
        val differencePercent = ((1.0 - similarity) * 100).toInt().coerceIn(0, 100)
        
        return differencePercent
    }

    private fun getBigrams(text: String): Set<String> {
        if (text.length < 2) return setOf(text)
        return (0 until text.length - 1).map { text.substring(it, it + 2) }.toSet()
    }

    /**
     * Проверка, что новый пароль отличается от предыдущего минимум на 60%.
     */
    fun isAtLeast60PercentUnique(oldPassword: String, newPassword: String): Boolean {
        return calculatePasswordDifferencePercent(oldPassword, newPassword) >= 60
    }

    /**
     * Комплексная валидация нового пароля для записи.
     */
    fun validateNewPasswordForEntry(
        entry: Entry,
        newPassword: String,
        checkHistory: Boolean = true
    ): ValidationResult {
        // 1. Проверка уникальных символов
        val uniqueCheck = validateUniqueCharacters(newPassword)
        if (!uniqueCheck.isValid) return uniqueCheck
        
        // 2. Проверка повтора пароля
        if (checkHistory) {
            val reuseCheck = validatePasswordNotReusedForEntry(entry, newPassword)
            if (!reuseCheck.isValid) return reuseCheck
        }
        
        // 3. Проверка 60% уникальности относительно предыдущего пароля
        if (checkHistory) {
            val history = entry.getPasswordHistory()
            val lastHistoryItem = history.firstOrNull()
            if (lastHistoryItem != null && lastHistoryItem.encryptedOldPassword != null) {
                try {
                    val oldPassword = CryptoUtils.decrypt(lastHistoryItem.encryptedOldPassword)
                    if (!isAtLeast60PercentUnique(oldPassword, newPassword)) {
                        return ValidationResult(
                            false,
                            "Новый пароль слишком похож на предыдущий. Отличие должно быть не менее 60%."
                        )
                    }
                } catch (e: Exception) {
                    // Если не можем расшифровать — пропускаем проверку
                }
            }
        }
        
        return ValidationResult(true)
    }
}
