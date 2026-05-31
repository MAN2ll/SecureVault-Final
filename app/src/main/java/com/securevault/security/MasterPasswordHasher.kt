package com.securevault.security

import android.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import java.security.SecureRandom

@Singleton
class MasterPasswordHasher @Inject constructor() {
    
    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
        private const val HASH_PREFIX = "pbkdf2_sha256"
    }
    
    private val random = SecureRandom()
    
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        return salt
    }
    
    fun hash(password: CharArray): String {
        val salt = generateSalt()
        val hash = pbkdf2(password, salt, ITERATIONS, KEY_LENGTH)
        return "$HASH_PREFIX:$ITERATIONS:${Base64.encodeToString(salt, Base64.NO_WRAP)}:${Base64.encodeToString(hash, Base64.NO_WRAP)}"
    }
    
    fun verify(password: CharArray, storedHash: String): Boolean {
        return try {
            val parts = storedHash.split(":")
            if (parts.size != 4 || parts[0] != HASH_PREFIX) {
                return false
            }
            
            val iterations = parts[1].toInt()
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expectedHash = Base64.decode(parts[3], Base64.NO_WRAP)
            
            val actualHash = pbkdf2(password, salt, iterations, expectedHash.size * 8)
            secureCompare(expectedHash, actualHash)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLength)
        val skf = SecretKeyFactory.getInstance(ALGORITHM)
        return skf.generateSecret(spec).encoded
    }
    
    private fun secureCompare(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
    
    fun clearCharArray(charArray: CharArray) {
        if (charArray.isNotEmpty()) {
            for (i in charArray.indices) {
                charArray[i] = '\u0000'
            }
        }
    }
    
    fun isValidHashFormat(hash: String): Boolean {
        return hash.startsWith("$HASH_PREFIX:") && hash.split(":").size == 4
    }
    
    fun shouldSelfDestruct(failedAttempts: Int, maxAttempts: Int = 10): Boolean {
        return failedAttempts >= maxAttempts
    }
}
