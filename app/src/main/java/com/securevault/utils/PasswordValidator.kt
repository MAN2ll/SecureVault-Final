package com.securevault.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.securevault.data.Entry
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import java.security.MessageDigest

object PasswordValidator {

    private const val HMAC_KEY_ALIAS = "securevault_fingerprint_hmac"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    fun hasDuplicateCharacters(password: String): Boolean {
        val seen = mutableSetOf<Char>()
        for (char in password.lowercase()) {
            if (char.isWhitespace()) continue
            if (!seen.add(char)) return true
        }
        return false
    }

    fun validateUniqueCharacters(password: String): ValidationResult {
        return if (hasDuplicateCharacters(password)) {
            ValidationResult(false, "Пароль содержит повторяющиеся символы. Создайте другой пароль.")
        } else {
            ValidationResult(true)
        }
    }

    fun buildPasswordFingerprint(password: String, context: Context): String {
        return try {
            val key = getOrCreateHmacKey(context)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(key)
            val hashBytes = mac.doFinal(password.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            buildLegacyFingerprint(password)
        }
    }

    fun buildLegacyFingerprint(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateHmacKey(context: Context): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)

        val existingKey = keyStore.getEntry(HMAC_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) return existingKey.secretKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            ANDROID_KEY_STORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                HMAC_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).build()
        )
        return keyGenerator.generateKey()
    }

    fun wasPasswordUsedForEntry(entry: Entry, password: String, context: Context): Boolean {
        val newFingerprint = buildPasswordFingerprint(password, context)
        val legacyFingerprint = buildLegacyFingerprint(password)

        if (entry.passwordFingerprint == newFingerprint || entry.passwordFingerprint == legacyFingerprint) {
            return true
        }

        val history = entry.getPasswordHistory()
        return history.any {
            it.passwordFingerprint == newFingerprint || it.passwordFingerprint == legacyFingerprint
        }
    }

    fun validatePasswordNotReusedForEntry(entry: Entry, newPassword: String, context: Context): ValidationResult {
        return if (wasPasswordUsedForEntry(entry, newPassword, context)) {
            ValidationResult(false, "Этот пароль уже использовался для данного сервиса. Выберите другой пароль.")
        } else {
            ValidationResult(true)
        }
    }

    fun calculatePasswordDifferencePercent(oldPassword: String, newPassword: String): Int {
        val old = oldPassword.lowercase().replace(" ", "")
        val new = newPassword.lowercase().replace(" ", "")

        if (old.isEmpty() || new.isEmpty()) return 100
        if (old == new) return 0

        val maxLength = maxOf(old.length, new.length)
        val minLength = minOf(old.length, new.length)

        var positionMatches = 0
        for (i in 0 until minLength) {
            if (old[i] == new[i]) positionMatches++
        }
        val positionSimilarity = positionMatches.toDouble() / maxLength

        val oldSet = old.toSet()
        val newSet = new.toSet()
        val intersection = oldSet.intersect(newSet).size.toDouble()
        val union = oldSet.union(newSet).size.toDouble()
        val jaccardSimilarity = if (union == 0.0) 0.0 else intersection / union

        val oldBigrams = getBigrams(old)
        val newBigrams = getBigrams(new)
        val bigramIntersection = oldBigrams.intersect(newBigrams).size.toDouble()
        val bigramUnion = oldBigrams.union(newBigrams).size.toDouble()
        val bigramSimilarity = if (bigramUnion == 0.0) 0.0 else bigramIntersection / bigramUnion

        val lengthSimilarity = 1.0 - (kotlin.math.abs(old.length - new.length).toDouble() / maxLength)

        val similarity = positionSimilarity * 0.4 +
                jaccardSimilarity * 0.3 +
                bigramSimilarity * 0.2 +
                lengthSimilarity * 0.1

        val differencePercent = ((1.0 - similarity) * 100).toInt().coerceIn(0, 100)

        return differencePercent
    }

    private fun getBigrams(text: String): Set<String> {
        if (text.length < 2) return setOf(text)
        return (0 until text.length - 1).map { text.substring(it, it + 2) }.toSet()
    }

    fun isAtLeast60PercentUnique(oldPassword: String, newPassword: String): Boolean {
        return calculatePasswordDifferencePercent(oldPassword, newPassword) >= 60
    }

    fun validateNewPasswordForEntry(
        entry: Entry,
        newPassword: String,
        context: Context,
        checkHistory: Boolean = true
    ): ValidationResult {
        val uniqueCheck = validateUniqueCharacters(newPassword)
        if (!uniqueCheck.isValid) return uniqueCheck

        if (checkHistory) {
            val reuseCheck = validatePasswordNotReusedForEntry(entry, newPassword, context)
            if (!reuseCheck.isValid) return reuseCheck
        }

        if (checkHistory) {
            val history = entry.getPasswordHistory()
            val lastHistoryItem = history.firstOrNull()
            if (lastHistoryItem?.encryptedOldPassword != null) {
                try {
                    val oldPassword = CryptoUtils.decrypt(lastHistoryItem.encryptedOldPassword)
                    if (!isAtLeast60PercentUnique(oldPassword, newPassword)) {
                        return ValidationResult(
                            false,
                            "Новый пароль слишком похож на предыдущий. Отличие должно быть не менее 60%."
                        )
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        return ValidationResult(true)
    }
}
