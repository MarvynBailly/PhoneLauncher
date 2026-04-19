package com.phonelauncher

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NightlyClosingScreen(
    settings: LauncherSettings,
    dayTasks: List<DayTask>,
    timers: List<TimerEntry>,
    quickActions: List<QuickAction>,
    closingState: ClosingState,
    isManual: Boolean,
    onComplete: (ClosingState) -> Unit,
    onSkip: () -> Unit,
    onFieldDefsChanged: (List<ClosingFieldDef>) -> Unit,
) {
    if (isManual) {
        BackHandler { onSkip() }
    }

    val textColor = Color(settings.clockColor)
    val subtleColor = textColor.copy(alpha = 0.5f)

    val completedCount = dayTasks.count { it.isCompleted }
    val totalCount = dayTasks.size
    val incompleteTasks = dayTasks.filter { !it.isCompleted }

    var dayRating by remember { mutableIntStateOf(closingState.dayRating) }

    // Per-task notes: reason + carry forward
    val taskReasons = remember {
        mutableStateMapOf<String, String>().apply {
            closingState.taskNotes.forEach { put(it.taskId, it.reason) }
        }
    }
    val taskCarry = remember {
        mutableStateMapOf<String, Boolean>().apply {
            closingState.taskNotes.forEach { put(it.taskId, it.carryForward) }
        }
    }

    // Custom field values
    val customRatings = remember {
        mutableStateMapOf<String, Int>().apply { putAll(closingState.customRatings) }
    }
    val customTexts = remember {
        mutableStateMapOf<String, String>().apply { putAll(closingState.customTexts) }
    }

    // Add field UI
    var addingField by remember { mutableStateOf(false) }
    var newFieldLabel by remember { mutableStateOf("") }
    var newFieldType by remember { mutableStateOf("stars") }

    // Timer stats
    val now = System.currentTimeMillis()
    val totalTimerMs = timers.sumOf { it.elapsed(now) }

    fun buildClosingState(): ClosingState {
        val notes = incompleteTasks.map { task ->
            TaskClosingNote(
                taskId = task.id,
                reason = taskReasons[task.id] ?: "",
                carryForward = taskCarry[task.id] ?: false,
            )
        }
        return ClosingState(
            date = closingState.date,
            dayRating = dayRating,
            customRatings = customRatings.toMap(),
            customTexts = customTexts.toMap(),
            taskNotes = notes,
            closingDone = true,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(settings.backgroundColor))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Day Review", fontSize = 32.sp, fontWeight = FontWeight.Thin, color = textColor)
        Spacer(Modifier.height(8.dp))
        Text(closingState.date, fontSize = 16.sp, color = subtleColor)
        Spacer(Modifier.height(40.dp))

        // -- Summary Stats --
        SectionLabel("SUMMARY", subtleColor)
        Spacer(Modifier.height(12.dp))
        Text(
            "$completedCount / $totalCount tasks completed",
            color = textColor, fontSize = 16.sp
        )
        if (totalTimerMs > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Time tracked: ${formatElapsed(totalTimerMs)}",
                color = textColor, fontSize = 16.sp
            )
        }
        if (quickActions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            quickActions.forEach { qa ->
                Text(
                    "${qa.name}: ${qa.count}",
                    color = subtleColor, fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // -- Day Rating --
        SectionLabel("DAY RATING", subtleColor)
        Spacer(Modifier.height(12.dp))
        StarRatingRow(value = dayRating, textColor = textColor, onChange = { dayRating = it })

        // -- Incomplete Tasks --
        if (incompleteTasks.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            SectionLabel("INCOMPLETE TASKS", subtleColor)
            Spacer(Modifier.height(12.dp))

            incompleteTasks.forEach { task ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(task.title, color = textColor, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = taskReasons[task.id] ?: "",
                        onValueChange = { taskReasons[task.id] = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Why not completed?", color = subtleColor.copy(alpha = 0.5f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            cursorColor = textColor,
                            focusedBorderColor = textColor.copy(alpha = 0.3f),
                            unfocusedBorderColor = textColor.copy(alpha = 0.15f),
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Carry forward", modifier = Modifier.weight(1f), color = subtleColor, fontSize = 14.sp)
                        Switch(
                            checked = taskCarry[task.id] ?: false,
                            onCheckedChange = { taskCarry[task.id] = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = textColor.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        }

        // -- Custom Fields --
        if (settings.closingFields.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            SectionLabel("REFLECTIONS", subtleColor)
            Spacer(Modifier.height(12.dp))

            settings.closingFields.forEach { field ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(field.label, color = textColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(
                        "x",
                        modifier = Modifier
                            .clickable {
                                onFieldDefsChanged(settings.closingFields.filter { it.id != field.id })
                            }
                            .padding(horizontal = 8.dp),
                        color = subtleColor.copy(alpha = 0.3f),
                        fontSize = 14.sp
                    )
                }
                when (field.type) {
                    "stars" -> StarRatingRow(
                        value = customRatings[field.id] ?: 0,
                        textColor = textColor,
                        onChange = { customRatings[field.id] = it }
                    )
                    "text" -> OutlinedTextField(
                        value = customTexts[field.id] ?: "",
                        onValueChange = { customTexts[field.id] = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("...", color = subtleColor.copy(alpha = 0.5f)) },
                        singleLine = false,
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            cursorColor = textColor,
                            focusedBorderColor = textColor.copy(alpha = 0.3f),
                            unfocusedBorderColor = textColor.copy(alpha = 0.15f),
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // -- Add Custom Field --
        Spacer(Modifier.height(16.dp))
        if (addingField) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newFieldLabel,
                    onValueChange = { newFieldLabel = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Field name", color = subtleColor.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        cursorColor = textColor,
                        focusedBorderColor = textColor.copy(alpha = 0.3f),
                        unfocusedBorderColor = textColor.copy(alpha = 0.15f),
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("stars" to "Rating", "text" to "Text").forEach { (type, label) ->
                        val sel = newFieldType == type
                        Surface(
                            modifier = Modifier.clickable { newFieldType = type },
                            shape = RoundedCornerShape(16.dp),
                            color = if (sel) textColor.copy(alpha = 0.2f) else Color.Transparent,
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = textColor,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (newFieldLabel.isNotBlank()) {
                        Text(
                            "Add",
                            modifier = Modifier
                                .clickable {
                                    val def = ClosingFieldDef(label = newFieldLabel.trim(), type = newFieldType)
                                    onFieldDefsChanged(settings.closingFields + def)
                                    newFieldLabel = ""
                                    addingField = false
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        "x",
                        modifier = Modifier
                            .clickable { addingField = false; newFieldLabel = "" }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        color = subtleColor,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            Text(
                "+ Add field",
                modifier = Modifier
                    .clickable { addingField = true }
                    .padding(vertical = 8.dp),
                color = subtleColor.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(40.dp))

        // -- Submit --
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onComplete(buildClosingState()) },
            shape = RoundedCornerShape(28.dp),
            color = textColor.copy(alpha = 0.15f)
        ) {
            Text(
                "Finish Review",
                modifier = Modifier.padding(16.dp),
                color = textColor,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Skip review",
            modifier = Modifier
                .clickable { onSkip() }
                .padding(vertical = 8.dp),
            color = subtleColor.copy(alpha = 0.3f),
            fontSize = 13.sp
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 2.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StarRatingRow(value: Int, textColor: Color, onChange: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        (1..5).forEach { n ->
            val sel = n <= value
            Surface(
                modifier = Modifier.clickable { onChange(if (value == n) 0 else n) },
                shape = RoundedCornerShape(8.dp),
                color = if (sel) textColor.copy(alpha = 0.2f) else Color.Transparent,
            ) {
                Text(
                    "$n",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (sel) textColor else textColor.copy(alpha = 0.3f),
                    fontSize = 16.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
