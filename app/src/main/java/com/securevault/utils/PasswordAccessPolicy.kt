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

//  Sealed class для безопасной обработки отсутствия PIN
sealed class AccessResult {
    object Granted : AccessResult()
    object PinRequired : AccessResult()
    object BiometricOrPin : AccessResult()
    object PinNotSet : AccessResult()
}

object PasswordAccessPolicy {
    fun resolve(entry: Entry, profile: Profile): AccessResult {
        val entryMode = AccessMode.values().find { it.value == entry.passwordAccessMode } ?: AccessMode.INHERIT
        val mode = if (entryMode == AccessMode.INHERIT) {
            AccessMode.values().find { it.value == profile.passwordAccessMode } ?: AccessMode.PIN_REQUIRED
        } else {
            entryMode
        }

        //  Профиль без PIN имеет пустую строку в passwordHash
        val hasProfilePin = !profile.passwordHash.isNullOrBlank()

        return when (mode) {
            AccessMode.NO_CONFIRMATION -> AccessResult.Granted
            AccessMode.PIN_REQUIRED, AccessMode.PIN_ALWAYS -> {
                if (!hasProfilePin) AccessResult.PinNotSet else AccessResult.PinRequired
            }
            AccessMode.BIOMETRIC_OR_PIN -> {
                //  UI попробует биометрию, а при неудаче проверит hasProfilePin и покажет PinNotSet
                AccessResult.BiometricOrPin 
            }
            else -> if (!hasProfilePin) AccessResult.PinNotSet else AccessResult.PinRequired
            }
        }
    }
}
