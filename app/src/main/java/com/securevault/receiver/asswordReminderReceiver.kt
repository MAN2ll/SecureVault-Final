package com.securevault.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.securevault.R
import com.securevault.utils.ReminderManager

class PasswordReminderReceiver : BroadcastReceiver() {
    
    companion object {
        private const val CHANNEL_ID = "password_reminders"
        private const val NOTIFICATION_ID = 2001
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReminderManager.ACTION_REMINDER) {
            val entryId = intent.getStringExtra(ReminderManager.EXTRA_ENTRY_ID) ?: return
            val entryTitle = intent.getStringExtra(ReminderManager.EXTRA_ENTRY_TITLE) ?: "пароль"
            
            showNotification(context, entryTitle)
        }
    }
    
    private fun showNotification(context: Context, entryTitle: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("⏰ Пора сменить пароль")
            .setContentText("Пароль для \"$entryTitle\" скоро истечёт. Обновите его в приложении.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID + entryTitle.hashCode(), notification)
        }
    }
}
