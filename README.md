# Breathe

Offline meditation timer for Android. Configure a session's length, then add any number of bells at chosen minute offsets. Each event has its own tone and strike count. Scroll the bell tone wheel to preview sounds, and tap Preview to hear the selected sound again.

**Audio note:** The bundled tones are seven struck singing bowls, a gong, and a low temple block. Their sources and CC0 licenses are recorded in `THIRD_PARTY_NOTICES.md`.

## Build and install (Windows / PowerShell 7)

Open this folder in Android Studio, allow Gradle to sync, select your USB-debugging-enabled Android phone, and click Run. Alternatively, after Android Studio has installed the SDK and Gradle:

```powershell
# If Gradle is installed and the wrapper has been generated:
./gradlew.bat assembleDebug
adb install -r ./app/build/outputs/apk/debug/app-debug.apk
```

This source archive currently does not bundle a Gradle wrapper executable. Android Studio can import the project directly, or you can use an installed Gradle 8.13 to generate the wrapper (`gradle wrapper --gradle-version 8.13`).

## Behavior

- A foreground service keeps the timer operating with the phone locked; the service takes a timed partial wake lock.
- While a session runs in the foreground, a dim black countdown screen stays visible over the lock screen.
- A persistent notification shows time remaining and offers a Stop action.
- Bell events can share the same minute. Hold an interval bell to delete it.
- Preferences are kept locally on the phone.
- Bell events may be placed at any minute-and-second countdown value, including start or finish. Strike spacing is 1.9 seconds.
- A session will not recover after an Android process kill or phone restart.

No cloud services or network permissions.
