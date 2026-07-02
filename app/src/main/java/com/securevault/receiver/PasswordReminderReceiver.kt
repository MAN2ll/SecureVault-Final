package com.securevault.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.securevault.data.VaultRepository
import com.securevault.utils.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PasswordReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: VaultRepository

    override fun onReceive(context: Context, intent: Intent?) {
        CoroutineScope(Dispatchers.IO).launch {
            // ✅ Используем getAllEntriesSync() из VaultRepository
            val entries = repository.getAllEntriesSync()

            for (entry in entries) {
                if (entry.rotationEnabled && entry.nextRotationDate != null) {
                    val daysLeft = ((entry.nextRotationDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()

                    when {
                        daysLeft < 0 -> {
                            NotificationHelper.showCriticalNotification(context, entry.service)
                        }
                        daysLeft <= 7 -> {
                            NotificationHelper.showWarningNotification(context, entry.service, daysLeft)
                        }
                    }
                }
            }
        }
    }
}
