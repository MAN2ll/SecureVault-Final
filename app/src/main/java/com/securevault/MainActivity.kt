package com.securevault

import android.Manifest
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.securevault.security.ScreenPrivacyController
import com.securevault.security.ScreenPrivacyHost
import com.securevault.ui.SecureVaultNavHost
import com.securevault.ui.theme.SecureVaultTheme
import com.securevault.utils.RotationNotificationWorker
import com.securevault.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : FragmentActivity(), ScreenPrivacyHost {
    private val authViewModel: AuthViewModel by viewModels()
    override lateinit var screenPrivacy: ScreenPrivacyController
        private set
    private var screenWasOff = false
    private var receiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                screenWasOff = true
                screenPrivacy.onPause()
                authViewModel.lock()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenPrivacy = ScreenPrivacyController(this) { authViewModel.updateInteractionTime() }
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        setContent { SecureVaultTheme { SecureVaultNavHost(authViewModel = authViewModel) } }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) scheduleNotifications()
        lifecycleScope.launch {
            while (true) {
                delay(1000)
                if (authViewModel.authState.value !is AuthViewModel.AuthState.Unlocked) continue
                val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val minutes = prefs.getInt("auto_lock_timeout_minutes", 5)
                val last = prefs.getLong("last_interaction_time", System.currentTimeMillis())
                if (minutes > 0 && System.currentTimeMillis() - last >= minutes * 60_000L) authViewModel.lock()
            }
        }
    }
    private fun deviceLocked(): Boolean {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return !power.isInteractive || keyguard.isDeviceLocked
    }
    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val minutes = prefs.getInt("auto_lock_timeout_minutes", 5)
        val last = prefs.getLong("last_background_at", 0L)
        if (screenWasOff || deviceLocked() || (last > 0 &&
                (minutes == 0 || System.currentTimeMillis() - last >= minutes * 60_000L))) authViewModel.lock()
    }
    override fun onResume() {
        if (screenWasOff || deviceLocked()) authViewModel.lock()
        screenWasOff = false
        super.onResume()
        screenPrivacy.onResume()
    }
    override fun onPause() {
        screenPrivacy.onPause()
        if (deviceLocked()) { screenWasOff = true; authViewModel.lock() }
        super.onPause()
    }
    override fun onStop() {
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        if (screenPrivacy.hasSensitiveContent || prefs.getInt("auto_lock_timeout_minutes", 5) == 0 || deviceLocked()) authViewModel.lock()
        prefs.edit().putLong("last_background_at", System.currentTimeMillis()).apply()
        super.onStop()
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE)
            authViewModel.updateInteractionTime()
        return super.dispatchTouchEvent(event)
    }
    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        screenPrivacy.close()
        super.onDestroy()
    }
    private fun scheduleNotifications() {
        val request = PeriodicWorkRequestBuilder<RotationNotificationWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("securevault_rotation_notifications",
            ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
