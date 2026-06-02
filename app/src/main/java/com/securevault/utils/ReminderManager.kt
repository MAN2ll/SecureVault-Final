package com.securevault.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import com.securevault.data.Entry
import com.securevault.receiver.PasswordReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "password_reminders"
        const val ACTION_REMINDER = "com.securevault.action.REMINDER"
    }

    fun scheduleReminder(entry: Entry) {
        if (!entry.rotationEnabled || entry.nextRotationDate == null) return
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PasswordReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra("entry_id", entry.id)
            putExtra("service", entry.service)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getBroadcast(context, entry.id.hashCode(), intent, flags)
        val triggerTime = entry.nextRotationDate!! - (7L * 24 * 60 * 60 * 1000) // За 7 дней

        if (triggerTime > System.currentTimeMillis()) {
            AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}
