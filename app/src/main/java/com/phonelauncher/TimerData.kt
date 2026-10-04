package com.phonelauncher

import android.app.NotificationManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TimeSegment(val startMs: Long, val endMs: Long)

data class TimerEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val taskId: String? = null,
    val segments: List<TimeSegment> = emptyList(),
    val startedAt: Long = System.currentTimeMillis(),
    val isRunning: Boolean = true,
    val dndEnabled: Boolean = false,
    val collapsed: Boolean = false,
)

data class QuickAction(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val count: Int = 0,
)

// Timer operations

fun TimerEntry.elapsed(now: Long = System.currentTimeMillis()): Long {
    val segTotal = segments.sumOf { it.endMs - it.startMs }
    return segTotal + if (isRunning) (now - startedAt) else 0
}

fun TimerEntry.pause(now: Long = System.currentTimeMillis()): TimerEntry {
    if (!isRunning) return this
    return copy(
        segments = segments + TimeSegment(startedAt, now),
        isRunning = false
    )
}

fun TimerEntry.resume(): TimerEntry {
    if (isRunning) return this
    return copy(startedAt = System.currentTimeMillis(), isRunning = true)
}

/**
 * Removes the [away] intervals (time spent in other apps) from a running timer's
 * current session. Time outside them, including screen-off time, is kept: the
 * session is split into closed segments around each interval and keeps running
 * from the end of the last one.
 */
fun TimerEntry.excludeIntervals(away: List<TimeSegment>, now: Long): TimerEntry {
    if (!isRunning) return this
    var cursor = startedAt
    val kept = mutableListOf<TimeSegment>()
    for (a in away.sortedBy { it.startMs }) {
        if (a.endMs <= cursor) continue
        if (a.startMs > now) break
        if (a.startMs > cursor) kept += TimeSegment(cursor, a.startMs)
        cursor = maxOf(cursor, a.endMs)
    }
    return copy(segments = segments + kept, startedAt = minOf(cursor, now))
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * Applies an edited start or end time to a session. The time picker keeps the
 * original date, so an end before the start means the session crossed midnight
 * (and a start after the end means it began the previous day).
 */
fun TimeSegment.withEditedTime(editingStart: Boolean, newMs: Long): TimeSegment =
    if (editingStart) copy(startMs = if (newMs > endMs) newMs - DAY_MS else newMs)
    else copy(endMs = if (newMs < startMs) newMs + DAY_MS else newMs)

/** Ids of the auto-managed "Phone usage" parent timer and its per-app sub-timers. */
fun List<TimerEntry>.phoneUsageIds(): Set<String> {
    val parentId = find { it.name == PHONE_USAGE_TIMER_NAME && it.parentId == null }?.id
        ?: return emptySet()
    return filter { it.id == parentId || it.parentId == parentId }.map { it.id }.toSet()
}

/**
 * Wall-clock time covered by at least one user timer. Overlapping timers (a parent
 * and its sub-timer, or two timers running together) are counted once, and the
 * auto-managed phone-usage timers are left out.
 */
fun trackedTime(timers: List<TimerEntry>, now: Long): Long {
    val skip = timers.phoneUsageIds()
    val intervals = timers.filter { it.id !in skip }.flatMap { t ->
        t.segments + if (t.isRunning) listOf(TimeSegment(t.startedAt, now)) else emptyList()
    }.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
    var total = 0L
    var curStart = -1L
    var curEnd = -1L
    for (i in intervals) {
        if (i.startMs > curEnd) {
            total += curEnd - curStart
            curStart = i.startMs
            curEnd = i.endMs
        } else {
            curEnd = maxOf(curEnd, i.endMs)
        }
    }
    return total + (curEnd - curStart)
}

/**
 * Timers that survive a day reset: running user timers restart at [dayStartMs]
 * (their earlier time belongs to the day that just closed), and any parent of a
 * running sub-timer is kept, paused and empty, so the sub-timer is not orphaned.
 */
fun carryRunningTimers(timers: List<TimerEntry>, dayStartMs: Long): List<TimerEntry> {
    val skip = timers.phoneUsageIds()
    val running = timers.filter { it.isRunning && it.id !in skip }
    val parentIds = running.mapNotNull { it.parentId }.toSet()
    return timers.filter { (it.isRunning && it.id !in skip) || it.id in parentIds }.map { t ->
        if (t.isRunning) t.copy(segments = emptyList(), startedAt = maxOf(t.startedAt, dayStartMs))
        else t.copy(segments = emptyList())
    }
}

fun formatElapsed(ms: Long): String {
    val total = maxOf(0, ms / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "${m}:${s.toString().padStart(2, '0')}"
}

// DND

fun setDnd(context: Context, enabled: Boolean) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.isNotificationPolicyAccessGranted) {
        nm.setInterruptionFilter(
            if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }
}

fun hasDndPermission(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return nm.isNotificationPolicyAccessGranted
}

// Persistence

private const val TP = "launcher_timers"

fun loadTimers(context: Context): List<TimerEntry> {
    val json = context.getSharedPreferences(TP, Context.MODE_PRIVATE)
        .getString("timers", null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { timerFromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }
}

fun saveTimers(context: Context, timers: List<TimerEntry>) {
    val arr = JSONArray().apply { timers.forEach { put(timerToJson(it)) } }
    context.getSharedPreferences(TP, Context.MODE_PRIVATE)
        .edit().putString("timers", arr.toString()).apply()
}

fun loadTimerHistory(context: Context): List<String> {
    val json = context.getSharedPreferences(TP, Context.MODE_PRIVATE)
        .getString("history", null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) { emptyList() }
}

fun saveTimerHistory(context: Context, history: List<String>) {
    val arr = JSONArray().apply { history.take(20).forEach { put(it) } }
    context.getSharedPreferences(TP, Context.MODE_PRIVATE)
        .edit().putString("history", arr.toString()).apply()
}

fun addToHistory(context: Context, name: String) {
    val history = loadTimerHistory(context).toMutableList()
    history.remove(name)
    history.add(0, name)
    saveTimerHistory(context, history.take(20))
}

fun loadQuickActions(context: Context, date: String): List<QuickAction> {
    val prefs = context.getSharedPreferences(TP, Context.MODE_PRIVATE)
    if (prefs.getString("qa_date", "") != date) return emptyList()
    val json = prefs.getString("quick_actions", null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map {
            val j = arr.getJSONObject(it)
            QuickAction(j.getString("id"), j.getString("name"), j.optInt("count", 0))
        }
    } catch (_: Exception) { emptyList() }
}

fun saveQuickActions(context: Context, actions: List<QuickAction>, date: String) {
    val arr = JSONArray().apply {
        actions.forEach {
            put(JSONObject().apply { put("id", it.id); put("name", it.name); put("count", it.count) })
        }
    }
    context.getSharedPreferences(TP, Context.MODE_PRIVATE).edit()
        .putString("quick_actions", arr.toString())
        .putString("qa_date", date).apply()
}

// JSON helpers

private fun segToJson(s: TimeSegment) = JSONObject().apply {
    put("s", s.startMs); put("e", s.endMs)
}
private fun segFromJson(j: JSONObject) = TimeSegment(j.getLong("s"), j.getLong("e"))

private fun timerToJson(t: TimerEntry) = JSONObject().apply {
    put("id", t.id); put("name", t.name)
    put("parentId", t.parentId ?: ""); put("taskId", t.taskId ?: "")
    put("segs", JSONArray().apply { t.segments.forEach { put(segToJson(it)) } })
    put("startedAt", t.startedAt)
    put("running", t.isRunning); put("dnd", t.dndEnabled)
    put("collapsed", t.collapsed)
}

private fun timerFromJson(j: JSONObject) = TimerEntry(
    id = j.getString("id"), name = j.getString("name"),
    parentId = j.optString("parentId", "").ifEmpty { null },
    taskId = j.optString("taskId", "").ifEmpty { null },
    segments = j.optJSONArray("segs")?.let { a ->
        (0 until a.length()).map { segFromJson(a.getJSONObject(it)) }
    } ?: emptyList(),
    startedAt = j.optLong("startedAt", System.currentTimeMillis()),
    isRunning = j.optBoolean("running", false),
    dndEnabled = j.optBoolean("dnd", false),
    collapsed = j.optBoolean("collapsed", false),
)
