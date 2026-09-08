package com.securevault.utils

import com.securevault.data.Profile

enum class AccessMode(val value: String) {
    INHERIT("inherit"),
    NO_CONFIRMATION("no_confirmation"),
    PIN_REQUIRED("pin_required"),
    BIOMETRIC_OR_PIN("biometric_or_pin")
}

// МОСТ СОВМЕСТИМОСТИ: связывает старый код с новой PasswordAccessPolicy
typealias AccessResult = PasswordAccessPolicy.Result

fun resolveProfileAccess(profile: Profile): AccessResult {
    return when (profile.profileAccessMode) {
        AccessMode.BIOMETRIC_OR_PIN.value -> {
            if (profile.passwordHash.isNullOrBlank() || profile.passwordSalt.isNullOrBlank()) {
                AccessResult.PinNotSet
            } else {
                AccessResult.BiometricOrPin
            }
        }
        AccessMode.PIN_REQUIRED.value -> {
            if (profile.passwordHash.isNullOrBlank() || profile.passwordSalt.isNullOrBlank()) {
                AccessResult.PinNotSet
            } else {
                AccessResult.PinRequired
            }
        }
        else -> AccessResult.Granted
    }
}
