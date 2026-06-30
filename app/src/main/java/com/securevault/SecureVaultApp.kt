package com.securevault

import android.app.Application
import com.securevault.utils.AutoLockManager
import com.securevault.utils.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SecureVaultApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // ✅ Инициализация AutoLockManager
        AutoLockManager.init(applicationContext)
        
        // ✅ Создание канала уведомлений
        NotificationHelper.createNotificationChannel(applicationContext)
    }
}
