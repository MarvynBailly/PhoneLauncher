package com.phonelauncher

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class TimerPromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        scheduleTimerPrompt(context)

        val settings = loadSettings(context)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < settings.dayResetHour || hour >= ACTIVE_END_HOUR) return

        if (loadTimers(context).any { it.isRunning }) return

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK }
            ?: return
        val pi = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PROMPT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("No timer running")
            .setContentText("Tap to start tracking your time")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(PROMPT_NOTIF_ID, notification)
        }
    }

    companion object {
        const val PROMPT_CHANNEL = "timer_prompt"
        const val PROMPT_NOTIF_ID = 43
        const val PROMPT_INTERVAL_MS = 60 * 60 * 1000L // 1 hour
        const val ACTIVE_END_HOUR = 22 // stop prompting at 10pm
    }
}

fun createTimerPromptChannel(context: Context) {
    val channel = NotificationChannel(
        TimerPromptReceiver.PROMPT_CHANNEL,
        "Timer Prompts",
        NotificationManager.IMPORTANCE_DEFAULT
    )
    channel.description = "Prompts you to start a timer when none is running"
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .createNotificationChannel(channel)
}

fun scheduleTimerPrompt(context: Context) {
    val intent = Intent(context, TimerPromptReceiver::class.java)
    val pi = PendingIntent.getBroadcast(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val triggerAt = System.currentTimeMillis() + TimerPromptReceiver.PROMPT_INTERVAL_MS
    am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
}
