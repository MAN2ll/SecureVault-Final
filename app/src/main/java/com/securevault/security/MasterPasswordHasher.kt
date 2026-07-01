package com.securevault.security

import java.security.SecureRandom
import java.util.Base64
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
        
        return HashResult(
            hash = Base64.getEncoder().encodeToString(hash),
            salt = Base64.getEncoder().encodeToString(salt),
            iterations = ITERATIONS
        )
    }

    fun verify(password: String, storedHash: String, storedSalt: String, iterations: Int = ITERATIONS): Boolean {
        val salt = Base64.getDecoder().decode(storedSalt)
        val computedHash = hashWithSalt(password, salt, iterations)
        val computedHashBase64 = Base64.getEncoder().encodeToString(computedHash)
        return computedHashBase64 == storedHash
    }

    private fun hashWithSalt(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }
}
