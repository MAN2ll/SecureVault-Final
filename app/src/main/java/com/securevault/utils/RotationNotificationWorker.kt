package com.securevault.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.securevault.R
import com.securevault.data.VaultRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

class RotationNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_rotation_enabled", false)) return Result.success()

        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, VaultRepositoryEntryPoint::class.java)
        val repository = entryPoint.vaultRepository()
        
        val now = System.currentTimeMillis()
        val entries = repository.allEntries.first()
        val expiredEntries = entries.filter { it.rotationEnabled && (it.nextRotationDate ?: Long.MAX_VALUE) <= now }

        if (expiredEntries.isNotEmpty()) {
            showNotification(expiredEntries.size, expiredEntries.first().service)
        }
        return Result.success()
    }

    private fun showNotification(count: Int, firstService: String) {
        createChannel()
        val text = if (count == 1) "Пора обновить пароль: $firstService" else "Есть пароли, которым требуется ротация ($count)"
        
        val notification = NotificationCompat.Builder(applicationContext, "securevault_rotation_channel")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("SecureVault")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(1001, notification)
        } catch (e: SecurityException) { /* Permission denied */ }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "securevault_rotation_channel",
                "Ротация паролей",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Уведомления о паролях, которым требуется ротация" }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

@dagger.hilt.EntryPoint
@InstallIn(dagger.hilt.components.SingletonComponent::class)
interface VaultRepositoryEntryPoint {
    val vaultRepository: VaultRepository
}
