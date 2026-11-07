package com.qasim.nexa.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional Android TV Home-button compatibility service.
 *
 * It requests only the HOME key stream and window-state events.
 * It never requests access to the accessibility node/window-content tree.
 */
class NexaHomeAccessibilityService : AccessibilityService() {

    private var lastForegroundPackage: String? = null
    private var lastLaunchAt: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val eventPackage = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        lastForegroundPackage = eventPackage

        // NEXA is already visible; do not relaunch or steal focus.
        if (eventPackage == packageName) return

        // Some TV firmwares ignore the filtered HOME key. If their selected
        // Home app comes to the foreground, bring NEXA forward as a fallback.
        val resolvedHomePackage = resolveHomePackage() ?: return
        if (eventPackage == resolvedHomePackage && resolvedHomePackage != packageName) {
            launchNexaHome()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false

        // Consume both halves of the key stream so it stays well-formed.
        if (event.action == KeyEvent.ACTION_UP && lastForegroundPackage != packageName) {
            launchNexaHome()
        }
        return true
    }

    private fun resolveHomePackage(): String? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val resolved = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName
    }

    private fun launchNexaHome() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchAt < 900L) return
        lastLaunchAt = now

        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                component = ComponentName(this@NexaHomeAccessibilityService, MainActivity::class.java)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(intent)
        } catch (_: Exception) {
            // Keep the service alive; the stock launcher remains available.
        }
    }

    override fun onInterrupt() = Unit
}
