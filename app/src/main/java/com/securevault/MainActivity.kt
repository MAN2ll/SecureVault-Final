package com.securevault

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
    private var lastInteractionTime = System.currentTimeMillis()
    private var lastBackgroundTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SecureVaultTheme { SecureVaultNavHost() } }
        scheduleNotifications()
        startInactivityMonitor()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) {
            lastInteractionTime = System.currentTimeMillis()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        lastBackgroundTime = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val timeoutMs = prefs.getInt("auto_lock_timeout_minutes", 5) * 60 * 1000L
        if (lastBackgroundTime > 0) {
            if (timeoutMs == 0L || (System.currentTimeMillis() - lastBackgroundTime) >= timeoutMs) {
                authViewModel.lock()
            }
        }
    }

    private fun startInactivityMonitor() {
        lifecycleScope.launch {
            while (true) {
                delay(5000)
                val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val timeoutMs = prefs.getInt("auto_lock_timeout_minutes", 5) * 60 * 1000L
                val isUnlocked = authViewModel.authState.value is AuthViewModel.AuthState.Unlocked
                if (timeoutMs > 0 && isUnlocked && (System.currentTimeMillis() - lastInteractionTime) >= timeoutMs) {
                    authViewModel.lock()
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
