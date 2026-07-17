package com.securevault.utils

import com.securevault.data.Entry
import com.securevault.data.Profile

enum class AccessMode(val value: String) {
    INHERIT("INHERIT"),
    NO_CONFIRMATION("NO_CONFIRMATION"),
    PIN_REQUIRED("PIN_REQUIRED"),
    BIOMETRIC_OR_PIN("BIOMETRIC_OR_PIN")
}

sealed class AccessResult {
    object Granted : AccessResult()
    object BiometricOrPin : AccessResult()
    object PinNotSet : AccessResult()
}

object PasswordAccessPolicy {
    fun resolve(entry: Entry, profile: Profile): AccessResult {
        val entryMode = AccessMode.values().find { it.value == entry.passwordAccessMode } ?: AccessMode.INHERIT
        val hasProfilePin = !profile.passwordHash.isNullOrBlank()

        if (entryMode == AccessMode.INHERIT && !hasProfilePin) {
            return AccessResult.Granted
        }

        val mode = if (entryMode == AccessMode.INHERIT) {
            AccessMode.values().find { it.value == profile.passwordAccessMode } ?: AccessMode.PIN_REQUIRED
        } else {
            entryMode
        }

        if (!hasProfilePin) {
            return when (mode) {
                AccessMode.NO_CONFIRMATION -> AccessResult.Granted
                AccessMode.PIN_REQUIRED, AccessMode.BIOMETRIC_OR_PIN -> AccessResult.PinNotSet
                else -> AccessResult.Granted
            }
        }

        return when (mode) {
            AccessMode.NO_CONFIRMATION -> AccessResult.Granted
            AccessMode.PIN_REQUIRED, AccessMode.BIOMETRIC_OR_PIN -> AccessResult.BiometricOrPin
            else -> AccessResult.BiometricOrPin
        }
    }
}
