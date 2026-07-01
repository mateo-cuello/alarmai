# AlarmAI

AlarmAI is a native Android morning assistant application that replaces standard waking alarms with interactive voice briefings.

When the alarm fires, it launches a full-screen window. Upon dismissal, the app aggregates weather coordinates, curated news headlines, and calendar events, and sends them to Gemini 2.5 Flash to generate a briefing. The briefing is read aloud using Android's Text-to-Speech (TTS) engine, and the app listens for spoken follow-up responses using Speech-to-Text (STT) for hands-free dialogue.

---

## Key Features

* **Exact Alarm Scheduling**: Schedules waking times via AlarmManager, supporting exact timing even under Android Doze mode.
* **Full-Screen Interaction**: Wakes up and displays over the phone lockscreen.
* **Information Aggregation**: Gathers data from Open-Meteo, Gemini Google Search for news, and system calendar.
* **Expedited Pre-fetching**: Leverages WorkManager to pre-generate AI responses 2 minutes before the alarm triggers, avoiding ANR timeouts and bypassing Doze mode network constraints.
* **Conversational Voice Loop**: Speaks briefings via TTS and opens the microphone for user follow-up questions.
* **Direct Boot Recovery**: Restores scheduled alarms automatically after overnight device updates/reboots before the screen is unlocked.
* **Demo Mode Fallback**: Operates using simulated local data if Gemini or News API credentials are not set.

---

## Technical Architecture

* **Frameworks**: Kotlin, Jetpack Compose (Material 3 with custom glassmorphic rendering), MVVM Architecture.
* **APIs & Data**: OkHttp direct HTTP requests for Gemini, Retrofit for weather, Google News RSS parser, and Android Calendar Provider ContentResolver.
* **Services & Receivers**: Foreground Services (mediaPlayback type) for audio playback, WorkManager for pre-fetching, and direct-boot aware receivers for reboot recovery.

---

## Setup & Running in Android Studio

### 1. Import the Project
1. Open Android Studio.
2. Select **File > Open** and open `C:\Users\usuario\alarmai`.
3. Wait for the Gradle sync to complete.

### 2. Configure Settings & Run
1. Connect an Android device or emulator running API 26 or higher.
2. Click **Run** in the toolbar.
3. Grant permissions on startup (Calendar, Location, Microphone, Notifications).
4. Enter Gemini and News API credentials in the settings screen. If left blank, the app runs in demo mode.

### 3. Immediate Testing
1. Tap the **Test Alarm (Fires in 5 Seconds)** button in the settings UI.
2. Put the app in the background or lock the screen.
3. Once the alarm rings, tap **Dismiss & Talk** to test the speech loop. Say *"Goodbye"* or *"Exit"* to close the session.
