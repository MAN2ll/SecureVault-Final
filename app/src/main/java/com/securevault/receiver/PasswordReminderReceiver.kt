package com.securevault.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.securevault.utils.ReminderManager

class PasswordReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReminderManager.ACTION_REMINDER) {
            val entryTitle = intent.getStringExtra(ReminderManager.EXTRA_ENTRY_TITLE) ?: "пароль"
            val daysLeft = intent.getIntExtra(ReminderManager.EXTRA_DAYS_LEFT, 7)
            
            val notification = NotificationCompat.Builder(context, ReminderManager.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle(" Пора сменить пароль")
                .setContentText("Пароль для \"$entryTitle\" истекает через $daysLeft д.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context)
                .notify(5000 + entryTitle.hashCode(), notification)
        }
    }
}
