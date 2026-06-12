# AlarmAI System Context and Architecture Cache

This document outlines the architectural design, core components, and data flows of AlarmAI. It serves as a persistent context cache for developer agents.

---

## General Overview
AlarmAI is a voice-powered morning assistant alarm application for Android, built using Kotlin and Jetpack Compose. It replaces standard alarms with a conversational speech loop powered by Gemini 1.5 Flash to deliver a personalized morning briefing.

Upon dismissing the alarm, the app aggregates:
1. Local Weather: Coordinates from GPS, queried against Open-Meteo.
2. Personalized News: Headlines fetched via NewsAPI or Google News RSS.
3. Calendar Agendas: Today's schedule read from the Android system calendar.
4. AI Generation and TTS: Aggregated data is sent to Gemini to generate a cohesive briefing read aloud via Android's Text-to-Speech (TTS) engine.
5. Interactive Voice Dialogue: Speech-to-Text (STT) captures follow-up questions for a hands-free conversation.

---

## Architecture and Component Reference

AlarmAI is structured using the Model-View-ViewModel (MVVM) design pattern.

### 1. Presentation Layer (UI and ViewModels)
* **MainActivity**: Handles setup configurations, API credentials, settings, permission requests (Calendar, Location, Audio, Notifications), and alarm scheduling.
* **MainViewModel**: Manages settings state, loads and saves user preferences, and schedules alarm triggers.
* **AlarmActivity**: Full-screen wake-up interface displaying over the lockscreen when the alarm fires. Manages the "Dismiss and Talk" interaction and the briefing conversation.
* **AlarmViewModel**: Manages the alarm activity state machine (IDLE, RINGING, SPEAKING, LISTENING, THINKING, ERROR). Coordinates speech loop states.
* **ui/theme**: Defines the dark-mode color scheme, custom typography, styling, and glassmorphic layouts.

### 2. Background Services and Receivers
* **AlarmScheduler**: Wraps Android's AlarmManager to schedule exact alarms using setExactAndAllowWhileIdle.
* **AlarmReceiver**: BroadcastReceiver that handles alarm wake-up events, starts the playback service, and launches the alarm activity.
* **AlarmService**: Foreground Service (mediaPlayback type) managing alarm ringtone playback via MediaPlayer.
* **PreAlarmReceiver**: BroadcastReceiver scheduled 2 minutes before the main alarm to trigger the data pre-fetching worker.
* **PrefetchWorker**: CoroutineWorker managed by WorkManager that asynchronously fetches weather, calendar, and news data, and requests the Gemini morning briefing prior to wake-up.
* **BootReceiver**: Direct-boot aware BroadcastReceiver that listens for BOOT_COMPLETED and LOCKED_BOOT_COMPLETED to reschedule active alarms upon device reboot.

### 3. Data and Infrastructure Layer
* **PreferencesManager**: Handles SharedPreferences persistence for alarm parameters, API keys, news topics, location cache, and settings. Automatically switches to device-protected storage during direct-boot states.
* **Alarm**: Data model representing alarm times and repeating weekdays.
* **LocationProvider**: Fetches device coordinates using FusedLocationProviderClient.
* **CalendarRepository**: Queries the Android CalendarContract ContentResolver for today's user events.
* **WeatherRepository**: Queries the Open-Meteo REST API for weather forecasts.
* **NewsRepository**: Fetches current headlines via Google News RSS parser or NewsAPI.
* **VoiceManager**: Wraps TextToSpeech and SpeechRecognizer, exposing clean interfaces for voice synthesis and voice recognition.
* **GeminiAgentManager**: Manages Gemini API communication. Configures system instructions, prompt payloads, custom tools (such as tone preferences, World Cup fixture lookups, news searches), and enforces fallback demo modes if API keys are missing.

---

## Developer Gotchas
* **Direct Boot**: Since reboots can occur overnight, all boot receivers must be direct-boot aware, and database/SharedPreferences access must use device-protected storage contexts.
* **Doze Mode Restrictions**: Running network requests directly in BroadcastReceivers is prohibited due to system network restrictions and 10-second timeouts. All background operations must run in expedited WorkManager tasks.
* **TTS Output Guidelines**: Speech text sent to TTS must be under 120 words, free of markdown styling characters, and end with a question.
