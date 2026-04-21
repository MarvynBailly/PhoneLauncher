package com.phonelauncher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar

data class UsageStat(
    val packageName: String,
    val label: String,
    val totalMs: Long,
)

data class PhoneUsageStats(
    val unlockCount: Int,
    val totalScreenMs: Long,
    val apps: List<UsageStat>,
)

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

fun getDayStartMs(resetHour: Int): Long {
    val cal = Calendar.getInstance()
    if (cal.get(Calendar.HOUR_OF_DAY) < resetHour) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    cal.set(Calendar.HOUR_OF_DAY, resetHour)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun loadUsageStats(context: Context, dayResetHour: Int): PhoneUsageStats? {
    if (!hasUsageStatsPermission(context)) return null

    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val start = getDayStartMs(dayResetHour)
    val end = System.currentTimeMillis()
    val ownPackage = context.packageName

    val appTimes = mutableMapOf<String, Long>()
    val appResumeAt = mutableMapOf<String, Long>()
    var unlockCount = 0

    val events = usm.queryEvents(start, end)
    val event = UsageEvents.Event()
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        when (event.eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                appResumeAt[event.packageName] = event.timeStamp
            }
            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                val startedAt = appResumeAt.remove(event.packageName) ?: continue
                appTimes[event.packageName] =
                    (appTimes[event.packageName] ?: 0L) + (event.timeStamp - startedAt)
            }
            UsageEvents.Event.KEYGUARD_HIDDEN -> {
                unlockCount++
            }
        }
    }
    // Close any still-open sessions at "now"
    for ((pkg, startedAt) in appResumeAt) {
        appTimes[pkg] = (appTimes[pkg] ?: 0L) + (end - startedAt)
    }

    val pm = context.packageManager
    val apps = appTimes
        .filter { it.key != ownPackage && it.value > 0 }
        .map { (pkg, ms) ->
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg
            }
            UsageStat(pkg, label, ms)
        }
        .sortedByDescending { it.totalMs }

    return PhoneUsageStats(
        unlockCount = unlockCount,
        totalScreenMs = apps.sumOf { it.totalMs },
        apps = apps,
    )
}
