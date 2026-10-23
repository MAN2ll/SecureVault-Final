package com.securevault.utils

import android.content.Context
import com.securevault.data.Entry

object EntryChanges {
    fun nextRotationDate(original: Entry?, enabled: Boolean, months: Int,
                         passwordChanged: Boolean, now: Long): Long? = when {
        !enabled -> null
        original == null || !original.rotationEnabled ||
            original.rotationPeriodMonths != months || passwordChanged -> {
            require(months in 1..120) { "Некорректный период ротации" }
            now + months * 30L * 24 * 60 * 60 * 1000
        }
        else -> original.nextRotationDate
    }

    fun replace(entry: Entry, password: String, type: String, context: Context,
                hint: String? = null, mnemonicHint: String? = null,
                mnemonicJson: String? = null, relatedService: String? = null): Entry {
        val old = CryptoUtils.decrypt(entry.encryptedPassword)
        if (password == old) return entry
        require(password.isNotBlank()) { "Пароль не может быть пустым" }
        val validation = PasswordValidator.validateNewPasswordForEntry(entry, password, context, generationType = type)
        require(validation.isValid) { validation.errorMessage.orEmpty() }
        val now = System.currentTimeMillis()
        val oldFingerprint = PasswordValidator.buildPasswordFingerprint(old, context)
        return entry.addToPasswordHistory(old, type, oldFingerprint, relatedService).copy(
            encryptedPassword = CryptoUtils.encrypt(password),
            passwordFingerprint = PasswordValidator.buildPasswordFingerprint(password, context),
            lastChanged = now,
            nextRotationDate = nextRotationDate(entry, entry.rotationEnabled,
                entry.rotationPeriodMonths, true, now),
            generationType = type, textHint = hint,
            mnemonicPhraseHint = mnemonicHint, mnemonicOptionsJson = mnemonicJson
        )
    }
}
