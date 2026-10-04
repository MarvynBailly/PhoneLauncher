package com.phonelauncher

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

private const val TRACKER_PREFS = "launcher_tracker"
private const val KEY_LAST_PAUSE = "last_pause_ms"

const val PHONE_USAGE_TIMER_NAME = "Phone usage"

fun markLauncherPaused(context: Context, now: Long = System.currentTimeMillis()) {
    context.getSharedPreferences(TRACKER_PREFS, Context.MODE_PRIVATE)
        .edit().putLong(KEY_LAST_PAUSE, now).apply()
}

private fun readLastPause(context: Context): Long =
    context.getSharedPreferences(TRACKER_PREFS, Context.MODE_PRIVATE)
        .getLong(KEY_LAST_PAUSE, 0L)

private fun clearLastPause(context: Context) {
    context.getSharedPreferences(TRACKER_PREFS, Context.MODE_PRIVATE)
        .edit().remove(KEY_LAST_PAUSE).apply()
}

private data class FgSegment(val pkg: String, val startMs: Long, val endMs: Long)

/**
 * Back-fills a "Phone usage" parent timer + per-app sub-timers for the window since
 * the last launcher onPause, and removes time spent in other apps from any running
 * task timer (auto-pause / auto-resume). Screen-off time is not removed.
 */
fun syncPhoneUsage(
    context: Context,
    settings: LauncherSettings,
    timers: List<TimerEntry>,
    now: Long = System.currentTimeMillis(),
): List<TimerEntry> {
    val lastPause = readLastPause(context)
    clearLastPause(context)
    if (lastPause <= 0 || now - lastPause < 2_000) return timers

    // Without usage access there is no way to tell screen-off time (which should
    // count) from time in other apps (which should not), so keep all of it.
    if (!hasUsageStatsPermission(context)) return timers

    val self = context.packageName
    val segments = collectForegroundSegments(context, lastPause, now).filter { it.pkg != self }

    // Cut only the time other apps were in the foreground out of running task
    // timers. Runs even when phone-usage tracking is off; that setting only gates
    // the Phone usage timers below.
    val away = segments.map { TimeSegment(it.startMs, it.endMs) }
    val phoneUsage = timers.phoneUsageIds()
    val truncated = timers.map { t ->
        if (t.id !in phoneUsage) t.excludeIntervals(away, now) else t
    }

    if (!settings.trackPhoneUsage) return truncated
    if (segments.isEmpty()) return truncated

    val pm = context.packageManager
    fun labelFor(pkg: String) = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    val updated = truncated.toMutableList()

    val parentIdx = updated.indexOfFirst { it.name == PHONE_USAGE_TIMER_NAME && it.parentId == null }
    val parent = if (parentIdx >= 0) updated[parentIdx]
    else TimerEntry(name = PHONE_USAGE_TIMER_NAME, isRunning = false, startedAt = now, segments = emptyList())

    val parentAdditions = segments.map { TimeSegment(it.startMs, it.endMs) }
    val newParent = parent.copy(
        segments = parent.segments + parentAdditions,
        isRunning = false,
        startedAt = now,
    )
    if (parentIdx >= 0) updated[parentIdx] = newParent else updated.add(newParent)

    if (settings.phoneUsageBreakdown) {
        segments.groupBy { it.pkg }.forEach { (pkg, segs) ->
            val subName = labelFor(pkg)
            val subIdx = updated.indexOfFirst { it.parentId == newParent.id && it.name == subName }
            val newSegs = segs.map { TimeSegment(it.startMs, it.endMs) }
            if (subIdx >= 0) {
                val cur = updated[subIdx]
                updated[subIdx] = cur.copy(segments = cur.segments + newSegs, isRunning = false, startedAt = now)
            } else {
                updated.add(TimerEntry(
                    name = subName,
                    parentId = newParent.id,
                    isRunning = false,
                    startedAt = now,
                    segments = newSegs,
                ))
            }
        }
    }

    return updated
}

private fun collectForegroundSegments(context: Context, start: Long, end: Long): List<FgSegment> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val events = usm.queryEvents(start, end)
    val event = UsageEvents.Event()
    val openAt = mutableMapOf<String, Long>()
    val out = mutableListOf<FgSegment>()
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        when (event.eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> openAt[event.packageName] = event.timeStamp
            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                val s = openAt.remove(event.packageName) ?: continue
                if (event.timeStamp > s) out.add(FgSegment(event.packageName, s, event.timeStamp))
            }
        }
    }
    for ((pkg, s) in openAt) if (end > s) out.add(FgSegment(pkg, s, end))
    return out
}
