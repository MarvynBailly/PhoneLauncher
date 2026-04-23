package com.phonelauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

data class LauncherSettings(
    val pinnedApps: List<String> = emptyList(),
    val clockSize: Int = 64,
    val tempSize: Int = 20,
    val pinnedAppSize: Int = 18,
    val clockColor: Int = 0xFFFFFFFF.toInt(),
    val tempColor: Int = 0xB3FFFFFF.toInt(),
    val pinnedAppColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundColor: Int = 0xFF000000.toInt(),
    val backgroundImageUri: String? = null,
    val restrictedApps: List<String> = emptyList(),
    val emergencyOverride: Boolean = false,
    val dayResetHour: Int = 5,
    val useCelsius: Boolean = false,
    val closingFields: List<ClosingFieldDef> = emptyList(),
    val trackPhoneUsage: Boolean = false,
    val phoneUsageBreakdown: Boolean = true,
)

data class ThemePreset(
    val id: String,
    val name: String,
    val bg: Color,
    val clock: Color,
    val temp: Color,
    val app: Color,
)

val themePresets = listOf(
    ThemePreset("midnight", "Midnight",
        bg = Color.Black, clock = Color.White,
        temp = Color.White.copy(alpha = 0.7f), app = Color.White),
    ThemePreset("clean", "Clean",
        bg = Color(0xFFF5F5F5), clock = Color(0xFF212121),
        temp = Color(0xFF757575), app = Color(0xFF424242)),
    ThemePreset("ocean", "Ocean",
        bg = Color(0xFF0D1B2A), clock = Color(0xFFE0E1DD),
        temp = Color(0xFF778DA9), app = Color(0xFFE0E1DD)),
    ThemePreset("sunset", "Sunset",
        bg = Color(0xFF1A0A00), clock = Color(0xFFFF6B35),
        temp = Color(0xFFD4A276), app = Color(0xFFFFB563)),
    ThemePreset("forest", "Forest",
        bg = Color(0xFF0B1D0B), clock = Color(0xFFA7C957),
        temp = Color(0xFF6A994E), app = Color(0xFFC5DCA0)),
)

val colorPalette = listOf(
    Color.White, Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF616161), Color(0xFF212121), Color.Black, Color(0xFF263238),
    Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4),
    Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800),
)

// Persistence

fun loadSettings(context: Context): LauncherSettings {
    val json = context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)
        .getString("settings", null) ?: return LauncherSettings()
    return try {
        val j = JSONObject(json)
        val defaults = LauncherSettings()
        LauncherSettings(
            pinnedApps = j.optJSONArray("pinnedApps")?.let { a ->
                (0 until a.length()).map { a.getString(it) }
            } ?: defaults.pinnedApps,
            clockSize = j.optInt("clockSize", defaults.clockSize),
            tempSize = j.optInt("tempSize", defaults.tempSize),
            pinnedAppSize = j.optInt("pinnedAppSize", defaults.pinnedAppSize),
            clockColor = j.optInt("clockColor", defaults.clockColor),
            tempColor = j.optInt("tempColor", defaults.tempColor),
            pinnedAppColor = j.optInt("pinnedAppColor", defaults.pinnedAppColor),
            backgroundColor = j.optInt("backgroundColor", defaults.backgroundColor),
            backgroundImageUri = j.optString("backgroundImageUri", "").ifEmpty { null },
            restrictedApps = j.optJSONArray("restrictedApps")?.let { a ->
                (0 until a.length()).map { a.getString(it) }
            } ?: defaults.restrictedApps,
            emergencyOverride = j.optBoolean("emergencyOverride", defaults.emergencyOverride),
            dayResetHour = j.optInt("dayResetHour", defaults.dayResetHour),
            useCelsius = j.optBoolean("useCelsius", defaults.useCelsius),
            closingFields = j.optJSONArray("closingFields")?.let { a ->
                (0 until a.length()).map {
                    val f = a.getJSONObject(it)
                    ClosingFieldDef(f.getString("id"), f.getString("label"), f.getString("type"))
                }
            } ?: defaults.closingFields,
            trackPhoneUsage = j.optBoolean("trackPhoneUsage", defaults.trackPhoneUsage),
            phoneUsageBreakdown = j.optBoolean("phoneUsageBreakdown", defaults.phoneUsageBreakdown),
        )
    } catch (_: Exception) {
        LauncherSettings()
    }
}

fun saveSettings(context: Context, settings: LauncherSettings) {
    val json = JSONObject().apply {
        put("pinnedApps", JSONArray(settings.pinnedApps))
        put("clockSize", settings.clockSize)
        put("tempSize", settings.tempSize)
        put("pinnedAppSize", settings.pinnedAppSize)
        put("clockColor", settings.clockColor)
        put("tempColor", settings.tempColor)
        put("pinnedAppColor", settings.pinnedAppColor)
        put("backgroundColor", settings.backgroundColor)
        put("backgroundImageUri", settings.backgroundImageUri ?: "")
        put("restrictedApps", JSONArray(settings.restrictedApps))
        put("emergencyOverride", settings.emergencyOverride)
        put("dayResetHour", settings.dayResetHour)
        put("useCelsius", settings.useCelsius)
        put("closingFields", JSONArray().apply {
            settings.closingFields.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("label", it.label); put("type", it.type)
                })
            }
        })
        put("trackPhoneUsage", settings.trackPhoneUsage)
        put("phoneUsageBreakdown", settings.phoneUsageBreakdown)
    }
    context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)
        .edit().putString("settings", json.toString()).apply()
}

// Settings Screen

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    allApps: List<AppInfo>,
    onSettingsChanged: (LauncherSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showAddApps by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            onSettingsChanged(settings.copy(backgroundImageUri = uri.toString()))
        }
    }

    BackHandler { onDismiss() }

    val textColor = Color(settings.clockColor)
    val subtleColor = textColor.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(settings.backgroundColor))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
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
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)
        }

        Spacer(Modifier.height(32.dp))

        // Themes
        SectionLabel("THEMES", subtleColor)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            themePresets.forEach { preset ->
                ThemePresetCard(preset, settings, onSettingsChanged)
            }
        }

        Spacer(Modifier.height(32.dp))

        // Pinned Apps
        SectionLabel("PINNED APPS", subtleColor)
        Spacer(Modifier.height(12.dp))
        settings.pinnedApps.forEachIndexed { index, pkg ->
            val app = allApps.find { it.packageName == pkg }
            if (app != null) {
                PinnedAppSettingsRow(
                    appName = app.label,
                    canMoveUp = index > 0,
                    canMoveDown = index < settings.pinnedApps.size - 1,
                    textColor = Color(settings.pinnedAppColor),
                    onMoveUp = {
                        val list = settings.pinnedApps.toMutableList()
                        val item = list.removeAt(index)
                        list.add(index - 1, item)
                        onSettingsChanged(settings.copy(pinnedApps = list))
                    },
                    onMoveDown = {
                        val list = settings.pinnedApps.toMutableList()
                        val item = list.removeAt(index)
                        list.add(index + 1, item)
                        onSettingsChanged(settings.copy(pinnedApps = list))
                    },
                    onRemove = {
                        onSettingsChanged(settings.copy(pinnedApps = settings.pinnedApps - pkg))
                    }
                )
            }
        }

        Text(
            text = if (showAddApps) "- Hide apps" else "+ Add app",
            color = textColor,
            fontSize = 14.sp,
            modifier = Modifier
                .clickable { showAddApps = !showAddApps }
                .padding(vertical = 8.dp)
        )

        if (showAddApps) {
            val available = allApps.filter { it.packageName !in settings.pinnedApps }
            available.forEach { app ->
                Text(
                    text = app.label,
                    color = Color(settings.pinnedAppColor).copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSettingsChanged(
                                settings.copy(pinnedApps = settings.pinnedApps + app.packageName)
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // Font Sizes
        SectionLabel("FONT SIZES", subtleColor)
        Spacer(Modifier.height(12.dp))
        SizeSlider("Clock", settings.clockSize, 32, 96, textColor) {
            onSettingsChanged(settings.copy(clockSize = it))
        }
        SizeSlider("Temperature", settings.tempSize, 12, 48, textColor) {
            onSettingsChanged(settings.copy(tempSize = it))
        }
        SizeSlider("App names", settings.pinnedAppSize, 12, 36, textColor) {
            onSettingsChanged(settings.copy(pinnedAppSize = it))
        }

        Spacer(Modifier.height(32.dp))

        // Colors
        SectionLabel("COLORS", subtleColor)
        Spacer(Modifier.height(12.dp))
        ColorPicker("Clock", settings.clockColor, subtleColor) {
            onSettingsChanged(settings.copy(clockColor = it))
        }
        ColorPicker("Temperature", settings.tempColor, subtleColor) {
            onSettingsChanged(settings.copy(tempColor = it))
        }
        ColorPicker("App names", settings.pinnedAppColor, subtleColor) {
            onSettingsChanged(settings.copy(pinnedAppColor = it))
        }
        ColorPicker("Background", settings.backgroundColor, subtleColor) {
            onSettingsChanged(settings.copy(backgroundColor = it))
        }

        Spacer(Modifier.height(32.dp))

        // Background Image
        SectionLabel("BACKGROUND IMAGE", subtleColor)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                Text("Choose Image", color = textColor)
            }
            if (settings.backgroundImageUri != null) {
                OutlinedButton(onClick = {
                    onSettingsChanged(settings.copy(backgroundImageUri = null))
                }) {
                    Text("Remove", color = Color(0xFFFF5252))
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Productivity
        SectionLabel("PRODUCTIVITY", subtleColor)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Emergency Override", color = textColor, fontSize = 15.sp)
                Text(
                    "Bypass all app restrictions",
                    color = subtleColor,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = settings.emergencyOverride,
                onCheckedChange = { onSettingsChanged(settings.copy(emergencyOverride = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF5252))
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Temperature Unit", color = textColor, fontSize = 15.sp)
                Text(
                    if (settings.useCelsius) "Celsius" else "Fahrenheit",
                    color = subtleColor,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = settings.useCelsius,
                onCheckedChange = { onSettingsChanged(settings.copy(useCelsius = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = textColor.copy(alpha = 0.3f))
            )
        }

        Spacer(Modifier.height(16.dp))

        SizeSlider("Day resets at", settings.dayResetHour, 1, 8, textColor) {
            onSettingsChanged(settings.copy(dayResetHour = it))
        }
        Text(
            "Tasks reset after ${settings.dayResetHour}:00 AM",
            color = subtleColor,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Track phone usage", color = textColor, fontSize = 15.sp)
                Text(
                    "Auto-pause task timer and record per-app usage while away",
                    color = subtleColor,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = settings.trackPhoneUsage,
                onCheckedChange = { onSettingsChanged(settings.copy(trackPhoneUsage = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = textColor.copy(alpha = 0.3f))
            )
        }

        if (settings.trackPhoneUsage && !hasUsageStatsPermission(context)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Usage access permission required. Tap to grant.",
                color = Color(0xFFFF5252),
                fontSize = 12.sp,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
        }

        if (settings.trackPhoneUsage) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Break down by app", color = textColor, fontSize = 15.sp)
                    Text(
                        "Show per-app sub-timers under Phone usage",
                        color = subtleColor,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = settings.phoneUsageBreakdown,
                    onCheckedChange = { onSettingsChanged(settings.copy(phoneUsageBreakdown = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = textColor.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // Restricted Apps
        SectionLabel("RESTRICTED APPS", subtleColor)
        Spacer(Modifier.height(4.dp))
        Text(
            "Restricted apps require completing a task to unlock",
            color = subtleColor,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        allApps.forEach { app ->
            val isRestricted = app.packageName in settings.restrictedApps
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val updated = if (isRestricted)
                            settings.restrictedApps - app.packageName
                        else
                            settings.restrictedApps + app.packageName
                        onSettingsChanged(settings.copy(restrictedApps = updated))
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    app.label,
                    modifier = Modifier.weight(1f),
                    color = if (isRestricted) textColor else textColor.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
                Switch(
                    checked = isRestricted,
                    onCheckedChange = {
                        val updated = if (it)
                            settings.restrictedApps + app.packageName
                        else
                            settings.restrictedApps - app.packageName
                        onSettingsChanged(settings.copy(restrictedApps = updated))
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = textColor.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 2.sp
    )
}

@Composable
private fun ThemePresetCard(
    preset: ThemePreset,
    settings: LauncherSettings,
    onSettingsChanged: (LauncherSettings) -> Unit
) {
    val isSelected = settings.backgroundColor == preset.bg.toArgb() &&
            settings.clockColor == preset.clock.toArgb()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable {
            onSettingsChanged(
                settings.copy(
                    backgroundColor = preset.bg.toArgb(),
                    clockColor = preset.clock.toArgb(),
                    tempColor = preset.temp.toArgb(),
                    pinnedAppColor = preset.app.toArgb(),
                )
            )
        }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(preset.bg)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) preset.clock else Color.Gray.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("Aa", color = preset.clock, fontSize = 16.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            preset.name,
            fontSize = 12.sp,
            color = Color(settings.clockColor).copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun PinnedAppSettingsRow(
    appName: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    textColor: Color,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(appName, modifier = Modifier.weight(1f), color = textColor, fontSize = 15.sp)
        Text(
            "up",
            modifier = Modifier
                .clickable(enabled = canMoveUp) { onMoveUp() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            color = if (canMoveUp) textColor else textColor.copy(alpha = 0.2f),
            fontSize = 18.sp
        )
        Text(
            "dn",
            modifier = Modifier
                .clickable(enabled = canMoveDown) { onMoveDown() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            color = if (canMoveDown) textColor else textColor.copy(alpha = 0.2f),
            fontSize = 18.sp
        )
        Text(
            "x",
            modifier = Modifier
                .clickable { onRemove() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            color = Color(0xFFFF5252),
            fontSize = 16.sp
        )
    }
}

@Composable
private fun SizeSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    textColor: Color,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$label: $value",
            modifier = Modifier.width(130.dp),
            color = textColor.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = textColor,
                activeTrackColor = textColor.copy(alpha = 0.5f),
                inactiveTrackColor = textColor.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
private fun ColorPicker(
    label: String,
    selectedArgb: Int,
    labelColor: Color,
    onSelected: (Int) -> Unit
) {
    Text(label, fontSize = 14.sp, color = labelColor)
    Spacer(Modifier.height(6.dp))
    val rows = colorPalette.chunked(7)
    rows.forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            row.forEach { color ->
                val argb = color.toArgb()
                val isSelected = argb == selectedArgb
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) Modifier.border(2.5.dp, Color(0xFF448AFF), CircleShape)
                            else Modifier.border(0.5.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                        )
                        .clickable { onSelected(argb) }
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}
