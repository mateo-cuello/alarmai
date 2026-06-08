# AlarmAI ⏰🤖

**AlarmAI** is a premium, voice-powered morning assistant alarm application for Android. Built natively using **Kotlin** and **Jetpack Compose**, it turns standard waking alarms into interactive conversational sessions. 

When the alarm fires, it launches a full-screen wake-up window. Once dismissed, the app retrieves real-time **weather coordinates**, scrapes **curated news headlines** according to your interests, reads **today's system calendar events**, and streams a unified morning briefing through the **Gemini 1.5 Flash** model. It speaks the briefing using Android's native Text-to-Speech (TTS) engine and listens to your spoken responses using Android's native SpeechRecognizer (STT) for a complete back-and-forth hands-free voice dialogue.

---

## 🌟 Key Features

- **Exact Alarm Scheduling**: Uses `AlarmManager` with `setExactAndAllowWhileIdle()` to guarantee wake-up triggers even under Android Doze mode.
- **Full-Screen Alarm Intent**: Rings and displays a beautiful wake-up screen over the phone lockscreen.
- **Multimodal AI Orchestrator**: Collects local weather (Open-Meteo), personalized news headlines (NewsAPI/GNews), and calendar agendas (Google Calendar), feeding them directly to Gemini.
- **Interactive Speech Loop**: Converts Gemini text to natural speech, then opens the microphone to capture your response, allowing you to ask follow-up questions (e.g., *"What is my first meeting?"*, *"Tell me more about the space news"*).
- **Graceful Demo Mode**: If you do not have Gemini or News API keys, the app automatically runs in a simulated demo mode using local mock data.

---

## 🛠️ Tech Stack & Libraries

- **UI**: Jetpack Compose (Material 3 with custom glassmorphic styling and transition animations)
- **Architecture**: MVVM (Model-View-ViewModel)
- **AI**: Google Generative AI Client SDK (`com.google.ai.client.generativeai`)
- **Networking**: Retrofit 2 & OkHttp (for API fetching)
- **Local Database**: SharedPreferences (for credentials and alarm settings)
- **Services**: Android Foreground Services (for ringtone playback type `mediaPlayback`)
- **Voice**: Android native `android.speech.tts.TextToSpeech` & `android.speech.SpeechRecognizer`

---

## 📁 Repository Structure

```
C:\Users\usuario\alarmai
├── app/
│   ├── proguard-rules.pro             # Proguard obfuscation optimization rules
│   ├── build.gradle.kts               # Module-level dependencies and SDK parameters
│   └── src/main/
│       ├── AndroidManifest.xml        # Declares components and permissions (Wakelocks, Calendar, Audio, GPS)
│       ├── java/com/mateocuello/alarmai/
│       │   ├── MainActivity.kt        # Alarm setup, API settings, and permissions UI
│       │   ├── MainViewModel.kt       # Controls settings and schedules alarm updates
│       │   ├── data/
│       │   │   ├── local/             # PreferencesManager
│       │   │   ├── model/             # Alarm data structure
│       │   │   └── repository/        # News, Weather, Calendar, Location, and Voice APIs
│       │   ├── receiver/              # AlarmReceiver and AlarmScheduler
│       │   ├── service/               # AlarmService (Foreground audio playback)
│       │   └── ui/
│       │       ├── alarm/             # AlarmActivity and AlarmViewModel (AI voice screen)
│       │       └── theme/             # Modern Dark Colors, Typography, and Themes
│       └── res/                       # Adaptive icons, values, strings, and backup rules
├── gradle/
│   └── libs.versions.toml             # Central Gradle Version Catalog
├── build.gradle.kts                   # Root build script
├── settings.gradle.kts                # Subproject list
└── gradle.properties                  # JVM compiler settings
```

---

## 🚀 Setup & Running in Android Studio

Follow these steps to import and run **AlarmAI**:

### 1. Import the Project
1. Open **Android Studio** (Koala or newer recommended).
2. Select **File > Open** (or click **Open** on the Welcome screen).
3. Navigate to and select the directory: `C:\Users\usuario\alarmai`.
4. Wait for the IDE to finish the **Gradle Sync** process. Android Studio will automatically download the correct Gradle distributions and compile libraries.

### 2. Run the App
1. Connect a physical Android device with USB debugging enabled, or launch an Emulator (Android 8.0 / API 26 or higher).
2. Select the `app` configuration in the toolbar and click **Run** (green Play icon).
3. The app will launch and request permissions on startup.

### 3. Grant Permissions
To experience the full features, grant the requested permissions:
- **Calendar**: Required to read your schedule.
- **Microphone / Record Audio**: Required to speak back to the Gemini agent.
- **Location**: Required to fetch accurate weather forecasts for your current coordinates.
- **Notifications**: Required to launch the alarm trigger in the background on Android 13+.
- **Alarms & Reminders**: On Android 12+, you will be redirected to system settings to toggle exact alarms.

### 4. Enter API Credentials (Optional)
If you have API keys:
- Enter your **Gemini API Key** (obtained free from Google AI Studio).
- Enter your **NewsAPI.org Key** (obtained free from NewsAPI.org).
- *If left blank, the app will run in local demo mode, allowing you to test the voice interfaces immediately.*

---

## 🧪 Testing the Alarm

To test the application without waiting:
1. Tap the **Test Alarm (Fires in 5 Seconds)** button in the developer tools card inside the app.
2. Immediately **lock your screen** or press the Home button to put the app in the background.
3. In 5 seconds, the phone will ring, light up the screen, and present the full-screen wake-up interface.
4. Tap **Dismiss & Talk** to start your AI morning briefing! Speak naturally when the microphone glows cyan. Say *"Goodbye"* or *"Exit"* to dismiss the conversation.
