package com.securevault.utils

import com.securevault.data.Entry
import com.securevault.data.Profile

enum class AccessMode(val value: String) {
    INHERIT("inherit"), NO_CONFIRMATION("no_confirmation"), PIN_REQUIRED("pin_required"), BIOMETRIC_OR_PIN("biometric_or_pin");
    companion object {
        fun fromString(mode: String?): AccessMode = values().find { it.value.equals(mode?.trim(), true) } ?: INHERIT
        fun isKnown(mode: String?) = values().any { it.value.equals(mode?.trim(), true) }
    }
}
sealed class AccessResult {
    object Granted : AccessResult()
    object PinRequired : AccessResult()
    object BiometricOrPin : AccessResult()
    object PinNotSet : AccessResult()
}
private fun resolve(mode: AccessMode, profile: Profile): AccessResult {
    if (mode == AccessMode.NO_CONFIRMATION) return AccessResult.Granted
    if (profile.passwordHash.isBlank() || profile.passwordSalt.isBlank()) return AccessResult.PinNotSet
    return if (mode == AccessMode.BIOMETRIC_OR_PIN) AccessResult.BiometricOrPin else AccessResult.PinRequired
}
fun resolveAccess(entry: Entry, profile: Profile): AccessResult {
    require(entry.profileId == profile.id) { "Профиль записи не совпадает" }
    if (!AccessMode.isKnown(entry.passwordAccessMode)) return resolve(AccessMode.PIN_REQUIRED, profile)
    val entryMode = AccessMode.fromString(entry.passwordAccessMode)
    return resolve(if (entryMode == AccessMode.INHERIT) AccessMode.fromString(profile.passwordAccessMode) else entryMode, profile)
}
fun resolveProfileAccess(profile: Profile): AccessResult = resolve(AccessMode.fromString(profile.profileAccessMode), profile)

/** Revealing or copying a saved secret always requires the PIN of its own folder. */
fun resolvePasswordRevealAccess(entry: Entry, profile: Profile): AccessResult {
    require(entry.profileId == profile.id) { "Профиль записи не совпадает" }
    return resolve(AccessMode.PIN_REQUIRED, profile)
}
