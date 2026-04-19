# PhoneLauncher

A minimal, productivity-focused Android launcher built with Kotlin + Jetpack Compose.

## Project Overview

This is a custom Android home screen launcher designed to remove phone distractions and streamline productivity. It replaces the default launcher and controls app access through a task/reward system.

## Architecture

Single-activity app (`MainActivity.kt`) with Compose-based screen navigation via a `Screen` enum: `HOME`, `SEARCH`, `SETTINGS`, `PLANNING`, `TASK_EDIT`.

### Key Files

- **MainActivity.kt** — Entry point, screen routing, all state management, home screen UI, search screen, and core composables (Clock, Temperature, TaskRow, PinnedAppItem, UnifiedAddRow, SearchBar, SearchScreen)
- **Settings.kt** — `LauncherSettings` data class (persisted as JSON in SharedPreferences), theme presets (5 built-in), color palette, font size controls, restricted apps management, emergency override, day reset hour, background image picker, and the full Settings screen UI
- **TaskData.kt** — `TaskTemplate`, `DayTask`, `RewardSession`, `DayState` models + JSON persistence + notification scheduling via AlarmManager + day reset logic (`getEffectiveDate()`)
- **TaskScreens.kt** — `DailyPlanningScreen` (shown on first open after reset hour) and `TaskEditScreen` (full editor with deadline, recurrence, reminder, reward app/time)
- **TimerData.kt** — `TimerEntry` (with `TimeSegment` list for timeline tracking), `QuickAction` models + JSON persistence + DND helpers
- **TimerUI.kt** — `TimerSection`, `TimerDetailDialog` (editable timeline of sessions), `NewTimerDialog`, `QuickActionRow`, `NewQuickActionDialog`
- **TimerService.kt** — Foreground service showing persistent notification with timer name + elapsed time + pause action button. Syncs with activity via broadcast (`TIMER_SYNC`)
- **ReminderReceiver.kt** — BroadcastReceiver for task deadline reminder notifications
- **ui/theme/Theme.kt** — Minimal Material3 theme wrapper (light/dark)

### Data Persistence

All data stored in SharedPreferences as JSON:
- `launcher_settings` prefs — `LauncherSettings` (colors, fonts, pinned apps, restricted apps, etc.)
- `launcher_tasks` prefs — task templates, day state (today's tasks), reward sessions
- `launcher_timers` prefs — active timers, timer history, quick actions

### Key Features

1. **Daily Planning** — On first open after reset hour (default 5 AM), shows planning screen with recurring tasks pre-populated. User adds tasks, then taps "Start Day".
2. **Task System** — Tasks with optional deadlines, reminder notifications (N min before), recurrence (daily/weekdays/weekly), and rewards (app access for N minutes).
3. **App Restriction** — Apps can be marked restricted in Settings. Restricted apps require completing a task with that app as reward. Emergency override toggle bypasses all restrictions.
4. **Timers** — Multiple simultaneous timers, sub-timers (nested under parent), DND mode per timer, session timeline with editable start/stop times. Same-name timer accumulates time across pause/resume in a day.
5. **Quick Actions (Counters)** — Tap-to-increment daily counters (e.g. "Water: 4"). Decrement with "-", remove with long-press.
6. **Unified "+" Button** — Single "+" on home screen expands to: task (inline quick-add or full editor), timer, counter.
7. **Customization** — 5 theme presets, individual color pickers (clock, temp, apps, background), font size sliders, background image.
8. **Weather** — Fetches temperature from Open-Meteo API using device location (coarse). Falls back to requesting fresh GPS fix if no cached location.
9. **Timer Notifications** — Foreground service shows persistent notification while timers run, with pause button. Syncs state via broadcast.

### Build & Deploy

- Target SDK 34, Min SDK 26
- AGP 8.2.2, Kotlin 1.9.22, Compose BOM 2024.02.00
- Build: `./gradlew assembleDebug`
- Install on emulator: `./gradlew installDebug`
- GitHub repo: `MarvynBailly/PhoneLauncher` (private)
- Release: `./gradlew assembleDebug --quiet; gh release create vX.Y app/build/outputs/apk/debug/app-debug.apk --title "Launcher vX.Y" --notes "Update"`

### Emulator Commands

```bash
$ANDROID_HOME/emulator/emulator -avd launcher_test &
$ANDROID_HOME/platform-tools/adb wait-for-device
$ANDROID_HOME/platform-tools/adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton
$ANDROID_HOME/platform-tools/adb emu geo fix -74.0060 40.7128
./gradlew installDebug
$ANDROID_HOME/platform-tools/adb shell cmd package set-home-activity com.phonelauncher/.MainActivity
$ANDROID_HOME/platform-tools/adb shell input keyevent KEYCODE_HOME
```

## Rules

- **No emojis or Unicode symbols** in code. Use plain ASCII text only (e.g. "x" not U+2715, ">" not U+25B6). Exception: degree symbol U+00B0 for temperature.
- Do not add Co-Authored-By lines to commits. User manages git directly.
- Keep the UI minimal and text-based. No app icons — names only.
