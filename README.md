# PhoneLauncher

A minimal, text-only Android home screen built to make your phone a tool instead of a distraction.

No icons, no feeds, no badges. Just a clock, your tasks for today, the timers you are running, and the handful of apps you actually need. Distracting apps stay locked until you have earned them by finishing a task.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
[![CI Build](https://github.com/MarvynBailly/PhoneLauncher/actions/workflows/release.yml/badge.svg)](https://github.com/MarvynBailly/PhoneLauncher/actions/workflows/release.yml)
[![Latest release](https://img.shields.io/github/v/release/MarvynBailly/PhoneLauncher)](https://github.com/MarvynBailly/PhoneLauncher/releases/latest)

---

## How a day works

1. **Review** - The first time you unlock after your reset hour (5 AM by default), a **Day Review** screen closes out yesterday: tracked time, phone usage, why unfinished tasks were not done, and which ones to carry forward. You can also open it yourself before bed.
2. **Plan** - Next comes the planning screen, with your recurring tasks and carried-over tasks already filled in. Add anything else for today, then tap **Start Day**.
3. **Work** - The home screen shows today's tasks and any running timers. Start a timer when you begin something; the launcher nudges you once an hour if nothing is being tracked.
4. **Earn** - Restricted apps stay locked. Completing a task can unlock a chosen app for a set number of minutes.

## Features

### Tasks
- Optional deadlines with reminder notifications N minutes before
- Recurrence: daily, weekdays, or weekly
- Rewards: completing a task unlocks a specific app for N minutes
- Quick inline add from the home screen, or a full editor for details

### App restriction
- Mark any app as restricted in Settings; it opens only during an earned reward session
- An **emergency override** toggle bypasses every restriction when you really need it

### Timers
- Run several timers at once, and nest sub-timers under a parent
- Optional **Do Not Disturb** per timer, switched on while it runs
- Restarting a timer with the same name continues its total for the day
- Session timeline for each timer, with editable start and end times
- Persistent notification with the elapsed time and a pause button
- Hourly "No timer running" prompt during the day

### Phone usage tracking (optional)
- Fills in a **Phone usage** timer with the time you spent in other apps, with an optional per-app breakdown
- A Stats screen and a usage line on the home screen
- Requires the Usage Access permission

### Counters
- Tap-to-increment daily counters such as "Water: 4" or "Pushups: 30"; reset every day

### Home screen
- Large clock and current temperature (Open-Meteo, coarse location, C/F toggle)
- Pinned apps shown as names only
- Swipe up to search all apps
- Swipe left or right to open a configured app or URL
- One **+** button for adding a task, timer, or counter

### Customization
- 5 built-in theme presets plus individual colors for the clock, temperature, apps, and background
- Font size sliders and an optional background image
- Configurable day reset hour

### Pi goal sync (optional)
- Point the launcher at a self-hosted API (URL + token in Settings). On a new day it pulls today's goals as tasks, and after planning it pushes the final task list back.

## Install

### With Obtainium (recommended, gets updates automatically)
1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. **Add app**, source URL: `https://github.com/MarvynBailly/PhoneLauncher`
3. Install, then set PhoneLauncher as your default home app when prompted.

### Manually
Download `app-debug.apk` from the [latest release](https://github.com/MarvynBailly/PhoneLauncher/releases/latest) and install it. You may need to allow installs from unknown sources.

### Permissions

| Permission | Used for |
|---|---|
| Notifications | Task reminders, timer notification, hourly timer prompt |
| Do Not Disturb access | Per-timer DND mode |
| Usage access | Phone usage tracking and Stats (optional) |
| Approximate location | Temperature on the home screen |
| Query all packages | Listing and launching installed apps |

## Build from source

Requirements: JDK 17 and the Android SDK (compile/target SDK 34, min SDK 26).

```bash
git clone https://github.com/MarvynBailly/PhoneLauncher.git
cd PhoneLauncher
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew installDebug         # install on a connected device or emulator
```

Stack: Kotlin 1.9, Jetpack Compose (Material 3), AGP 8.2. Everything is stored locally in SharedPreferences as JSON; there is no account and no backend (except the optional Pi sync).

### Project layout

```
app/src/main/java/com/phonelauncher/
  MainActivity.kt        entry point, screen routing, home + search screens
  Settings.kt            settings model, themes, settings screen
  TaskData.kt            tasks, day state, reminders, day reset
  TaskScreens.kt         daily planning + task editor
  TimerData.kt           timer model, segments, persistence, DND
  TimerUI.kt             timer list, timeline dialog, new timer / counter dialogs
  TimerService.kt        foreground service for the timer notification
  TimerPromptReceiver.kt hourly "no timer running" prompt
  PhoneUsageTracker.kt   phone-usage back-fill and away-time handling
  UsageStats.kt          usage stats queries
  StatsScreen.kt         phone stats screen
  ClosingScreen.kt       end-of-day review
  ReminderReceiver.kt    task reminder notifications
```

## Releasing

Releases are cut locally so the APK is always signed with the same key, which Android requires for in-place updates:

```bash
./release.sh v2.1      # or omit the version for a date-based tag
```

The script makes a release commit, builds with `versionName` set from the tag, and publishes a GitHub release with the APK. `versionCode` is the git commit count, so every release is a strict upgrade. CI only checks that the project builds; it does not publish APKs.

## Design principles

- **Text over icons.** App names only, no colorful grid pulling at your attention.
- **Friction where it helps.** Distracting apps take a deliberate step to open.
- **Review, plan, track.** Every day starts by closing out yesterday and setting intent for today.
