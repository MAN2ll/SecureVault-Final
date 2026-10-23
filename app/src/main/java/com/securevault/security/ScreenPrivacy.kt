package com.securevault.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.IdentityHashMap

object ScreenPrivacyPolicy {
    data class Decision(val blockCapture: Boolean, val blockRecents: Boolean, val conceal: Boolean)
    fun decide(sensitive: Boolean, allowScreenshots: Boolean, foreground: Boolean, latched: Boolean): Decision {
        val protected = sensitive || latched
        return Decision(protected && (!allowScreenshots || !foreground || latched),
            protected, protected && (!foreground || latched))
    }
}
interface ScreenPrivacyHost { val screenPrivacy: ScreenPrivacyController }

class ScreenPrivacyController(private val activity: Activity, private val onInteraction: () -> Unit) : AutoCloseable {
    companion object { const val ALLOW_SCREENSHOTS = "allow_password_screenshots" }
    private val prefs = activity.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val owners = IdentityHashMap<Any, Window>()
    private val curtains = IdentityHashMap<Window, ColorDrawable>()
    private val callbacks = IdentityHashMap<Window, Pair<Window.Callback, Window.Callback>>()
    private val _foreground = MutableStateFlow(false)
    val foreground = _foreground.asStateFlow()
    private var latched = false
    private var closed = false
    val hasSensitiveContent: Boolean get() = owners.isNotEmpty()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ALLOW_SCREENSHOTS) applyPolicy()
    }
    init { prefs.registerOnSharedPreferenceChangeListener(preferenceListener) }

    fun register(window: Window): () -> Unit {
        val token = Any()
        owners[token] = window
        if (!callbacks.containsKey(window)) {
            val original = window.callback
            val wrapped = object : Window.Callback by original {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) onInteraction()
                    return original.dispatchTouchEvent(event)
                }
            }
            callbacks[window] = original to wrapped
            window.callback = wrapped
        }
        applyPolicy()
        return {
            owners.remove(token)
            if (owners.values.none { it === window }) {
                callbacks.remove(window)?.let { (original, wrapped) -> if (window.callback === wrapped) window.callback = original }
                if (_foreground.value && !latched && window !== activity.window) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    curtains.remove(window)?.let { window.decorView.overlay.remove(it) }
                }
            }
            applyPolicy()
        }
    }
    fun onPause() {
        latched = latched || hasSensitiveContent
        _foreground.value = false
        applyPolicy()
    }
    fun onResume() {
        _foreground.value = true
        applyPolicy()
    }
    // Снимаем занавес после кадров новой композиции, когда старый пароль уже закрыт.
    fun revealForeground() {
        if (!_foreground.value || closed) return
        latched = false
        curtains.forEach { (window, drawable) -> window.decorView.overlay.remove(drawable) }
        curtains.clear()
        applyPolicy()
    }
    private fun applyPolicy() {
        if (closed) return
        val decision = ScreenPrivacyPolicy.decide(hasSensitiveContent,
            prefs.getBoolean(ALLOW_SCREENSHOTS, false), _foreground.value, latched)
        val windows = (listOf(activity.window) + owners.values + curtains.keys).distinct()
        windows.forEach { window ->
            if (decision.blockCapture) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (decision.conceal && !curtains.containsKey(window)) {
                val decor = window.decorView
                val drawable = ColorDrawable(Color.BLACK)
                drawable.setBounds(0, 0, maxOf(decor.width, activity.resources.displayMetrics.widthPixels),
                    maxOf(decor.height, activity.resources.displayMetrics.heightPixels))
                decor.overlay.add(drawable)
                curtains[window] = drawable
            }
        }
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(!decision.blockRecents)
    }
    override fun close() {
        closed = true
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        callbacks.forEach { (window, pair) -> if (window.callback === pair.second) window.callback = pair.first }
        curtains.forEach { (window, drawable) -> window.decorView.overlay.remove(drawable) }
        curtains.clear(); callbacks.clear(); owners.clear()
    }
}
private fun Context.privacyHost(): ScreenPrivacyHost? {
    var context = this
    while (context is ContextWrapper) {
        if (context is ScreenPrivacyHost) return context
        val base = context.baseContext
        if (base === context) break
        context = base
    }
    return context as? ScreenPrivacyHost
}
@Composable
fun SensitiveContent() {
    val host = LocalContext.current.privacyHost() ?: return
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
        ?: (host as? Activity)?.window ?: return
    DisposableEffect(host, window) {
        val unregister = host.screenPrivacy.register(window)
        onDispose { unregister() }
    }
}
@Composable
fun PrivacyTransitionGuard(authState: Any) {
    val host = LocalContext.current.privacyHost() ?: return
    val foreground by host.screenPrivacy.foreground.collectAsState()
    LaunchedEffect(host, foreground, authState) {
        if (foreground) {
            withFrameNanos { }
            withFrameNanos { }
            host.screenPrivacy.revealForeground()
        }
    }
}
