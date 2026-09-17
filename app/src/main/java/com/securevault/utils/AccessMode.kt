package com.securevault.utils

import com.securevault.data.Entry
import com.securevault.data.Profile

enum class AccessMode(val value: String) {
    INHERIT("inherit"),
    NO_CONFIRMATION("no_confirmation"),
    PIN_REQUIRED("pin_required"),
    BIOMETRIC_OR_PIN("biometric_or_pin");

    companion object {
        /**
         * Безопасный парсинг режима доступа без учёта регистра.
         * Если значение null или неизвестно, возвращается INHERIT как наиболее безопасный вариант 
         * (который затем заставит проверить настройки профиля).
         */
        fun fromString(mode: String?): AccessMode {
            return when (mode?.lowercase()) {
                "inherit" -> INHERIT
                "no_confirmation" -> NO_CONFIRMATION
                "pin_required" -> PIN_REQUIRED
                "biometric_or_pin" -> BIOMETRIC_OR_PIN
                // Неизвестные или пустые значения безопасно fallback-ятся на INHERIT
                else -> INHERIT
            }
        }
    }
}

sealed class AccessResult {
    object Granted : AccessResult()
    object PinRequired : AccessResult()
    object BiometricOrPin : AccessResult()
    object PinNotSet : AccessResult()
}

/**
 * Определяет право доступа к КОНКРЕТНОЙ ЗАПИСИ.
 * Никогда не возвращает Granted для неизвестных режимов.
 */
fun resolveAccess(entry: Entry, profile: Profile): AccessResult {
    // Безопасно парсим режим записи (учитывает регистр)
    val entryMode = AccessMode.fromString(entry.passwordAccessMode)
    
    // Если запись наследует режим, берем режим профиля (также с безопасным парсингом)
    val effectiveMode = when (entryMode) {
        AccessMode.INHERIT -> AccessMode.fromString(profile.passwordAccessMode)
        else -> entryMode
    }

    return when (effectiveMode) {
        AccessMode.NO_CONFIRMATION -> AccessResult.Granted
        
        AccessMode.PIN_REQUIRED -> {
            if (profile.passwordHash.isNullOrBlank() || profile.passwordSalt.isNullOrBlank()) {
                AccessResult.PinNotSet
            } else {
                AccessResult.PinRequired
            }
        }
        
        AccessMode.BIOMETRIC_OR_PIN -> {
            if (profile.passwordHash.isNullOrBlank() || profile.passwordSalt.isNullOrBlank()) {
                AccessResult.PinNotSet
            } else {
                AccessResult.BiometricOrPin
            }
        }
        
        //  БЕЗОПАСНЫЙ FALLBACK: Если режим всё ещё INHERIT (например, профиль тоже был INHERIT) 
        // или попал сюда как неизвестный, мы ТРЕБУЕМ PIN. Никакого автоматического Granted.
        AccessMode.INHERIT -> AccessResult.PinRequired
    }
}

/**
 * Определяет право доступа к САМОМУ ПРОФИЛЮ.
 * Никогда не возвращает Granted для неизвестных режимов.
 */
fun resolveProfileAccess(profile: Profile): AccessResult {
    val profileMode = AccessMode.fromString(profile.profileAccessMode)

    return when (profileMode) {
        AccessMode.NO_CONFIRMATION -> AccessResult.Granted
        
        AccessMode.PIN_REQUIRED -> {
            if (profile.passwordHash.isNullOrBlank() || profile.passwordSalt.isNullOrBlank()) {
                AccessResult.PinNotSet
            } else {
                AccessResult.PinRequired
            }
        }
        
        AccessMode.BIOMETRIC_OR_PIN -> {
            if (profile.passwordHash.isNullOrBlank() || profile.passwordSalt.isNullOrBlank()) {
                AccessResult.PinNotSet
            } else {
                AccessResult.BiometricOrPin
            }
        }
        
        //  БЕЗОПАСНЫЙ FALLBACK: Профиль не может "наследовать". 
        // Если режим неизвестен, null или случайно установлен в INHERIT, мы ТРЕБУЕМ PIN.
        AccessMode.INHERIT -> AccessResult.PinRequired
    }
}
