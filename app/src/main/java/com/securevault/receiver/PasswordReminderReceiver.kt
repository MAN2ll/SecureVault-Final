package com.securevault.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.securevault.R // Убедись, что R импортирован из твоего пакета, либо используй android.R.drawable.ic_lock_idle_lock
import com.securevault.utils.ReminderManager

class PasswordReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReminderManager.ACTION_REMINDER) {
            createChannel(context)
            val service = intent.getStringExtra("service") ?: "Сервис"
            
            val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Напоминание о пароле")
                .setContentText("Пора сменить пароль для \"$service\"!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(service.hashCode(), notification)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ReminderManager.CHANNEL_ID,
                "Напоминания о паролях",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
