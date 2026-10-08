package com.tapasya.focus

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat

class StudyTimerForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var activeEndTime: Long = 0L

    private val timerTick = object : Runnable {
        override fun run() {
            val session = SessionManager.loadActiveSession(this@StudyTimerForegroundService)
            if (session == null) {
                stopServiceNow()
                return
            }

            activeEndTime = session.endTime
            val remaining = activeEndTime - System.currentTimeMillis()
            if (remaining <= 0L) {
                SessionManager.completeIfExpired(this@StudyTimerForegroundService)
                stopServiceNow()
                return
            }

            NotificationHelper.updateOngoing(this@StudyTimerForegroundService, remaining)
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = SessionManager.loadActiveSession(this)
        if (session == null) {
            SessionManager.completeIfExpired(this)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val now = System.currentTimeMillis()
        if (now >= session.endTime) {
            SessionManager.completeIfExpired(this, now)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        activeEndTime = session.endTime
        val notification = NotificationHelper.buildOngoing(this, activeEndTime - now)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID,
            notification,
            serviceType
        )
        handler.removeCallbacks(timerTick)
        handler.post(timerTick)
        return START_STICKY
    }

    private fun stopServiceNow() {
        handler.removeCallbacks(timerTick)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The timer is deliberately independent of the activity task.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerTick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
