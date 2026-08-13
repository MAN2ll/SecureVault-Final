package com.securevault

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.securevault.ui.SecureVaultNavHost
import com.securevault.ui.theme.SecureVaultTheme
import com.securevault.utils.RotationNotificationWorker
import com.securevault.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private var lastBackgroundTime = 0L

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) scheduleNotifications()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SecureVaultTheme { SecureVaultNavHost() } }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            scheduleNotifications()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        startInactivityMonitor()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) {
            authViewModel.updateInteractionTime()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_background_at", System.currentTimeMillis()).apply()
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val timeoutMinutes = prefs.getInt("auto_lock_timeout_minutes", 5)
        val lastBg = prefs.getLong("last_background_at", 0L)
        
        if (lastBg > 0) {
            val elapsed = System.currentTimeMillis() - lastBg
            val timeoutMs = if (timeoutMinutes == 0) 0L else timeoutMinutes * 60L * 1000L
            
            if (timeoutMs == 0L || elapsed >= timeoutMs) {
                authViewModel.lock()
            }
        }
    }

    private fun startInactivityMonitor() {
        lifecycleScope.launch {
            while (true) {
                delay(5000)
                val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val timeoutMinutes = prefs.getInt("auto_lock_timeout_minutes", 5)
                val timeoutMs = if (timeoutMinutes == 0) 0L else timeoutMinutes * 60L * 1000L
                
                if (timeoutMs > 0) {
                    val lastInteraction = prefs.getLong("last_interaction_time", System.currentTimeMillis())
                    if (System.currentTimeMillis() - lastInteraction >= timeoutMs) {
                        authViewModel.lock()
                    }
                }
            }
        }
    }

    private fun scheduleNotifications() {
        val workRequest = PeriodicWorkRequestBuilder<RotationNotificationWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "securevault_rotation_notifications",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
