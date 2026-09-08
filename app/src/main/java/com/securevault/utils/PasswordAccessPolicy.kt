package com.securevault.utils

import com.securevault.data.Entry
import com.securevault.data.Profile

object PasswordAccessPolicy {
    
    sealed class Result {
        object Granted : Result()
        object PinRequired : Result()
        object BiometricOrPin : Result()
        object PinNotSet : Result()
    }

    fun resolve(entry: Entry, profile: Profile): Result {
        val mode = when (entry.passwordAccessMode) {
            AccessMode.INHERIT.value -> profile.passwordAccessMode
            else -> entry.passwordAccessMode
        }

        return when (mode) {
            AccessMode.NO_CONFIRMATION.value -> Result.Granted
            AccessMode.PIN_REQUIRED.value -> {
                if (profile.passwordHash.isNullOrBlank() || profile.passwordSalt.isNullOrBlank()) {
                    Result.PinNotSet
                } else {
                    Result.PinRequired
                }
            }
            AccessMode.BIOMETRIC_OR_PIN.value -> {
                if (profile.passwordHash.isNullOrBlank() || profile.passwordSalt.isNullOrBlank()) {
                    Result.PinNotSet
                } else {
                    Result.BiometricOrPin
                }
            }
            else -> Result.Granted // Fallback
        }
    }
}
