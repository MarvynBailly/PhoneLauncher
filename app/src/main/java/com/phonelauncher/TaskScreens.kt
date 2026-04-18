package com.phonelauncher

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

@Composable
fun DailyPlanningScreen(
    settings: LauncherSettings,
    dayTasks: List<DayTask>,
    onUpdateTasks: (List<DayTask>) -> Unit,
    onAddTask: () -> Unit,
    onStartDay: () -> Unit,
) {
    val textColor = Color(settings.clockColor)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(settings.backgroundColor))
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Text("Good morning", fontSize = 32.sp, fontWeight = FontWeight.Thin, color = textColor)
        Spacer(Modifier.height(8.dp))
        Text("Plan your day", fontSize = 16.sp, color = textColor.copy(alpha = 0.5f))
        Spacer(Modifier.height(48.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (dayTasks.isEmpty()) {
                Text(
                    "No tasks yet. Add your first goal.",
                    color = textColor.copy(alpha = 0.4f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))
            }

            dayTasks.forEachIndexed { index, task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(task.title, color = textColor, fontSize = 16.sp)
                        if (task.deadlineHour != null) {
                            Text(
                                formatTime(task.deadlineHour, task.deadlineMinute ?: 0),
                                color = textColor.copy(alpha = 0.4f),
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (task.rewardAppPackage != null) {
                        Text(
                            "${task.rewardMinutes}m",
                            color = textColor.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(
                        "x",
                        modifier = Modifier
                            .clickable {
                                onUpdateTasks(dayTasks.toMutableList().apply { removeAt(index) })
                            }
                            .padding(8.dp),
                        color = Color(0xFFFF5252),
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "+ Add task",
                color = textColor.copy(alpha = 0.7f),
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable { onAddTask() }
                    .padding(vertical = 8.dp)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onStartDay() },
            shape = RoundedCornerShape(28.dp),
            color = textColor.copy(alpha = 0.15f)
        ) {
            Text(
                "Start Day",
                modifier = Modifier.padding(16.dp),
                color = textColor,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun TaskEditScreen(
    settings: LauncherSettings,
    allApps: List<AppInfo>,
    existingTask: DayTask?,
    onSave: (DayTask, Recurrence) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val textColor = Color(settings.clockColor)
    val subtleColor = textColor.copy(alpha = 0.5f)

    var title by remember { mutableStateOf(existingTask?.title ?: "") }
    var hasDeadline by remember { mutableStateOf(existingTask?.deadlineHour != null) }
    var deadlineHour by remember { mutableStateOf(existingTask?.deadlineHour ?: 17) }
    var deadlineMinute by remember { mutableStateOf(existingTask?.deadlineMinute ?: 0) }
    var reminderMinutes by remember { mutableStateOf(existingTask?.reminderMinutes) }
    var recurrence by remember { mutableStateOf(Recurrence.NONE) }
    var rewardApp by remember { mutableStateOf(existingTask?.rewardAppPackage) }
    var rewardMinutes by remember { mutableStateOf(existingTask?.rewardMinutes ?: 15) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        DisposableEffect(Unit) {
            val dlg = TimePickerDialog(context, { _, h, m ->
                deadlineHour = h; deadlineMinute = m; showTimePicker = false
            }, deadlineHour, deadlineMinute, false)
            dlg.setOnCancelListener { showTimePicker = false }
            dlg.show()
            onDispose { dlg.dismiss() }
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    item {
                        Text(
                            "None",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rewardApp = null; showAppPicker = false }
                                .padding(14.dp),
                            color = textColor
                        )
                    }
                    items(allApps) { app ->
                        Text(
                            app.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rewardApp = app.packageName; showAppPicker = false }
                                .padding(14.dp),
                            color = textColor
                        )
                    }
                }
            },
            confirmButton = {},
            containerColor = Color(settings.backgroundColor)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(settings.backgroundColor))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "<",
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(8.dp),
                fontSize = 24.sp,
                color = textColor
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (existingTask != null) "Edit Task" else "New Task",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }

        Spacer(Modifier.height(32.dp))

        // Title
        Text("TASK", fontSize = 11.sp, color = subtleColor, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("What do you need to do?", color = subtleColor) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                cursorColor = textColor,
                focusedBorderColor = textColor.copy(alpha = 0.5f),
                unfocusedBorderColor = textColor.copy(alpha = 0.2f),
            )
        )

        Spacer(Modifier.height(28.dp))

        // Deadline
        Text("DEADLINE", fontSize = 11.sp, color = subtleColor, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Set deadline", modifier = Modifier.weight(1f), color = textColor, fontSize = 16.sp)
            Switch(
                checked = hasDeadline,
                onCheckedChange = { hasDeadline = it },
                colors = SwitchDefaults.colors(checkedTrackColor = textColor.copy(alpha = 0.3f))
            )
        }
        if (hasDeadline) {
            Text(
                formatTime(deadlineHour, deadlineMinute),
                modifier = Modifier
                    .clickable { showTimePicker = true }
                    .padding(vertical = 8.dp),
                color = textColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light
            )

            Spacer(Modifier.height(8.dp))
            Text("REMIND BEFORE", fontSize = 11.sp, color = subtleColor, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "Off", 5 to "5m", 10 to "10m", 15 to "15m", 30 to "30m", 60 to "1h")
                    .forEach { (mins, label) ->
                        val sel = reminderMinutes == mins
                        Surface(
                            modifier = Modifier.clickable { reminderMinutes = mins },
                            shape = RoundedCornerShape(16.dp),
                            color = if (sel) textColor.copy(alpha = 0.2f) else Color.Transparent,
                            border = if (!sel) BorderStroke(1.dp, textColor.copy(alpha = 0.2f)) else null
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = textColor,
                                fontSize = 13.sp
                            )
                        }
                    }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Recurrence
        Text("REPEAT", fontSize = 11.sp, color = subtleColor, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Recurrence.values().forEach { r ->
                val sel = recurrence == r
                val label = when (r) {
                    Recurrence.NONE -> "Once"
                    Recurrence.DAILY -> "Daily"
                    Recurrence.WEEKDAYS -> "Weekdays"
                    Recurrence.WEEKLY -> "Weekly"
                }
                Surface(
                    modifier = Modifier.clickable { recurrence = r },
                    shape = RoundedCornerShape(16.dp),
                    color = if (sel) textColor.copy(alpha = 0.2f) else Color.Transparent,
                    border = if (!sel) BorderStroke(1.dp, textColor.copy(alpha = 0.2f)) else null
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = textColor,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Reward
        Text("REWARD", fontSize = 11.sp, color = subtleColor, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("App: ", color = textColor, fontSize = 15.sp)
            val appLabel = rewardApp?.let { pkg -> allApps.find { it.packageName == pkg }?.label }
                ?: "None"
            Surface(
                modifier = Modifier.clickable { showAppPicker = true },
                shape = RoundedCornerShape(12.dp),
                color = textColor.copy(alpha = 0.08f)
            ) {
                Text(
                    appLabel,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = textColor,
                    fontSize = 15.sp
                )
            }
        }

        if (rewardApp != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Time: ", color = textColor, fontSize = 15.sp)
                Slider(
                    value = rewardMinutes.toFloat(),
                    onValueChange = { rewardMinutes = it.toInt() },
                    valueRange = 5f..120f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = textColor,
                        activeTrackColor = textColor.copy(alpha = 0.5f),
                        inactiveTrackColor = textColor.copy(alpha = 0.15f)
                    )
                )
                Text(
                    "${rewardMinutes}m",
                    color = textColor,
                    fontSize = 15.sp,
                    modifier = Modifier.width(45.dp)
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (title.isNotBlank()) {
                        val task = DayTask(
                            id = existingTask?.id ?: UUID.randomUUID().toString(),
                            templateId = existingTask?.templateId,
                            title = title,
                            deadlineHour = if (hasDeadline) deadlineHour else null,
                            deadlineMinute = if (hasDeadline) deadlineMinute else null,
                            reminderMinutes = if (hasDeadline) reminderMinutes else null,
                            rewardAppPackage = rewardApp,
                            rewardMinutes = rewardMinutes,
                        )
                        onSave(task, recurrence)
                    }
                },
            shape = RoundedCornerShape(28.dp),
            color = textColor.copy(alpha = 0.15f)
        ) {
            Text(
                "Save",
                modifier = Modifier.padding(16.dp),
                color = textColor,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
