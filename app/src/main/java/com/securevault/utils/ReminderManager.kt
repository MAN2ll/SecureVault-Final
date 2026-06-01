package com.securevault.utils

import com.securevault.data.Entry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderManager @Inject constructor() {
    fun getUpcoming(entries: List<Entry>, days: Int = 7): List<Entry> {
        return entries.filter { e ->
            e.rotationEnabled && e.nextRotationDate != null &&
            (e.getDaysUntilRotation() ?: Int.MAX_VALUE) in 0..days
        }
    }
}
