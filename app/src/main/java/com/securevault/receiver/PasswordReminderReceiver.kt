package com.securevault.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.securevault.R
import com.securevault.utils.ReminderManager

class PasswordReminderReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReminderManager.ACTION_REMINDER) {
            val entryId = intent.getStringExtra(ReminderManager.EXTRA_ENTRY_ID) ?: return
            val entryTitle = intent.getStringExtra(ReminderManager.EXTRA_ENTRY_TITLE) ?: "пароль"
            val daysLeft = intent.getIntExtra(ReminderManager.EXTRA_DAYS_LEFT, 7)
            
            showNotification(context, entryTitle, daysLeft)
        }
    }
    
    private fun showNotification(context: Context, entryTitle: String, daysLeft: Int) {
        val notification = NotificationCompat.Builder(context, ReminderManager.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock) // Замените на свою иконку: R.drawable.ic_notification
            .setContentTitle("⏰ Пора сменить пароль")
            .setContentText("Пароль для \"$entryTitle\" истекает через $daysLeft д. Обновите его в приложении.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            // Уникальный ID уведомления = хеш ID записи + константа
            notify(5000 + entryTitle.hashCode(), notification)
        }
    }
}
