package com.tapasya.focus

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.content.ContextCompat

class TapasyaAccessibilityService : AccessibilityService() {
    private var lastBlockedPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        SessionManager.completeIfExpired(this)
        restoreTimerIfNeeded()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        SessionManager.completeIfExpired(this)
        val session = SessionManager.loadActiveSession(this)
        if (session == null || System.currentTimeMillis() >= session.endTime) {
            lastBlockedPackage = null
            return
        }

        val foregroundPackage = event.packageName?.toString() ?: return
        if (isIgnoredPackage(foregroundPackage)) {
            lastBlockedPackage = null
            return
        }

        if (foregroundPackage !in session.blockedPackages) {
            lastBlockedPackage = null
            return
        }

        if (lastBlockedPackage == foregroundPackage) return
        lastBlockedPackage = foregroundPackage

        performGlobalAction(GLOBAL_ACTION_HOME)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                getString(R.string.blocked_message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun restoreTimerIfNeeded() {
        val session = SessionManager.loadActiveSession(this) ?: return
        if (System.currentTimeMillis() >= session.endTime) {
            SessionManager.completeIfExpired(this)
            return
        }

        try {
            val serviceIntent = Intent(this, StudyTimerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: IllegalStateException) {
            // The service still blocks selected apps; opening Tapasya restores the timer service.
        } catch (_: SecurityException) {
            // Android may reject a background foreground-service start; do not crash accessibility.
        }
    }

    private fun isIgnoredPackage(packageName: String): Boolean {
        if (packageName == applicationContext.packageName) return true
        if (packageName == "android" || packageName.startsWith("com.android.systemui")) return true
        if (packageName.startsWith("com.android.permissioncontroller") ||
            packageName.startsWith("com.google.android.permissioncontroller")
        ) return true
        return packageName in setOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.emergency",
            "com.google.android.apps.safetyhub",
            "com.android.settings",
            "com.google.android.settings"
        )
    }

    override fun onInterrupt() = Unit
}
