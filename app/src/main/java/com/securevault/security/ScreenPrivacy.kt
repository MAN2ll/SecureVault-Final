package com.securevault.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.view.MotionEvent
import android.view.Window
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.IdentityHashMap

object ScreenPrivacyPolicy {
    data class Decision(val blockCapture: Boolean, val blockRecents: Boolean)
    fun decide(sensitive: Boolean, allowScreenshots: Boolean, foreground: Boolean, latched: Boolean): Decision {
        val protected = sensitive || latched
        return Decision(protected && (!allowScreenshots || !foreground || latched), protected)
    }
    fun safeReturnRoute(route: String?, profileId: Int?): String {
        val id = profileId?.takeIf { it > 0 }
        return when {
            route == "settings" || route == "change_password" -> "settings"
            route == "profiles" || route == null -> "profiles"
            route == "export" -> "export"
            id == null -> "profiles"
            route.startsWith("profile_settings/") -> "profile_settings/$id"
            route.startsWith("rotation_journal/") -> "rotation_journal/$id"
            route.startsWith("rotation/") -> "rotation/$id"
            route.startsWith("audit/") -> "audit/$id"
            route.startsWith("export/") -> "export/$id"
            else -> "vault/$id"
        }
    }
}
interface ScreenPrivacyHost { val screenPrivacy: ScreenPrivacyController }

class ScreenPrivacyController(private val activity: Activity, private val onInteraction: () -> Unit) : AutoCloseable {
    companion object { const val ALLOW_SCREENSHOTS = "allow_password_screenshots" }
    private val prefs = activity.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val owners = IdentityHashMap<Any, Window>()
    private val callbacks = IdentityHashMap<Window, Pair<Window.Callback, Window.Callback>>()
    private val _safePageEpoch = MutableStateFlow(0L)
    val safePageEpoch = _safePageEpoch.asStateFlow()
    private var foreground = false
    private var latched = false
    private var closed = false
    val hasSensitiveContent: Boolean get() = owners.isNotEmpty()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ALLOW_SCREENSHOTS) applyPolicy()
    }
    init { prefs.registerOnSharedPreferenceChangeListener(preferenceListener) }

    fun register(window: Window): () -> Unit {
        val token = Any(); owners[token] = window
        if (!callbacks.containsKey(window)) {
            val original = window.callback
            val wrapped = object : Window.Callback by original {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) onInteraction()
                    return original.dispatchTouchEvent(event)
                }
            }
            callbacks[window] = original to wrapped; window.callback = wrapped
        }
        applyPolicy()
        return {
            owners.remove(token)
            if (owners.values.none { it === window }) {
                callbacks.remove(window)?.let { (original, wrapped) -> if (window.callback === wrapped) window.callback = original }
                if (foreground && !latched && window !== activity.window) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            applyPolicy()
        }
    }
    fun onPause() {
        val reset = hasSensitiveContent && !latched
        latched = latched || hasSensitiveContent
        foreground = false
        applyPolicy()
        if (reset) {
            // Старый Dialog скрывается сразу; новая композиция закроет его и покажет безопасную страницу.
            owners.values.distinct().filter { it !== activity.window }.forEach { it.decorView.visibility = View.INVISIBLE }
            _safePageEpoch.value += 1
        }
    }
    fun onResume() {
        foreground = true; latched = false
        applyPolicy()
    }
    private fun applyPolicy() {
        if (closed) return
        val decision = ScreenPrivacyPolicy.decide(hasSensitiveContent,
            prefs.getBoolean(ALLOW_SCREENSHOTS, false), foreground, latched)
        (listOf(activity.window) + owners.values).distinct().forEach { window ->
            if (decision.blockCapture) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(!decision.blockRecents)
    }
    override fun close() {
        closed = true; prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        callbacks.forEach { (window, pair) -> if (window.callback === pair.second) window.callback = pair.first }
        callbacks.clear(); owners.clear()
    }
}
internal fun Context.privacyHost(): ScreenPrivacyHost? {
    var context = this
    while (context is ContextWrapper) {
        if (context is ScreenPrivacyHost) return context
        val base = context.baseContext; if (base === context) break; context = base
    }
    return context as? ScreenPrivacyHost
}
@Composable
fun SensitiveContent() {
    val host = LocalContext.current.privacyHost() ?: return
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        ?: (host as? Activity)?.window ?: return
    DisposableEffect(host, window) {
        val unregister = host.screenPrivacy.register(window)
        onDispose { unregister() }
    }
}
