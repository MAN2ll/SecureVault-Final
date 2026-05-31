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
        private const val ITERATIONS = 100_000  // Рекомендуется для мобильных устройств
        private const val KEY_LENGTH = 256       // 256 бит = 32 байта
        private const val SALT_LENGTH = 16       // 128 бит соли
        
        // Формат хеша: "pbkdf2:iterations:salt:hash" (все части в Base64)
        private const val HASH_PREFIX = "pbkdf2_sha256"
    }
    
    private val random = SecureRandom()
    
    /**
     * Генерирует соль криптографически безопасным способом
     */
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        return salt
    }
    
    /**
     * Хеширует пароль с использованием новой соли
     * @param password Мастер-пароль пользователя
     * @return Строка в формате: "pbkdf2_sha256:100000:<salt_base64>:<hash_base64>"
     */
    fun hash(password: CharArray): String {
        val salt = generateSalt()
        val hash = pbkdf2(password, salt, ITERATIONS, KEY_LENGTH)
        
        // Формируем строку: префикс:итерации:соль:хеш
        return "$HASH_PREFIX:$ITERATIONS:${Base64.encodeToString(salt, Base64.NO_WRAP)}:${Base64.encodeToString(hash, Base64.NO_WRAP)}"
    }
    
    /**
     * Проверяет пароль против сохранённого хеша
     * @param password Введённый пароль
     * @param storedHash Сохранённая строка хеша
     * @return true если пароль верный
     */
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
            
            // Безопасное сравнение хешей (защита от timing attack)
            secureCompare(expectedHash, actualHash)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Выполняет хеширование через PBKDF2
     */
    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLength)
        val skf = SecretKeyFactory.getInstance(ALGORITHM)
        return skf.generateSecret(spec).encoded
    }
    
    /**
     * Безопасное побайтовое сравнение (защита от атак по времени)
     */
    private fun secureCompare(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].xor(b[i]).toInt())
        }
        return result == 0
    }
    
    /**
     * Очищает массив пароля из памяти (защита от дампа)
     */
    fun clearCharArray(charArray: CharArray) {
        if (charArray.isNotEmpty()) {
            for (i in charArray.indices) {
                charArray[i] = '\u0000'
            }
        }
    }
    
    /**
     * Проверяет формат хеша
     */
    fun isValidHashFormat(hash: String): Boolean {
        return hash.startsWith("$HASH_PREFIX:") && hash.split(":").size == 4
    }
    
    /**
     * Проверка на самоуничтожение (для совместимости с интерфейсом)
     */
    fun shouldSelfDestruct(failedAttempts: Int, maxAttempts: Int = 10): Boolean {
        return failedAttempts >= maxAttempts
    }
}
