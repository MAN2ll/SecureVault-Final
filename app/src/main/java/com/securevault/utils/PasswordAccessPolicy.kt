package com.securevault.utils

import com.securevault.data.Entry
import com.securevault.data.Profile

enum class AccessMode(val value: String) {
    INHERIT("INHERIT"),
    NO_CONFIRMATION("NO_CONFIRMATION"),
    PIN_REQUIRED("PIN_REQUIRED"),
    BIOMETRIC_OR_PIN("BIOMETRIC_OR_PIN"),
    PIN_ALWAYS("PIN_ALWAYS")
}

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

        //  Если режим "Как в профиле" и у профиля нет PIN, доступ разрешён сразу
        if (entryMode == AccessMode.INHERIT && !hasProfilePin) {
            return AccessResult.Granted
        }

        val mode = if (entryMode == AccessMode.INHERIT) {
            AccessMode.values().find { it.value == profile.passwordAccessMode } ?: AccessMode.PIN_REQUIRED
        } else {
            entryMode
        }

        //  Если режим явно требует PIN, но у профиля его нет -> показываем PinNotSet
        if (!hasProfilePin) {
            return when (mode) {
                AccessMode.NO_CONFIRMATION -> AccessResult.Granted
                AccessMode.PIN_REQUIRED, AccessMode.PIN_ALWAYS, AccessMode.BIOMETRIC_OR_PIN -> AccessResult.PinNotSet
                else -> AccessResult.Granted
            }
        }

        return when (mode) {
            AccessMode.NO_CONFIRMATION -> AccessResult.Granted
            AccessMode.PIN_REQUIRED, AccessMode.PIN_ALWAYS -> AccessResult.PinRequired
            AccessMode.BIOMETRIC_OR_PIN -> AccessResult.BiometricOrPin
            else -> AccessResult.PinRequired
        }
    }
}
