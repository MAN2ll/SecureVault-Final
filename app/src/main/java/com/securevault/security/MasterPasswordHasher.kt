package com.securevault.security

import android.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import java.security.SecureRandom

/**
 * Хеширование мастер-пароля через PBKDF2 с HMAC-SHA256
 * Работает на любом Android 6.0+ без нативных библиотек
 */
@Singleton
class MasterPasswordHasher @Inject constructor() {
    
    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 100_000  // Рекомендуется для мобильных устройств
        private const val KEY_LENGTH = 256       // 256 бит = 32 байта
        private const val SALT_LENGTH = 16       // 128 бит соли
        
        // Формат хеша: "pbkdf2_sha256:iterations:salt_base64:hash_base64"
        private const val HASH_PREFIX = "pbkdf2_sha256"
    }
    
    private val random = SecureRandom()
    
    /**
     * Генерирует криптографически безопасную соль
     */
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        return salt
    }
    
    /**
     * Хеширует пароль с использованием новой соли
     * @param password Мастер-пароль пользователя (в виде CharArray для безопасности)
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
     * @param password Введённый пароль (CharArray)
     * @param storedHash Сохранённая строка хеша из SharedPreferences
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
            
            // Безопасное побайтовое сравнение (защита от timing attack)
            secureCompare(expectedHash, actualHash)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Выполняет хеширование через PBKDF2
     * @param password Пароль в виде CharArray
     * @param salt Соль (ByteArray)
     * @param iterations Количество итераций
     * @param keyLength Длина ключа в битах
     * @return Хеш в виде байтового массива
     */
    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLength)
        val skf = SecretKeyFactory.getInstance(ALGORITHM)
        return skf.generateSecret(spec).encoded
    }
    
    /**
     * Безопасное побайтовое сравнение массивов
     * Защищает от атак по времени (timing attacks)
     * @param a Первый массив
     * @param b Второй массив
     * @return true если массивы идентичны
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
     * Очищает массив пароля из памяти (защита от дампа памяти)
     * Перезаписывает данные нулями перед освобождением
     * @param charArray Массив символов для очистки
     */
    fun clearCharArray(charArray: CharArray) {
        if (charArray.isNotEmpty()) {
            for (i in charArray.indices) {
                charArray[i] = '\u0000'
            }
        }
    }
    
    /**
     * Проверяет, соответствует ли строка формату хеша PBKDF2
     * @param hash Строка для проверки
     * @return true если формат валиден
     */
    fun isValidHashFormat(hash: String): Boolean {
        return hash.startsWith("$HASH_PREFIX:") && hash.split(":").size == 4
    }
    
    /**
     * Проверка на необходимость самоуничтожения (для совместимости с интерфейсом)
     * @param failedAttempts Количество неудачных попыток
     * @param maxAttempts Максимально допустимое количество (по умолчанию 10)
     * @return true если превышен лимит
     */
    fun shouldSelfDestruct(failedAttempts: Int, maxAttempts: Int = 10): Boolean {
        return failedAttempts >= maxAttempts
    }
}
