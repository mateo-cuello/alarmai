# AlarmAI

AlarmAI is a native Android morning assistant application that replaces standard waking alarms with interactive voice briefings.

When the alarm fires, it launches a full-screen window. Upon dismissal, the app aggregates weather, news headlines, and calendar events, and sends them to Gemini to generate a briefing. The briefing is read aloud using Android's Text-to-Speech (TTS) engine, and the app listens for spoken follow-up responses using Speech-to-Text (STT) for hands-free dialogue.

The default model is `gemini-3.6-flash`, selectable in settings. If the chosen model is rate-limited or unavailable, the app automatically falls back down a chain of alternatives rather than failing the briefing.

---

## Key Features

* **Exact Alarm Scheduling**: Schedules waking times via AlarmManager, supporting exact timing even under Android Doze mode.
* **Full-Screen Interaction**: Wakes up and displays over the phone lockscreen.
* **Information Aggregation**: Gathers weather from Open-Meteo, headlines from the Google News RSS feed, and events from the system calendar.
* **Expedited Pre-fetching**: Leverages WorkManager to pre-generate AI responses 2 minutes before the alarm triggers, avoiding ANR timeouts and bypassing Doze mode network constraints.
* **Conversational Voice Loop**: Speaks briefings via TTS and opens the microphone for user follow-up questions.
* **Direct Boot Recovery**: Restores scheduled alarms automatically after overnight device updates/reboots before the screen is unlocked.
* **Demo Mode Fallback**: Operates using simulated local data if no Gemini API key is set.

---

## Technical Architecture

* **Frameworks**: Kotlin, Jetpack Compose (Material 3 with custom glassmorphic rendering), MVVM Architecture.
* **APIs & Data**: OkHttp direct HTTP requests for Gemini, Retrofit for weather, Google News RSS parser, and Android Calendar Provider ContentResolver.
* **Services & Receivers**: A `specialUse` foreground service (subtype `alarm`) for ringtone playback, WorkManager for pre-fetching, and direct-boot aware receivers for reboot recovery.

---

## Setup & Running in Android Studio

### 1. Import the Project
1. Open Android Studio.
2. Select **File > Open** and choose the cloned `alarmai` directory.
3. Wait for the Gradle sync to complete. The build requires JDK 21.

### 2. Configure Settings & Run
1. Connect an Android device or emulator running API 26 or higher.
2. Click **Run** in the toolbar.
3. Grant permissions on startup (Calendar, Location, Microphone, Notifications).
4. Enter your Gemini API key in the settings screen. If left blank, the app runs in demo mode with a canned briefing.
5. Optionally pick a different model under **AI Model**, and set your news topics — no news API key is needed.

### 3. Immediate Testing
1. Tap the **Test Alarm (Fires in 5 Seconds)** button in the settings UI. This rings without disturbing your real scheduled alarm.
2. Put the app in the background or lock the screen.
3. Once the alarm rings, tap **Dismiss & Talk** to test the speech loop. Say *"Goodbye"* or *"Exit"* to close the session.
