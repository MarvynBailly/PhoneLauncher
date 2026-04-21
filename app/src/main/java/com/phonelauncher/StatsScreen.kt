package com.phonelauncher

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun StatsScreen(
    settings: LauncherSettings,
    resumeKey: Int,
    onDismiss: () -> Unit,
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val textColor = Color(settings.clockColor)
    val subtleColor = textColor.copy(alpha = 0.5f)

    var stats by remember { mutableStateOf<PhoneUsageStats?>(null) }
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    // Refresh whenever the activity resumes (handles returning from Settings)
    LaunchedEffect(resumeKey) {
        hasPermission = hasUsageStatsPermission(context)
        stats = if (hasPermission) loadUsageStats(context, settings.dayResetHour) else null
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
                modifier = Modifier.clickable { onDismiss() }.padding(8.dp),
                fontSize = 24.sp,
                color = textColor
            )
            Spacer(Modifier.width(8.dp))
            Text("Phone Stats", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)
        }

        Spacer(Modifier.height(32.dp))

        if (!hasPermission) {
            Text(
                "Usage access permission is required to show phone stats.",
                color = textColor, fontSize = 15.sp
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                shape = RoundedCornerShape(28.dp),
                color = textColor.copy(alpha = 0.15f)
            ) {
                Text(
                    "Grant Access",
                    modifier = Modifier.padding(16.dp),
                    color = textColor,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
            return@Column
        }

        val s = stats
        if (s == null) {
            Text("Loading...", color = subtleColor, fontSize = 14.sp)
            return@Column
        }

        SectionLabel("TODAY", subtleColor)
        Spacer(Modifier.height(12.dp))
        StatLine("Unlocks", s.unlockCount.toString(), textColor, subtleColor)
        Spacer(Modifier.height(8.dp))
        StatLine("Screen time", formatElapsed(s.totalScreenMs), textColor, subtleColor)

        Spacer(Modifier.height(32.dp))
        SectionLabel("APPS", subtleColor)
        Spacer(Modifier.height(12.dp))

        if (s.apps.isEmpty()) {
            Text("No app usage recorded.", color = subtleColor, fontSize = 14.sp)
        } else {
            s.apps.forEach { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(app.label, modifier = Modifier.weight(1f), color = textColor, fontSize = 15.sp)
                    Text(formatElapsed(app.totalMs), color = subtleColor, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatLine(label: String, value: String, textColor: Color, subtleColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), color = subtleColor, fontSize = 15.sp)
        Text(value, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Light)
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
