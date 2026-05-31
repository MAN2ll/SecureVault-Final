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
    }
    
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    
    fun hash(password: CharArray): String {
        return try {
            // Исправлено: убран лишний параметр HASH_LENGTH
            argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, password)
        } finally {
            clearCharArray(password)
        }
    }
    
    fun hash(password: String): String {
        return try {
            argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, password, StandardCharsets.UTF_8)
        } finally {
            // Очистка не нужна для String, но оставляем для консистентности
        }
    }
    
    fun verify(password: CharArray, storedHash: String): Boolean {
        return try {
            argon2.verify(storedHash, password)
        } finally {
            clearCharArray(password)
        }
    }
    
    fun verify(password: String, storedHash: String): Boolean {
        return argon2.verify(storedHash, password, StandardCharsets.UTF_8)
    }
    
    fun clearCharArray(charArray: CharArray) {
        if (charArray.isNotEmpty()) {
            for (i in charArray.indices) {
                charArray[i] = '\u0000'
            }
        }
    }
    
    fun isValidHashFormat(hash: String): Boolean {
        return hash.startsWith("\$argon2id\$") && hash.count { it == '$' } >= 4
    }
    
    fun shouldSelfDestruct(failedAttempts: Int, maxAttempts: Int = 10): Boolean {
        return failedAttempts >= maxAttempts
    }
}
