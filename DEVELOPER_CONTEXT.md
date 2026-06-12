# AlarmAI Developer Context

This document provides technical instructions for agents and developers modifying the AlarmAI codebase. It defines architectural standards, background execution requirements, and generative AI constraints.

---

## 1. System Architecture

AlarmAI is built using the Model-View-ViewModel (MVVM) design pattern and Jetpack Compose.

* **MainActivity / MainViewModel**: Coordinates configuration UI, credentials, runtime permissions, and alarm scheduling.
* **AlarmActivity / AlarmViewModel**: Coordinates the full-screen wake-up alarm interface, TTS briefing playback, and user STT dialogue states (IDLE, RINGING, SPEAKING, LISTENING, THINKING, ERROR).
* **AlarmService**: A Foreground Service (mediaPlayback type) managing alarm ringtone playback via MediaPlayer. It routes audio through the alarm stream.
* **PreferencesManager**: Accesses SharedPreferences for settings, cached data, and credentials.
* **PrefetchWorker**: A CoroutineWorker managing background weather, calendar, news, and World Cup pre-fetching.

---

## 2. Background Execution & Direct Boot Rules

Due to strict Android OS energy saving policies and system security states, follow these requirements:

### 2.1 Direct Boot (Reboot Recovery)
After a device reboots, it enters a Direct Boot (locked) state before the user enters their credentials. During this state, standard credential-encrypted SharedPreferences are inaccessible.
* **Rule 1**: Any BroadcastReceiver triggering at boot (like `BootReceiver` for action `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED`) must be declared with `android:directBootAware="true"` in `AndroidManifest.xml`.
* **Rule 2**: `PreferencesManager` must utilize `context.createDeviceProtectedStorageContext()` if the context is not already in device-protected storage. This allows reading and rescheduling active alarms while the screen is locked.

### 2.2 Waking & Scheduling
* **Rule 1**: Alarms must be scheduled using `AlarmManager.setExactAndAllowWhileIdle()` for maximum timing precision.
* **Rule 2**: For Android 12+ (API 31+), check `alarmManager.canScheduleExactAlarms()` before calling exact alarm APIs, falling back to `setAndAllowWhileIdle()` if the permission is missing.

### 2.3 Data Pre-fetching
* **Rule 1**: Do not execute network requests or long-running tasks directly inside BroadcastReceivers (like `PreAlarmReceiver`) because they are subject to strict 10-second timeouts that cause ANRs.
* **Rule 2**: Always delegate background fetching to `WorkManager` using `PrefetchWorker`. This guarantees execution and bypasses background network blocks during Doze mode sleep states.

---

## 3. Generative AI (Gemini) & Text-To-Speech (TTS) Rules

AlarmAI integrates the Gemini API directly via OkHttp JSON requests to the `generativelanguage.googleapis.com` endpoint.

### 3.1 Prompt Formatting for TTS
Since Gemini outputs are read aloud by the TTS engine, adhere to these guidelines:
* **Constraint 1**: Restrict responses to under 120 words to keep interaction snappy.
* **Constraint 2**: Never output markdown format (no headers, no asterisks, no bullet points, no emojis). Raw text ensures the TTS engine does not read formatting characters.
* **Constraint 3**: End every assistant reply with a friendly open-ended question to continue the speech loop.

### 3.2 Localization
* Generate system instructions and cached prompts in the language matching the user's configuration (`es` for Spanish, `en` for English). Keep prompts consistent in memory to avoid mixed-language context logs.

### 3.3 Google Search Grounding
* The Google Search grounding tool must use the standard camelCase key `"googleSearch": {}` inside the JSON tools array to ensure API compatibility.
