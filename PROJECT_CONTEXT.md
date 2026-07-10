# AlarmAI System and Developer Context

This document provides a comprehensive overview of the AlarmAI architecture, core components, and developer guidelines. It serves as a persistent reference for understanding the project structure and constraints.

## 1. System Overview
AlarmAI is a voice-powered morning assistant alarm application for Android, built using Kotlin and Jetpack Compose (MVVM). It replaces standard alarms with a conversational speech loop powered by Gemini 1.5 Flash to deliver a personalized morning briefing.

Upon dismissing the alarm, the app aggregates:
- **Local Weather:** Coordinates from GPS, queried against Open-Meteo.
- **Personalized News:** Headlines fetched via NewsAPI or Google News RSS.
- **Calendar Agendas:** Today's schedule read from the Android system calendar.
- **AI Generation & TTS:** Aggregated data is sent to Gemini to generate a cohesive briefing read aloud via Android's Text-to-Speech (TTS) engine.
- **Interactive Voice Dialogue:** Speech-to-Text (STT) captures follow-up questions for a hands-free conversation.

## 2. Architecture and Component Reference

### 2.1 Presentation Layer (UI and ViewModels)
- **MainActivity & MainViewModel:** Coordinate configuration UI, credentials, runtime permissions (Calendar, Location, Audio, Notifications), and alarm scheduling.
- **AlarmActivity & AlarmViewModel:** Coordinate the full-screen wake-up alarm interface over the lockscreen, TTS briefing playback, and user STT dialogue states (IDLE, RINGING, SPEAKING, LISTENING, THINKING, ERROR).
- **ui/theme:** Defines the dark-mode color scheme, custom typography, styling, and glassmorphic layouts.

### 2.2 Background Services and Receivers
- **AlarmScheduler & AlarmTimeCalculator:** Wraps Android's `AlarmManager` to schedule exact alarms.
- **AlarmReceiver:** BroadcastReceiver that handles alarm wake-up events, starts the playback service, and launches `AlarmActivity`.
- **PreAlarmReceiver:** BroadcastReceiver scheduled before the main alarm to trigger the data pre-fetching worker.
- **AlarmService:** Foreground Service (`mediaPlayback` type) managing alarm ringtone playback via `MediaPlayer`. Routes audio through the alarm stream.
- **PrefetchWorker:** CoroutineWorker managed by `WorkManager` that asynchronously fetches weather, calendar, news, and requests the Gemini morning briefing prior to wake-up.
- **BootReceiver:** Direct-boot aware BroadcastReceiver that listens for `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED` to reschedule active alarms upon device reboot.

### 2.3 Data and Infrastructure Layer
- **PreferencesManager:** Handles `SharedPreferences` persistence for alarm parameters, API keys, news topics, location cache, and settings.
- **LocationProvider:** Fetches device coordinates using `FusedLocationProviderClient`.
- **Repositories:** `CalendarRepository`, `WeatherRepository`, and `NewsRepository` fetch data from the system and external APIs.
- **VoiceManager:** Wraps `TextToSpeech` and `SpeechRecognizer`, exposing clean interfaces for voice synthesis and voice recognition.
- **GeminiAgentManager:** Manages Gemini API communication via OkHttp JSON requests to the `generativelanguage.googleapis.com` endpoint. Configures system instructions, prompt payloads, custom tools (such as tone preferences, news searches), and enforces fallback demo modes if API keys are missing.

## 3. Developer Guidelines & Constraints

### 3.1 Background Execution & Direct Boot Rules
Due to strict Android OS energy saving policies and system security states, follow these requirements:
- **Direct Boot (Reboot Recovery):** After a device reboots, it enters a Direct Boot (locked) state before the user enters their credentials. During this state, standard credential-encrypted SharedPreferences are inaccessible.
  - **Rule 1:** Any BroadcastReceiver triggering at boot must be declared with `android:directBootAware="true"` in `AndroidManifest.xml`.
  - **Rule 2:** `PreferencesManager` must utilize `context.createDeviceProtectedStorageContext()` if the context is not already in device-protected storage. This allows reading and rescheduling active alarms while the screen is locked.
- **Waking & Scheduling:**
  - **Rule 1:** Alarms must be scheduled using `AlarmManager.setExactAndAllowWhileIdle()` for maximum timing precision.
  - **Rule 2:** For Android 12+ (API 31+), check `alarmManager.canScheduleExactAlarms()` before calling exact alarm APIs, falling back to `setAndAllowWhileIdle()` if the permission is missing.
- **Data Pre-fetching (Doze Mode):**
  - **Rule 1:** Do not execute network requests or long-running tasks directly inside BroadcastReceivers (like `PreAlarmReceiver`) because they are subject to strict 10-second timeouts that cause ANRs.
  - **Rule 2:** Always delegate background fetching to `WorkManager` using `PrefetchWorker`. This guarantees execution and bypasses background network blocks during Doze mode sleep states.

### 3.2 Generative AI (Gemini) & Text-To-Speech (TTS) Rules
Since Gemini outputs are read aloud by the TTS engine, adhere to these guidelines:
- **Constraint 1:** Restrict responses to under 120 words to keep interaction snappy.
- **Constraint 2:** Never output markdown format (no headers, no asterisks, no bullet points, no emojis). Raw text ensures the TTS engine does not read formatting characters.
- **Constraint 3:** End every assistant reply with a friendly open-ended question to continue the speech loop.
- **Localization:** Generate system instructions and cached prompts in the language matching the user's configuration (`es` for Spanish, `en` for English). Keep prompts consistent in memory to avoid mixed-language context logs.
- **Google Search Grounding:** The Google Search grounding tool must use the standard camelCase key `"googleSearch": {}` inside the JSON tools array to ensure API compatibility.
