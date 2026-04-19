package com.phonelauncher

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

// Data Models

enum class Recurrence { NONE, DAILY, WEEKDAYS, WEEKLY }

data class TaskTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val deadlineHour: Int? = null,
    val deadlineMinute: Int? = null,
    val reminderMinutes: Int? = null,
    val rewardAppPackage: String? = null,
    val rewardMinutes: Int = 15,
    val recurrence: Recurrence = Recurrence.DAILY,
)

data class DayTask(
    val id: String = UUID.randomUUID().toString(),
    val templateId: String? = null,
    val title: String = "",
    val deadlineHour: Int? = null,
    val deadlineMinute: Int? = null,
    val reminderMinutes: Int? = null,
    val rewardAppPackage: String? = null,
    val rewardMinutes: Int = 15,
    val isCompleted: Boolean = false,
)

data class RewardSession(
    val appPackage: String,
    val expiresAt: Long,
)

data class DayState(
    val date: String = "",
    val tasks: List<DayTask> = emptyList(),
    val planningDone: Boolean = false,
)

// Nightly closing models

data class ClosingFieldDef(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val type: String, // "stars" or "text"
)

data class TaskClosingNote(
    val taskId: String,
    val reason: String = "",
    val carryForward: Boolean = false,
)

data class ClosingState(
    val date: String = "",
    val dayRating: Int = 0,
    val customRatings: Map<String, Int> = emptyMap(),
    val customTexts: Map<String, String> = emptyMap(),
    val taskNotes: List<TaskClosingNote> = emptyList(),
    val closingDone: Boolean = false,
)

// Helpers

fun getEffectiveDate(resetHour: Int = 5): String {
    val cal = Calendar.getInstance()
    if (cal.get(Calendar.HOUR_OF_DAY) < resetHour) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

fun shouldShowPlanning(context: Context, resetHour: Int): Boolean {
    val state = loadDayState(context)
    return state.date != getEffectiveDate(resetHour) || !state.planningDone
}

fun generateDayTasks(templates: List<TaskTemplate>): List<DayTask> {
    val cal = Calendar.getInstance()
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    val isWeekday = dow in Calendar.MONDAY..Calendar.FRIDAY

    return templates.filter { t ->
        when (t.recurrence) {
            Recurrence.DAILY -> true
            Recurrence.WEEKDAYS -> isWeekday
            Recurrence.WEEKLY -> dow == Calendar.MONDAY
            Recurrence.NONE -> false
        }
    }.map { t ->
        DayTask(
            templateId = t.id,
            title = t.title,
            deadlineHour = t.deadlineHour,
            deadlineMinute = t.deadlineMinute,
            reminderMinutes = t.reminderMinutes,
            rewardAppPackage = t.rewardAppPackage,
            rewardMinutes = t.rewardMinutes,
        )
    }
}

fun formatTime(hour: Int, minute: Int): String {
    val p = if (hour < 12) "AM" else "PM"
    val h = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$h:${minute.toString().padStart(2, '0')} $p"
}

// Notifications

fun createNotificationChannel(context: Context) {
    val ch = NotificationChannel("reminders", "Task Reminders", NotificationManager.IMPORTANCE_HIGH)
    ch.description = "Deadline reminders"
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .createNotificationChannel(ch)
}

fun scheduleReminder(context: Context, task: DayTask) {
    if (task.deadlineHour == null || task.deadlineMinute == null || task.reminderMinutes == null) return
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, task.deadlineHour)
        set(Calendar.MINUTE, task.deadlineMinute)
        set(Calendar.SECOND, 0)
        add(Calendar.MINUTE, -task.reminderMinutes)
    }
    if (cal.timeInMillis <= System.currentTimeMillis()) return

    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("task_title", task.title)
        putExtra("task_id", task.id)
        putExtra("minutes_before", task.reminderMinutes)
    }
    val pi = PendingIntent.getBroadcast(
        context, task.id.hashCode(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        .set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
}

// Persistence

private const val PREFS = "launcher_tasks"

fun loadTaskTemplates(context: Context): List<TaskTemplate> {
    val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("templates", null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { templateFromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }
}

fun saveTaskTemplates(context: Context, templates: List<TaskTemplate>) {
    val arr = JSONArray().apply { templates.forEach { put(templateToJson(it)) } }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString("templates", arr.toString()).apply()
}

fun loadDayState(context: Context): DayState {
    val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("day_state", null) ?: return DayState()
    return try {
        val j = JSONObject(json)
        DayState(
            date = j.optString("date", ""),
            tasks = j.optJSONArray("tasks")?.let { a ->
                (0 until a.length()).map { dayTaskFromJson(a.getJSONObject(it)) }
            } ?: emptyList(),
            planningDone = j.optBoolean("planningDone", false),
        )
    } catch (_: Exception) { DayState() }
}

fun saveDayState(context: Context, state: DayState) {
    val arr = JSONArray().apply { state.tasks.forEach { put(dayTaskToJson(it)) } }
    val json = JSONObject().apply {
        put("date", state.date)
        put("tasks", arr)
        put("planningDone", state.planningDone)
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString("day_state", json.toString()).apply()
}

fun loadRewardSessions(context: Context): List<RewardSession> {
    val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("reward_sessions", null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        val now = System.currentTimeMillis()
        (0 until arr.length()).mapNotNull {
            val j = arr.getJSONObject(it)
            RewardSession(j.getString("app"), j.getLong("expires")).takeIf { s -> s.expiresAt > now }
        }
    } catch (_: Exception) { emptyList() }
}

fun saveRewardSessions(context: Context, sessions: List<RewardSession>) {
    val now = System.currentTimeMillis()
    val arr = JSONArray().apply {
        sessions.filter { it.expiresAt > now }.forEach {
            put(JSONObject().apply { put("app", it.appPackage); put("expires", it.expiresAt) })
        }
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString("reward_sessions", arr.toString()).apply()
}

// JSON helpers

private fun templateToJson(t: TaskTemplate) = JSONObject().apply {
    put("id", t.id); put("title", t.title)
    put("dh", t.deadlineHour ?: -1); put("dm", t.deadlineMinute ?: -1)
    put("rm", t.reminderMinutes ?: -1)
    put("rap", t.rewardAppPackage ?: ""); put("rmin", t.rewardMinutes)
    put("rec", t.recurrence.name)
}

private fun templateFromJson(j: JSONObject) = TaskTemplate(
    id = j.getString("id"), title = j.getString("title"),
    deadlineHour = j.optInt("dh", -1).takeIf { it >= 0 },
    deadlineMinute = j.optInt("dm", -1).takeIf { it >= 0 },
    reminderMinutes = j.optInt("rm", -1).takeIf { it >= 0 },
    rewardAppPackage = j.optString("rap", "").ifEmpty { null },
    rewardMinutes = j.optInt("rmin", 15),
    recurrence = try { Recurrence.valueOf(j.getString("rec")) } catch (_: Exception) { Recurrence.DAILY },
)

private fun dayTaskToJson(t: DayTask) = JSONObject().apply {
    put("id", t.id); put("tid", t.templateId ?: ""); put("title", t.title)
    put("dh", t.deadlineHour ?: -1); put("dm", t.deadlineMinute ?: -1)
    put("rm", t.reminderMinutes ?: -1)
    put("rap", t.rewardAppPackage ?: ""); put("rmin", t.rewardMinutes)
    put("done", t.isCompleted)
}

private fun dayTaskFromJson(j: JSONObject) = DayTask(
    id = j.getString("id"),
    templateId = j.optString("tid", "").ifEmpty { null },
    title = j.getString("title"),
    deadlineHour = j.optInt("dh", -1).takeIf { it >= 0 },
    deadlineMinute = j.optInt("dm", -1).takeIf { it >= 0 },
    reminderMinutes = j.optInt("rm", -1).takeIf { it >= 0 },
    rewardAppPackage = j.optString("rap", "").ifEmpty { null },
    rewardMinutes = j.optInt("rmin", 15),
    isCompleted = j.optBoolean("done", false),
)

// Closing state persistence

fun loadClosingState(context: Context): ClosingState {
    val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("closing_state", null) ?: return ClosingState()
    return try {
        val j = JSONObject(json)
        ClosingState(
            date = j.optString("date", ""),
            dayRating = j.optInt("dayRating", 0),
            customRatings = j.optJSONObject("customRatings")?.let { cr ->
                cr.keys().asSequence().associateWith { cr.getInt(it) }
            } ?: emptyMap(),
            customTexts = j.optJSONObject("customTexts")?.let { ct ->
                ct.keys().asSequence().associateWith { ct.getString(it) }
            } ?: emptyMap(),
            taskNotes = j.optJSONArray("taskNotes")?.let { a ->
                (0 until a.length()).map {
                    val n = a.getJSONObject(it)
                    TaskClosingNote(
                        taskId = n.getString("taskId"),
                        reason = n.optString("reason", ""),
                        carryForward = n.optBoolean("carryForward", false),
                    )
                }
            } ?: emptyList(),
            closingDone = j.optBoolean("closingDone", false),
        )
    } catch (_: Exception) { ClosingState() }
}

fun saveClosingState(context: Context, state: ClosingState) {
    val json = JSONObject().apply {
        put("date", state.date)
        put("dayRating", state.dayRating)
        put("customRatings", JSONObject().apply {
            state.customRatings.forEach { (k, v) -> put(k, v) }
        })
        put("customTexts", JSONObject().apply {
            state.customTexts.forEach { (k, v) -> put(k, v) }
        })
        put("taskNotes", JSONArray().apply {
            state.taskNotes.forEach {
                put(JSONObject().apply {
                    put("taskId", it.taskId)
                    put("reason", it.reason)
                    put("carryForward", it.carryForward)
                })
            }
        })
        put("closingDone", state.closingDone)
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString("closing_state", json.toString()).apply()
}
