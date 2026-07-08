package com.securevault.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.securevault.data.VaultRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RotationCheckWorkerEntryPoint {
    fun vaultRepository(): VaultRepository
}

class RotationCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                RotationCheckWorkerEntryPoint::class.java
            )
            val repository = entryPoint.vaultRepository()

            val allEntries = repository.getAllEntriesSync()
            val now = System.currentTimeMillis()
            val sevenDays = 7L * 24 * 60 * 60 * 1000

            val expiredCount = allEntries.count { 
                it.rotationEnabled && it.nextRotationDate != null && it.nextRotationDate <= now 
            }

            val expiringSoonCount = allEntries.count { 
                it.rotationEnabled && it.nextRotationDate != null && 
                it.nextRotationDate > now && it.nextRotationDate <= now + sevenDays 
            }

            if (expiredCount > 0 || expiringSoonCount > 0) {
                NotificationHelper.sendRotationNotification(
                    applicationContext,
                    expiredCount,
                    expiringSoonCount
                )
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
