package com.securevault.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.securevault.R // ✅ Добавь этот импорт если есть свои ресурсы

class PasswordReminderReceiver : BroadcastReceiver() {
    
    // ✅ Константы дублируем здесь, чтобы не зависеть от ReminderManager
    companion object {
        const val ACTION_REMINDER = "com.securevault.action.PASSWORD_REMINDER"
        const val EXTRA_ENTRY_TITLE = "extra_entry_title"
        const val EXTRA_DAYS_LEFT = "extra_days_left"
        const val NOTIFICATION_CHANNEL_ID = "password_reminders"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REMINDER) {
            val entryTitle = intent.getStringExtra(EXTRA_ENTRY_TITLE) ?: "пароль"
            val daysLeft = intent.getIntExtra(EXTRA_DAYS_LEFT, 7)
            
            showNotification(context, entryTitle, daysLeft)
        }
    }
    
    private fun showNotification(context: Context, entryTitle: String, daysLeft: Int) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock) // Или R.drawable.ic_notification
            .setContentTitle("⏰ Пора сменить пароль")
            .setContentText("Пароль для \"$entryTitle\" истекает через $daysLeft д.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(5000 + entryTitle.hashCode(), notification)
    }
}
