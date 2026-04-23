package com.phonelauncher

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TimerSection(
    timers: List<TimerEntry>,
    settings: LauncherSettings,
    onPause: (TimerEntry) -> Unit,
    onShowDetail: (TimerEntry) -> Unit,
    onRemove: (TimerEntry) -> Unit,
    onToggleCollapse: (TimerEntry) -> Unit,
) {
    if (timers.isEmpty()) return

    val textColor = Color(settings.clockColor)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(timers.any { it.isRunning }) {
        while (true) { delay(1000); now = System.currentTimeMillis() }
    }

    val roots = timers.filter { it.parentId == null }
    roots.forEach { root ->
        val children = timers.filter { it.parentId == root.id }
        TimerRow(root, now, textColor, 0,
            hasChildren = children.isNotEmpty(),
            isCollapsed = root.collapsed,
            onClick = { if (root.isRunning) onPause(root) else onShowDetail(root) },
            onRemove = { onRemove(root) },
            onToggleCollapse = { onToggleCollapse(root) })
        if (!root.collapsed) {
            children.forEach { child ->
                TimerRow(child, now, textColor, 1,
                    hasChildren = false,
                    isCollapsed = false,
                    onClick = { if (child.isRunning) onPause(child) else onShowDetail(child) },
                    onRemove = { onRemove(child) },
                    onToggleCollapse = { })
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerRow(
    timer: TimerEntry, now: Long, textColor: Color, indent: Int,
    hasChildren: Boolean,
    isCollapsed: Boolean,
    onClick: () -> Unit, onRemove: () -> Unit,
    onToggleCollapse: () -> Unit,
) {
    val elapsed = timer.elapsed(now)
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onRemove)
            .padding(start = (indent * 20).dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (indent > 0) Text("  ", color = textColor.copy(alpha = 0.3f), fontSize = 14.sp)
        if (hasChildren) {
            Text(
                if (isCollapsed) "+" else "-",
                modifier = Modifier.clickable { onToggleCollapse() }.padding(horizontal = 4.dp),
                color = textColor.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            if (timer.isRunning) ">" else "||",
            color = if (timer.isRunning) Color(0xFF4CAF50) else textColor.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        Spacer(Modifier.width(8.dp))
        Text(timer.name, color = textColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            formatElapsed(elapsed),
            color = if (timer.isRunning) Color(0xFF4CAF50) else textColor.copy(alpha = 0.6f),
            fontSize = 15.sp, fontWeight = FontWeight.Light
        )
        if (timer.dndEnabled && timer.isRunning) Text(" dnd", fontSize = 10.sp, color = textColor.copy(alpha = 0.4f))
    }
}

// Timer Detail Dialog (timeline + segment editing)

@Composable
fun TimerDetailDialog(
    timer: TimerEntry,
    settings: LauncherSettings,
    onUpdateSegments: (List<TimeSegment>) -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val textColor = Color(settings.clockColor)
    val subtleColor = textColor.copy(alpha = 0.5f)
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var segments by remember { mutableStateOf(timer.segments) }
    var editingSegIdx by remember { mutableStateOf(-1) }
    var editingStart by remember { mutableStateOf(true) }

    // Time picker
    if (editingSegIdx >= 0 && editingSegIdx < segments.size) {
        val seg = segments[editingSegIdx]
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (editingStart) seg.startMs else seg.endMs
        }
        DisposableEffect(editingSegIdx, editingStart) {
            val dlg = TimePickerDialog(context, { _, h, m ->
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = if (editingStart) seg.startMs else seg.endMs
                    set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0)
                }
                val updated = segments.toMutableList()
                updated[editingSegIdx] = if (editingStart)
                    seg.copy(startMs = newCal.timeInMillis)
                else
                    seg.copy(endMs = newCal.timeInMillis)
                segments = updated
                onUpdateSegments(updated)
                editingSegIdx = -1
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false)
            dlg.setOnCancelListener { editingSegIdx = -1 }
            dlg.show()
            onDispose { dlg.dismiss() }
        }
    }

    val totalMs = segments.sumOf { it.endMs - it.startMs }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(settings.backgroundColor),
        title = {
            Column {
                Text(timer.name, color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Total: ${formatElapsed(totalMs)}", color = subtleColor, fontSize = 14.sp)
            }
        },
        text = {
            Column {
                if (segments.isEmpty()) {
                    Text("No sessions recorded yet.", color = subtleColor, fontSize = 14.sp)
                } else {
                    Text("SESSIONS", fontSize = 10.sp, color = subtleColor, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    segments.forEachIndexed { idx, seg ->
                        val dur = seg.endMs - seg.startMs
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                timeFmt.format(Date(seg.startMs)),
                                modifier = Modifier.clickable { editingSegIdx = idx; editingStart = true },
                                color = textColor, fontSize = 14.sp
                            )
                            Text(" - ", color = subtleColor, fontSize = 14.sp)
                            Text(
                                timeFmt.format(Date(seg.endMs)),
                                modifier = Modifier.clickable { editingSegIdx = idx; editingStart = false },
                                color = textColor, fontSize = 14.sp
                            )
                            Spacer(Modifier.weight(1f))
                            Text(formatElapsed(dur), color = subtleColor, fontSize = 13.sp)
                            Text(
                                " x",
                                modifier = Modifier.clickable {
                                    segments = segments.toMutableList().apply { removeAt(idx) }
                                    onUpdateSegments(segments)
                                }.padding(start = 8.dp),
                                color = Color(0xFFFF5252), fontSize = 14.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap a time to edit it",
                    color = subtleColor.copy(alpha = 0.5f), fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onResume) { Text("Resume", color = textColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = subtleColor) }
        }
    )
}

// New Timer Dialog

@Composable
fun NewTimerDialog(
    settings: LauncherSettings,
    dayTasks: List<DayTask>,
    timerHistory: List<String>,
    existingTimers: List<TimerEntry>,
    onStart: (name: String, taskId: String?, parentId: String?, dnd: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val textColor = Color(settings.clockColor)
    var name by remember { mutableStateOf("") }
    var dndEnabled by remember { mutableStateOf(false) }
    var selectedParent by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(settings.backgroundColor),
        title = { Text("New Timer", color = textColor) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Timer name", color = textColor.copy(alpha = 0.4f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor, unfocusedTextColor = textColor,
                        cursorColor = textColor,
                        focusedBorderColor = textColor.copy(alpha = 0.5f),
                        unfocusedBorderColor = textColor.copy(alpha = 0.2f),
                    )
                )
                Spacer(Modifier.height(12.dp))

                val incompleteTasks = dayTasks.filter { !it.isCompleted }
                if (incompleteTasks.isNotEmpty()) {
                    Text("FROM TASKS", fontSize = 10.sp, color = textColor.copy(alpha = 0.4f), letterSpacing = 2.sp)
                    Spacer(Modifier.height(4.dp))
                    incompleteTasks.forEach { task ->
                        Text(task.title,
                            modifier = Modifier.fillMaxWidth().clickable { name = task.title }.padding(vertical = 6.dp),
                            color = textColor.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                val filtered = timerHistory.filter { h -> existingTimers.none { it.name == h } }.take(5)
                if (filtered.isNotEmpty()) {
                    Text("RECENT", fontSize = 10.sp, color = textColor.copy(alpha = 0.4f), letterSpacing = 2.sp)
                    Spacer(Modifier.height(4.dp))
                    filtered.forEach { h ->
                        Text(h,
                            modifier = Modifier.fillMaxWidth().clickable { name = h }.padding(vertical = 6.dp),
                            color = textColor.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Do Not Disturb", color = textColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = dndEnabled, onCheckedChange = {
                        if (it && !hasDndPermission(context)) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        } else dndEnabled = it
                    }, colors = SwitchDefaults.colors(checkedTrackColor = textColor.copy(alpha = 0.3f)))
                }

                if (existingTimers.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("SUB-TIMER OF", fontSize = 10.sp, color = textColor.copy(alpha = 0.4f), letterSpacing = 2.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("None",
                        modifier = Modifier.fillMaxWidth().clickable { selectedParent = null }.padding(vertical = 4.dp),
                        color = if (selectedParent == null) textColor else textColor.copy(alpha = 0.5f), fontSize = 13.sp)
                    existingTimers.filter { it.parentId == null }.forEach { t ->
                        Text(t.name,
                            modifier = Modifier.fillMaxWidth().clickable { selectedParent = t.id }.padding(vertical = 4.dp),
                            color = if (selectedParent == t.id) textColor else textColor.copy(alpha = 0.5f), fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val linked = dayTasks.find { it.title == name }?.id
                    onStart(name, linked, selectedParent, dndEnabled)
                }
            }) { Text("Start", color = textColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = textColor.copy(alpha = 0.5f)) }
        }
    )
}

// Quick Actions

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickActionRow(
    actions: List<QuickAction>,
    settings: LauncherSettings,
    onIncrement: (QuickAction) -> Unit,
    onDecrement: (QuickAction) -> Unit,
    onRemove: (QuickAction) -> Unit,
) {
    val textColor = Color(settings.clockColor)

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            Surface(shape = RoundedCornerShape(20.dp), color = textColor.copy(alpha = 0.08f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "-",
                        modifier = Modifier.clickable { onDecrement(action) }
                            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        color = Color(0xFFFF5252).copy(alpha = 0.7f), fontSize = 14.sp
                    )
                    Text(
                        "${action.name}: ${action.count}",
                        color = textColor, fontSize = 13.sp,
                        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { onRemove(action) })
                    )
                    Text(
                        "+",
                        modifier = Modifier.clickable { onIncrement(action) }
                            .padding(start = 4.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                        color = Color(0xFF4CAF50), fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun NewQuickActionDialog(
    settings: LauncherSettings,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val textColor = Color(settings.clockColor)
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(settings.backgroundColor),
        title = { Text("New Counter", color = textColor) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Water, Pushups", color = textColor.copy(alpha = 0.4f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor, unfocusedTextColor = textColor,
                    cursorColor = textColor,
                    focusedBorderColor = textColor.copy(alpha = 0.5f),
                    unfocusedBorderColor = textColor.copy(alpha = 0.2f),
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onAdd(name) }) { Text("Add", color = textColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = textColor.copy(alpha = 0.5f)) }
        }
    )
}
