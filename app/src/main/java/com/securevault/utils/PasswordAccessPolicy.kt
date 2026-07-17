package com.securevault.utils

import com.securevault.data.Entry
import com.securevault.data.Profile

enum class AccessMode(val value: String) {
    INHERIT("INHERIT"),
    NO_CONFIRMATION("NO_CONFIRMATION"),
    PIN_REQUIRED("PIN_REQUIRED"),
    BIOMETRIC_OR_PIN("BIOMETRIC_OR_PIN")
}

// ✅ ИСПРАВЛЕНО: Добавлен PinRequired
sealed class AccessResult {
    object Granted : AccessResult()
    object PinRequired : AccessResult()
    object BiometricOrPin : AccessResult()
    object PinNotSet : AccessResult()
}

object PasswordAccessPolicy {
    fun resolve(entry: Entry, profile: Profile): AccessResult {
        val entryMode = AccessMode.values().find { it.value == entry.passwordAccessMode } ?: AccessMode.INHERIT
        val hasProfilePin = !profile.passwordHash.isNullOrBlank()

        // Если запись "Как в профиле" и у профиля нет PIN, доступ разрешён сразу
        if (entryMode == AccessMode.INHERIT && !hasProfilePin) {
            return AccessResult.Granted
        }

        val mode = if (entryMode == AccessMode.INHERIT) {
            AccessMode.values().find { it.value == profile.passwordAccessMode } ?: AccessMode.PIN_REQUIRED
        } else {
            entryMode
        }

        // Если режим требует PIN, но PIN не задан
        if (!hasProfilePin) {
            return when (mode) {
                AccessMode.NO_CONFIRMATION -> AccessResult.Granted
                AccessMode.PIN_REQUIRED, AccessMode.BIOMETRIC_OR_PIN -> AccessResult.PinNotSet
                else -> AccessResult.Granted
            }
        }

        // ✅ ИСПРАВЛЕНО: Строгое разделение режимов
        return when (mode) {
            AccessMode.NO_CONFIRMATION -> AccessResult.Granted
            AccessMode.PIN_REQUIRED -> AccessResult.PinRequired
            AccessMode.BIOMETRIC_OR_PIN -> AccessResult.BiometricOrPin
            else -> AccessResult.PinRequired
        }
    }

    //  Политика доступа для входа в сам профиль
    fun resolveProfileAccess(profile: Profile): AccessResult {
        val hasPin = !profile.passwordHash.isNullOrBlank()
        
        if (!hasPin) {
            return AccessResult.Granted
        }

        val mode = AccessMode.values().find { it.value == profile.profileAccessMode } ?: AccessMode.PIN_REQUIRED

        return when (mode) {
            AccessMode.NO_CONFIRMATION -> AccessResult.Granted
            AccessMode.PIN_REQUIRED -> AccessResult.PinRequired
            AccessMode.BIOMETRIC_OR_PIN -> AccessResult.BiometricOrPin
            else -> AccessResult.PinRequired
        }
    }
}
