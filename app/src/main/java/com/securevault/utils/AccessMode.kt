package com.securevault.utils

import com.securevault.data.Entry
import com.securevault.data.Profile

enum class AccessMode(val value: String) {
    INHERIT("inherit"),
    NO_CONFIRMATION("no_confirmation"),
    PIN_REQUIRED("pin_required"),
    BIOMETRIC_OR_PIN("biometric_or_pin")
}

//  ЕДИНЫЙ sealed class для всех экранов
sealed class AccessResult {
    object Granted : AccessResult()
    object PinRequired : AccessResult()
    object BiometricOrPin : AccessResult()
    object PinNotSet : AccessResult()
}

// Для проверки доступа к КОНКРЕТНОЙ ЗАПИСИ (учитывает наследование от профиля)
fun resolveAccess(entry: Entry, profile: Profile): AccessResult {
    val mode = when (entry.passwordAccessMode) {
        AccessMode.INHERIT.value -> profile.passwordAccessMode
        else -> entry.passwordAccessMode
    }

    return when (mode) {
        AccessMode.NO_CONFIRMATION.value -> AccessResult.Granted
        AccessMode.PIN_REQUIRED.value -> {
            if (profile.passwordHash.isNullOrBlank() || profile.passwordSalt.isNullOrBlank()) {
                AccessResult.PinNotSet
            } else {
                AccessResult.PinRequired
            }
        }
        AccessMode.BIOMETRIC_OR_PIN.value -> {
            if (profile.passwordHash.isNullOrBlank() || profile.passwordSalt.isNullOrBlank()) {
                AccessResult.PinNotSet
            } else {
                AccessResult.BiometricOrPin
            }
        }
        else -> AccessResult.Granted
    }
}

//  Для проверки доступа к САМОМУ ПРОФИЛЮ (используется в ProfileListScreen, QrScannerScreen и т.д.)
fun resolveProfileAccess(profile: Profile): AccessResult {
    return when (profile.profileAccessMode) {
        AccessMode.BIOMETRIC_OR_PIN.value -> {
            if (profile.passwordHash.isNullOrBlank() || profile.passwordSalt.isNullOrBlank()) AccessResult.PinNotSet else AccessResult.BiometricOrPin
        }
        AccessMode.PIN_REQUIRED.value -> {
            if (profile.passwordHash.isNullOrBlank() || profile.passwordSalt.isNullOrBlank()) AccessResult.PinNotSet else AccessResult.PinRequired
        }
        else -> AccessResult.Granted
    }
}
