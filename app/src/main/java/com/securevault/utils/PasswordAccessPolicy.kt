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

object PasswordAccessPolicy {
    fun resolve(entry: Entry, profile: Profile): AccessMode {
        val entryMode = AccessMode.values().find { it.value == entry.passwordAccessMode } ?: AccessMode.INHERIT
        
        return if (entryMode == AccessMode.INHERIT) {
            AccessMode.values().find { it.value == profile.passwordAccessMode } ?: AccessMode.PIN_REQUIRED
        } else {
            entryMode
        }
    }
}
