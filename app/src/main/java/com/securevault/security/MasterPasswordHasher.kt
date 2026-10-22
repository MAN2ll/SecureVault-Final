package com.securevault.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object MasterPasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    data class HashResult(
        val hash: String,
        val salt: String,
        val iterations: Int
    )

    private val secureRandom = SecureRandom()

    fun hash(password: String): HashResult {
        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val hash = hashWithSalt(password, salt, ITERATIONS)
        
        //  ИСПОЛЬЗУЕМ android.util.Base64 вместо java.util.Base64
        return HashResult(
            hash = Base64.encodeToString(hash, Base64.NO_WRAP),
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            iterations = ITERATIONS
        )
    }

    fun verify(password: String, storedHash: String, storedSalt: String, iterations: Int = ITERATIONS): Boolean {
        return try {
            val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
            val computedHash = hashWithSalt(password, salt, iterations)
            constantTimeEquals(Base64.encodeToString(computedHash, Base64.NO_WRAP), storedHash)
        } catch (_: Exception) { false }
    }

    //  Constant-time comparison
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    private fun hashWithSalt(password: String, salt: ByteArray, iterations: Int): ByteArray {
        return PasswordKdf.derive(password, salt, iterations)
    }
}
