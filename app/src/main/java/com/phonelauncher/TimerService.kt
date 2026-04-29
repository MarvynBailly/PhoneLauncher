package com.phonelauncher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            val timers = loadTimers(this@TimerService)
            val running = timers.filter { it.isRunning }
            if (running.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            NotificationManagerCompat.from(this@TimerService)
                .notify(NOTIF_ID, buildNotification(running))
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.deleteNotificationChannel("timers")
        val ch = NotificationChannel("timers_v2", "Timer Tracking", NotificationManager.IMPORTANCE_DEFAULT)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always satisfy the startForegroundService contract by calling
        // startForeground before any branch can stopSelf. Otherwise the system
        // throws ForegroundServiceDidNotStartInTime / RemoteServiceException.
        val running = loadTimers(this).filter { it.isRunning }
        val notif = if (running.isNotEmpty()) buildNotification(running)
        else placeholderNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        when (intent?.action) {
            ACTION_PAUSE -> {
                val id = intent.getStringExtra("timer_id")
                if (id != null) pauseById(id)
                sendBroadcast(Intent(ACTION_SYNC).setPackage(packageName))
                val remaining = loadTimers(this).filter { it.isRunning }
                if (remaining.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                } else {
                    NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification(remaining))
                    handler.removeCallbacks(ticker)
                    handler.postDelayed(ticker, 1000)
                }
            }
            else -> {
                if (running.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                handler.removeCallbacks(ticker)
                handler.postDelayed(ticker, 1000)
            }
        }
        return START_STICKY
    }

    private fun placeholderNotification(): android.app.Notification =
        NotificationCompat.Builder(this, "timers_v2")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Timers")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun pauseById(id: String) {
        val timers = loadTimers(this)
        val target = timers.find { it.id == id } ?: return
        val updated = timers.map { if (it.id == id) it.pause() else it }
        saveTimers(this, updated)
        if (target.dndEnabled && updated.none { it.isRunning && it.dndEnabled }) {
            setDnd(this, false)
        }
    }

    private fun buildNotification(running: List<TimerEntry>): android.app.Notification {
        val now = System.currentTimeMillis()
        val primary = running.first()
        val elapsed = formatElapsed(primary.elapsed(now))
        val title = if (running.size > 1) "${primary.name} (+${running.size - 1})" else primary.name

        val pauseIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_PAUSE
            putExtra("timer_id", primary.id)
        }
        val pausePi = PendingIntent.getService(
            this, primary.id.hashCode(), pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "timers_v2")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(elapsed)
            .setContentIntent(openPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePi)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 42
        const val ACTION_PAUSE = "com.phonelauncher.PAUSE_TIMER"
        const val ACTION_SYNC = "com.phonelauncher.TIMER_SYNC"
    }
}
