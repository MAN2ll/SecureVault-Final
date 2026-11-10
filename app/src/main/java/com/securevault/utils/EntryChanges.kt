package com.securevault.utils

import android.content.Context
import com.securevault.data.Entry

internal class PasswordPolicyException(message: String) : IllegalArgumentException(message)

internal fun isManualPasswordType(type: String) = type == "manual" || type == "manual_ampg"

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
                mnemonicJson: String? = null, relatedService: String? = null,
                acceptManualPolicyWarning: Boolean = false): Entry {
        val old = CryptoUtils.decrypt(entry.encryptedPassword)
        if (password == old) return entry
        require(password.isNotBlank()) { "Пароль не может быть пустым" }
        if (!acceptManualPolicyWarning || !isManualPasswordType(type)) {
            val validation = PasswordValidator.validateNewPasswordForEntry(entry, password, context, generationType = type)
            if (!validation.isValid) throw PasswordPolicyException(validation.errorMessage.orEmpty())
        }
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
