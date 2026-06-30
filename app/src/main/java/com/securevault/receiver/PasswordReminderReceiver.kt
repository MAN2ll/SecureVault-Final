package com.securevault.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.securevault.data.VaultDatabase
import com.securevault.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PasswordReminderReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent?) {
        CoroutineScope(Dispatchers.IO).launch {
            val database = VaultDatabase.getDatabase(context)
            val entries = database.entryDao().getAllEntriesSync()
            
            entries.forEach { entry ->
                if (entry.rotationEnabled && entry.nextRotationDate != null) {
                    val daysLeft = ((entry.nextRotationDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                    
                    when {
                        daysLeft < 0 -> {
                            // Пароль истёк
                            NotificationHelper.showCriticalNotification(context, entry.service)
                        }
                        daysLeft <= 7 -> {
                            // Истекает в течение 7 дней
                            NotificationHelper.showWarningNotification(context, entry.service, daysLeft)
                        }
                    }
                }
            }
        }
    }
}
