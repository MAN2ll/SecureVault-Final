package com.securevault.security

import de.mkammerer.argon2.Argon2Factory
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasterPasswordHasher @Inject constructor() {
    
    companion object {
        private const val MEMORY_KB = 65536
        private const val ITERATIONS = 3
        private const val PARALLELISM = 1
        private const val HASH_LENGTH = 32
    }
    
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    
    fun hash(password: CharArray): String {
        return try {
            // Исправлено: добавлен StandardCharsets.UTF_8
            argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, password, StandardCharsets.UTF_8, HASH_LENGTH)
        } finally {
            clearCharArray(password)
        }
    }
    
    fun verify(password: CharArray, storedHash: String): Boolean {
        return try {
            argon2.verify(storedHash, password, StandardCharsets.UTF_8)
        } finally {
            clearCharArray(password)
        }
    }
    
    fun clearCharArray(charArray: CharArray) {
        if (charArray.isNotEmpty()) {
            for (i in charArray.indices) {
                charArray[i] = '\u0000'
            }
        }
    }
    
    fun isValidHashFormat(hash: String): Boolean {
        // Исправлено: строковый литерал в кавычках
        return hash.startsWith("\$argon2id\$") && hash.count { it == '$' } >= 4
    }
    
    fun shouldSelfDestruct(failedAttempts: Int, maxAttempts: Int = 10): Boolean {
        return failedAttempts >= maxAttempts
    }
}
