package com.securevault.security

import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object PasswordKdf {
    fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations in 1..2_000_000) { "Некорректное число итераций" }
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } catch (_: NoSuchAlgorithmException) {
            deriveCompatible(password, salt, iterations)
        } finally { spec.clearPassword() }
    }
    // Тот же PBKDF2-HMAC-SHA256 для Android 7.0–7.1.
    fun deriveCompatible(password: String, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations in 1..2_000_000)
        val bytes = password.toByteArray(Charsets.UTF_8)
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(if (bytes.isEmpty()) byteArrayOf(0) else bytes, "HmacSHA256"))
            var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
            val output = u.copyOf()
            repeat(iterations - 1) {
                val next = mac.doFinal(u)
                u.fill(0); u = next
                for (i in output.indices) output[i] = (output[i].toInt() xor u[i].toInt()).toByte()
            }
            u.fill(0)
            return output
        } finally { bytes.fill(0) }
    }
}
