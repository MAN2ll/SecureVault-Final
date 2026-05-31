package com.securevault.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.securevault.R  // Убедись, что у тебя есть strings.xml с app_name
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    
    companion object {
        private const val CHANNEL_ID = "password_reminders"
        private const val NOTIFICATION_ID_BASE = 1000
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.securevault.REMINDER") {
            val entryId = intent.getStringExtra("entry_id") ?: return
            val entryTitle = intent.getStringExtra("entry_title") ?: "Запись"
            
            showNotification(context, entryId, entryTitle)
        }
    }
    
    private fun showNotification(context: Context, entryId: String, entryTitle: String) {
        //  Создаём уведомление
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(" Пора сменить пароль")
            .setContentText("Пароль для \"$entryTitle\" требует обновления")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            // TODO: Добавить pendingIntent для перехода к записи
            .build()
        
        //  Показываем уведомление
        with(NotificationManagerCompat.from(context)) {
            // Проверка разрешения на уведомления (Android 13+)
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notify(NOTIFICATION_ID_BASE + entryId.hashCode(), notification)
            }
        }
    }
}
