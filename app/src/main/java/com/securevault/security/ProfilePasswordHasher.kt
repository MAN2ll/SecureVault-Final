package com.securevault.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object ProfilePasswordHasher {

    private const val HASH_ALGORITHM = "SHA-256"
    private const val SALT_LENGTH = 16

    private val secureRandom = SecureRandom()

    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        digest.update(saltBytes)
        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    fun verify(password: String, storedHash: String, storedSalt: String): Boolean {
        val computedHash = hash(password, storedSalt)
        return computedHash == storedHash
    }
}
