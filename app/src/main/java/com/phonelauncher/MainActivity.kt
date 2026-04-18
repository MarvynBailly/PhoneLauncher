package com.phonelauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.phonelauncher.ui.theme.PhoneLauncherTheme
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class AppInfo(val label: String, val packageName: String)

private enum class Screen { HOME, SEARCH, SETTINGS, PLANNING, TASK_EDIT }

class MainActivity : ComponentActivity() {
    var homePressCount by mutableStateOf(0)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)
        enableEdgeToEdge()
        setContent {
            BackHandler { }
            PhoneLauncherTheme { LauncherScreen(this) }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        homePressCount++
    }
}

@Composable
private fun LauncherScreen(activity: MainActivity) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.HOME) }
    var settings by remember { mutableStateOf(loadSettings(context)) }
    var dayState by remember { mutableStateOf(loadDayState(context)) }
    var templates by remember { mutableStateOf(loadTaskTemplates(context)) }
    var rewardSessions by remember { mutableStateOf(loadRewardSessions(context)) }
    var editingTask by remember { mutableStateOf<DayTask?>(null) }
    var screenBeforeEdit by remember { mutableStateOf(Screen.HOME) }
    var blockMessage by remember { mutableStateOf<String?>(null) }

    // Timers & quick actions
    val effectiveDate = remember { getEffectiveDate(settings.dayResetHour) }
    var timers by remember { mutableStateOf(loadTimers(context)) }
    var timerHistory by remember { mutableStateOf(loadTimerHistory(context)) }
    var quickActions by remember { mutableStateOf(loadQuickActions(context, effectiveDate)) }
    var showNewTimerDialog by remember { mutableStateOf(false) }
    var showNewCounterDialog by remember { mutableStateOf(false) }
    var timerDetailFor by remember { mutableStateOf<TimerEntry?>(null) }
    var timerSyncTrigger by remember { mutableIntStateOf(0) }

    // Sync timers when service pauses one via notification
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) { timerSyncTrigger++ }
        }
        val filter = IntentFilter(TimerService.ACTION_SYNC)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(timerSyncTrigger) {
        if (timerSyncTrigger > 0) timers = loadTimers(context)
    }

    // Start service if timers are running
    LaunchedEffect(timers.any { it.isRunning }) {
        if (timers.any { it.isRunning }) {
            val svcIntent = Intent(context, TimerService::class.java)
            context.startForegroundService(svcIntent)
        }
    }

    // Request notification permission
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Home button → go home
    LaunchedEffect(activity.homePressCount) {
        if (activity.homePressCount > 0) screen = Screen.HOME
    }

    // Day reset check — resets tasks, timers, and quick actions
    LaunchedEffect(Unit) {
        val today = getEffectiveDate(settings.dayResetHour)
        if (dayState.date != today) {
            val newTasks = generateDayTasks(templates)
            dayState = DayState(date = today, tasks = newTasks, planningDone = false)
            saveDayState(context, dayState)
            // Reset timers for new day — save running ones to history first
            timers.forEach { addToHistory(context, it.name) }
            timerHistory = loadTimerHistory(context)
            timers = emptyList()
            saveTimers(context, timers)
            // Reset quick actions
            quickActions = emptyList()
            saveQuickActions(context, quickActions, today)
        }
        if (!dayState.planningDone) screen = Screen.PLANNING
    }

    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { ri -> AppInfo(ri.loadLabel(pm).toString(), ri.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    val pinnedApps = remember(settings.pinnedApps) {
        settings.pinnedApps.mapNotNull { pkg -> apps.find { it.packageName == pkg } }
    }

    fun update(new: LauncherSettings) { settings = new; saveSettings(context, new) }

    fun togglePin(app: AppInfo) {
        val pinned = settings.pinnedApps.toMutableList()
        if (app.packageName in pinned) pinned.remove(app.packageName) else pinned.add(app.packageName)
        update(settings.copy(pinnedApps = pinned))
    }

    fun launchApp(app: AppInfo) {
        val isRestricted = app.packageName in settings.restrictedApps && !settings.emergencyOverride
        if (isRestricted) {
            val session = rewardSessions.find {
                it.appPackage == app.packageName && it.expiresAt > System.currentTimeMillis()
            }
            if (session == null) {
                blockMessage = "${app.label} is restricted.\nComplete a task to earn access."
                return
            }
        }
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            context.startActivity(it)
        }
    }

    fun completeTask(task: DayTask) {
        val wasCompleted = task.isCompleted
        val updated = dayState.tasks.map {
            if (it.id == task.id) it.copy(isCompleted = !it.isCompleted) else it
        }
        dayState = dayState.copy(tasks = updated)
        saveDayState(context, dayState)
        if (!wasCompleted && task.rewardAppPackage != null) {
            val session = RewardSession(
                appPackage = task.rewardAppPackage,
                expiresAt = System.currentTimeMillis() + task.rewardMinutes * 60_000L
            )
            rewardSessions = rewardSessions + session
            saveRewardSessions(context, rewardSessions)
        }
    }

    fun saveTask(task: DayTask, recurrence: Recurrence) {
        if (recurrence != Recurrence.NONE) {
            val tmpl = TaskTemplate(
                id = task.templateId ?: UUID.randomUUID().toString(),
                title = task.title, deadlineHour = task.deadlineHour,
                deadlineMinute = task.deadlineMinute, reminderMinutes = task.reminderMinutes,
                rewardAppPackage = task.rewardAppPackage, rewardMinutes = task.rewardMinutes,
                recurrence = recurrence,
            )
            val idx = templates.indexOfFirst { it.id == tmpl.id }
            templates = if (idx >= 0) templates.toMutableList().apply { set(idx, tmpl) } else templates + tmpl
            saveTaskTemplates(context, templates)
        }
        val taskForDay = if (recurrence != Recurrence.NONE && task.templateId == null)
            task.copy(templateId = task.id) else task
        val idx = dayState.tasks.indexOfFirst { it.id == taskForDay.id }
        val newTasks = if (idx >= 0) dayState.tasks.toMutableList().apply { set(idx, taskForDay) }
        else dayState.tasks + taskForDay
        dayState = dayState.copy(tasks = newTasks)
        saveDayState(context, dayState)
        screen = screenBeforeEdit
    }

    fun quickAddTask(title: String) {
        val task = DayTask(title = title)
        dayState = dayState.copy(tasks = dayState.tasks + task)
        saveDayState(context, dayState)
    }

    fun removeTask(task: DayTask) {
        dayState = dayState.copy(tasks = dayState.tasks.filter { it.id != task.id })
        saveDayState(context, dayState)
    }

    // Timer operations
    fun pauseTimer(timer: TimerEntry) {
        timers = timers.map {
            if (it.id == timer.id) {
                val paused = it.pause()
                if (paused.dndEnabled && timers.none { t -> t.id != timer.id && t.isRunning && t.dndEnabled })
                    setDnd(context, false)
                paused
            } else it
        }
        saveTimers(context, timers)
    }

    fun resumeTimer(timer: TimerEntry) {
        timers = timers.map { if (it.id == timer.id) it.resume() else it }
        if (timer.dndEnabled) setDnd(context, true)
        saveTimers(context, timers)
        timerDetailFor = null
    }

    fun updateTimerSegments(timer: TimerEntry, segments: List<TimeSegment>) {
        timers = timers.map { if (it.id == timer.id) it.copy(segments = segments) else it }
        saveTimers(context, timers)
    }

    fun removeTimer(timer: TimerEntry) {
        if (timer.dndEnabled && timer.isRunning) {
            if (timers.none { it.id != timer.id && it.isRunning && it.dndEnabled })
                setDnd(context, false)
        }
        addToHistory(context, timer.name)
        timerHistory = loadTimerHistory(context)
        timers = timers.filter { it.id != timer.id && it.parentId != timer.id }
        saveTimers(context, timers)
    }

    fun startTimer(name: String, taskId: String?, parentId: String?, dnd: Boolean) {
        // Resume existing paused timer with same name
        val existing = timers.find { it.name == name && !it.isRunning }
        if (existing != null) {
            timers = timers.map { if (it.id == existing.id) it.resume() else it }
        } else {
            timers = timers + TimerEntry(name = name, taskId = taskId, parentId = parentId, dndEnabled = dnd)
        }
        if (dnd) setDnd(context, true)
        addToHistory(context, name)
        timerHistory = loadTimerHistory(context)
        saveTimers(context, timers)
        showNewTimerDialog = false
    }

    // Quick action operations
    fun incrementAction(action: QuickAction) {
        quickActions = quickActions.map { if (it.id == action.id) it.copy(count = it.count + 1) else it }
        saveQuickActions(context, quickActions, effectiveDate)
    }

    fun decrementAction(action: QuickAction) {
        quickActions = quickActions.map {
            if (it.id == action.id) it.copy(count = maxOf(0, it.count - 1)) else it
        }
        saveQuickActions(context, quickActions, effectiveDate)
    }

    fun addQuickAction(name: String) {
        quickActions = quickActions + QuickAction(name = name)
        saveQuickActions(context, quickActions, effectiveDate)
        showNewCounterDialog = false
    }

    val backgroundBitmap = remember(settings.backgroundImageUri) {
        settings.backgroundImageUri?.let { uriStr ->
            try {
                context.contentResolver.openInputStream(Uri.parse(uriStr))?.use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            } catch (_: Exception) { null }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(settings.backgroundColor))) {
        if (screen == Screen.HOME && backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        }

        when (screen) {
            Screen.PLANNING -> DailyPlanningScreen(
                settings = settings, dayTasks = dayState.tasks,
                onUpdateTasks = { dayState = dayState.copy(tasks = it); saveDayState(context, dayState) },
                onAddTask = { editingTask = null; screenBeforeEdit = Screen.PLANNING; screen = Screen.TASK_EDIT },
                onStartDay = {
                    dayState = dayState.copy(planningDone = true)
                    saveDayState(context, dayState)
                    dayState.tasks.forEach { scheduleReminder(context, it) }
                    screen = Screen.HOME
                }
            )

            Screen.TASK_EDIT -> TaskEditScreen(
                settings = settings, allApps = apps, existingTask = editingTask,
                onSave = ::saveTask, onDismiss = { screen = screenBeforeEdit }
            )

            Screen.HOME -> HomeScreen(
                settings = settings, pinnedApps = pinnedApps,
                dayTasks = dayState.tasks, rewardSessions = rewardSessions,
                timers = timers, quickActions = quickActions,
                onLaunch = ::launchApp, onUnpin = ::togglePin,
                onCompleteTask = ::completeTask, onRemoveTask = ::removeTask,
                onQuickAddTask = ::quickAddTask,
                onAddTask = { editingTask = null; screenBeforeEdit = Screen.HOME; screen = Screen.TASK_EDIT },
                onPauseTimer = ::pauseTimer, onShowTimerDetail = { timerDetailFor = it },
                onRemoveTimer = ::removeTimer, onAddTimer = { showNewTimerDialog = true },
                onIncrementAction = ::incrementAction, onDecrementAction = ::decrementAction,
                onRemoveAction = { quickActions = quickActions.filter { a -> a.id != it.id }; saveQuickActions(context, quickActions, effectiveDate) },
                onAddCounter = { showNewCounterDialog = true },
                onSearchClick = { screen = Screen.SEARCH }
            )

            Screen.SEARCH -> SearchScreen(
                settings = settings, apps = apps, pinnedPackages = settings.pinnedApps.toSet(),
                onLaunch = ::launchApp, onTogglePin = ::togglePin,
                onSettingsClick = { screen = Screen.SETTINGS },
                onDismiss = { screen = Screen.HOME }
            )

            Screen.SETTINGS -> SettingsScreen(
                settings = settings, allApps = apps,
                onSettingsChanged = ::update, onDismiss = { screen = Screen.SEARCH }
            )
        }

        // Dialogs
        timerDetailFor?.let { timer ->
            TimerDetailDialog(
                timer = timer, settings = settings,
                onUpdateSegments = { updateTimerSegments(timer, it) },
                onResume = { resumeTimer(timer) },
                onDismiss = { timerDetailFor = null }
            )
        }
        if (showNewTimerDialog) {
            NewTimerDialog(
                settings = settings, dayTasks = dayState.tasks,
                timerHistory = timerHistory, existingTimers = timers,
                onStart = ::startTimer, onDismiss = { showNewTimerDialog = false }
            )
        }
        if (showNewCounterDialog) {
            NewQuickActionDialog(
                settings = settings,
                onAdd = ::addQuickAction, onDismiss = { showNewCounterDialog = false }
            )
        }

        // Block message overlay
        blockMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { blockMessage = null },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(msg, color = Color.White, fontSize = 16.sp, lineHeight = 24.sp)
                    Spacer(Modifier.height(24.dp))
                    Text("Tap to dismiss", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    settings: LauncherSettings,
    pinnedApps: List<AppInfo>,
    dayTasks: List<DayTask>,
    rewardSessions: List<RewardSession>,
    timers: List<TimerEntry>,
    quickActions: List<QuickAction>,
    onLaunch: (AppInfo) -> Unit,
    onUnpin: (AppInfo) -> Unit,
    onCompleteTask: (DayTask) -> Unit,
    onRemoveTask: (DayTask) -> Unit,
    onQuickAddTask: (String) -> Unit,
    onAddTask: () -> Unit,
    onPauseTimer: (TimerEntry) -> Unit,
    onShowTimerDetail: (TimerEntry) -> Unit,
    onRemoveTimer: (TimerEntry) -> Unit,
    onAddTimer: () -> Unit,
    onIncrementAction: (QuickAction) -> Unit,
    onDecrementAction: (QuickAction) -> Unit,
    onRemoveAction: (QuickAction) -> Unit,
    onAddCounter: () -> Unit,
    onSearchClick: () -> Unit,

) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Clock(settings)
        Spacer(Modifier.height(4.dp))
        Temperature(settings)
        Spacer(Modifier.height(24.dp))

        // Scrollable middle content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Timers
            TimerSection(timers, settings, onPauseTimer, onShowTimerDetail, onRemoveTimer)

            // Quick Actions
            if (quickActions.isNotEmpty()) {
                QuickActionRow(quickActions, settings, onIncrementAction, onDecrementAction, onRemoveAction)
            }

            // Tasks
            dayTasks.forEach { task ->
                TaskRow(task, settings, rewardSessions, onCompleteTask, onRemoveTask)
            }

            // Unified add row
            UnifiedAddRow(settings, onQuickAddTask, onAddTask, onAddTimer, onAddCounter)

            Spacer(Modifier.height(16.dp))

            // Pinned apps
            pinnedApps.forEach { app ->
                PinnedAppItem(app, settings, rewardSessions, onLaunch = { onLaunch(app) }, onUnpin = { onUnpin(app) })
            }
        }

        SearchBar(settings, onClick = onSearchClick)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TaskRow(
    task: DayTask,
    settings: LauncherSettings,
    rewardSessions: List<RewardSession>,
    onComplete: (DayTask) -> Unit,
    onRemove: (DayTask) -> Unit
) {
    val textColor = Color(settings.clockColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onComplete(task) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (task.isCompleted) "o" else "-",
            color = if (task.isCompleted) Color(0xFF4CAF50) else textColor.copy(alpha = 0.4f),
            fontSize = 14.sp,
            modifier = Modifier.width(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                color = if (task.isCompleted) textColor.copy(alpha = 0.4f) else textColor,
                fontSize = 15.sp,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
            )
        }
        if (task.isCompleted) {
            if (task.rewardAppPackage != null) {
                val session = rewardSessions.find {
                    it.appPackage == task.rewardAppPackage && it.expiresAt > System.currentTimeMillis()
                }
                if (session != null) {
                    val mins = ((session.expiresAt - System.currentTimeMillis()) / 60_000).toInt()
                    Text("${mins}m ", color = Color(0xFF4CAF50), fontSize = 12.sp)
                }
            }
            Text(
                "x",
                modifier = Modifier
                    .clickable { onRemove(task) }
                    .padding(start = 4.dp),
                color = textColor.copy(alpha = 0.3f),
                fontSize = 14.sp
            )
        } else if (task.deadlineHour != null) {
            Text(
                formatTime(task.deadlineHour, task.deadlineMinute ?: 0),
                color = textColor.copy(alpha = 0.4f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun UnifiedAddRow(
    settings: LauncherSettings,
    onQuickAddTask: (String) -> Unit,
    onFullEditor: () -> Unit,
    onAddTimer: () -> Unit,
    onAddCounter: () -> Unit,
) {
    val textColor = Color(settings.clockColor)
    val subtleColor = textColor.copy(alpha = 0.4f)
    // collapsed | picking | typing_task
    var mode by remember { mutableStateOf("collapsed") }
    var text by remember { mutableStateOf("") }

    when (mode) {
        "picking" -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("task", modifier = Modifier.clickable { mode = "typing_task" }, color = textColor, fontSize = 14.sp)
                Text("timer", modifier = Modifier.clickable { onAddTimer(); mode = "collapsed" }, color = textColor, fontSize = 14.sp)
                Text("counter", modifier = Modifier.clickable { onAddCounter(); mode = "collapsed" }, color = textColor, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("x", modifier = Modifier.clickable { mode = "collapsed" }.padding(horizontal = 4.dp), color = subtleColor, fontSize = 16.sp)
            }
        }
        "typing_task" -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Quick task...", color = textColor.copy(alpha = 0.3f)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = textColor, unfocusedTextColor = textColor,
                        cursorColor = textColor,
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = textColor.copy(alpha = 0.2f),
                        unfocusedIndicatorColor = textColor.copy(alpha = 0.1f)
                    )
                )
                if (text.isNotBlank()) {
                    Text(
                        "ok",
                        modifier = Modifier.clickable { onQuickAddTask(text); text = ""; mode = "collapsed" }.padding(horizontal = 8.dp),
                        color = Color(0xFF4CAF50), fontSize = 20.sp
                    )
                }
                Text(
                    "...",
                    modifier = Modifier.clickable { onFullEditor(); mode = "collapsed" }.padding(horizontal = 8.dp),
                    color = subtleColor, fontSize = 20.sp
                )
            }
        }
        else -> {
            Text(
                "+",
                modifier = Modifier.clickable { mode = "picking" }.padding(vertical = 6.dp),
                color = subtleColor, fontSize = 18.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedAppItem(
    app: AppInfo,
    settings: LauncherSettings,
    rewardSessions: List<RewardSession>,
    onLaunch: () -> Unit,
    onUnpin: () -> Unit
) {
    val isRestricted = app.packageName in settings.restrictedApps && !settings.emergencyOverride
    val activeSession = rewardSessions.find {
        it.appPackage == app.packageName && it.expiresAt > System.currentTimeMillis()
    }
    val isLocked = isRestricted && activeSession == null
    val textColor = Color(settings.pinnedAppColor)

    Row(
        modifier = Modifier
            .combinedClickable(onClick = onLaunch, onLongClick = onUnpin)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            app.label,
            fontSize = settings.pinnedAppSize.sp,
            color = if (isLocked) textColor.copy(alpha = 0.3f) else textColor
        )
        if (isRestricted && activeSession != null) {
            val mins = ((activeSession.expiresAt - System.currentTimeMillis()) / 60_000).toInt()
            Text(" ${mins}m", fontSize = 12.sp, color = Color(0xFF4CAF50))
        } else if (isLocked) {
            Text(" [locked]", fontSize = 10.sp, color = textColor.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun Clock(settings: LauncherSettings) {
    var time by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            time = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }
    Text(time, fontSize = settings.clockSize.sp, fontWeight = FontWeight.Thin, color = Color(settings.clockColor))
}

@Composable
private fun Temperature(settings: LauncherSettings) {
    val context = LocalContext.current
    var temp by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return@LaunchedEffect
        }
        temp = withContext(Dispatchers.IO) { fetchTemperature(context) }
    }

    if (temp != null) {
        Text(temp!!, fontSize = settings.tempSize.sp, fontWeight = FontWeight.Light, color = Color(settings.tempColor))
    }
}

@SuppressLint("MissingPermission")
private suspend fun fetchTemperature(context: Context): String? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: requestFreshLocation(lm) ?: return null
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}&current_weather=true&temperature_unit=fahrenheit"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        "${JSONObject(json).getJSONObject("current_weather").getDouble("temperature").toInt()}\u00B0"
    } catch (_: Exception) { null }
}

@SuppressLint("MissingPermission")
private suspend fun requestFreshLocation(lm: LocationManager): Location? {
    val provider = when {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> return null
    }
    return withContext(Dispatchers.Main) {
        suspendCoroutine { cont ->
            lm.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(location: Location) { cont.resume(location) }
                @Deprecated("") override fun onStatusChanged(p: String?, s: Int, b: Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }, null)
        }
    }
}

@Composable
private fun SearchBar(settings: LauncherSettings, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = Color(settings.clockColor).copy(alpha = 0.1f)
    ) {
        Text(
            "Search apps",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            color = Color(settings.clockColor).copy(alpha = 0.5f),
            fontSize = 16.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchScreen(
    settings: LauncherSettings,
    apps: List<AppInfo>,
    pinnedPackages: Set<String>,
    onLaunch: (AppInfo) -> Unit,
    onTogglePin: (AppInfo) -> Unit,
    onSettingsClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val filtered = remember(query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    val textColor = Color(settings.clockColor)

    BackHandler { onDismiss() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(settings.backgroundColor))
            .systemBarsPadding()
    ) {
        TextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focusRequester),
            placeholder = { Text("Search apps", color = textColor.copy(alpha = 0.4f)) },
            singleLine = true, shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedTextColor = textColor, unfocusedTextColor = textColor, cursorColor = textColor,
                focusedContainerColor = textColor.copy(alpha = 0.08f),
                unfocusedContainerColor = textColor.copy(alpha = 0.08f),
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
            )
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(filtered) { app ->
                val isPinned = app.packageName in pinnedPackages
                val isRestricted = app.packageName in settings.restrictedApps
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onTogglePin(app) })
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(app.label, fontSize = 16.sp, color = textColor, modifier = Modifier.weight(1f))
                    if (isRestricted) Text("locked ", fontSize = 10.sp, color = textColor.copy(alpha = 0.3f))
                    if (isPinned) Text("pinned", fontSize = 12.sp, color = textColor.copy(alpha = 0.3f))
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onSettingsClick() }
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text("Settings", fontSize = 16.sp, color = textColor.copy(alpha = 0.5f))
                }
            }
        }
    }
}
