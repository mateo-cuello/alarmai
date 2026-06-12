always begin the responses with "goat"
# AlarmAI Development Roadmap ⏰🤖

This document outlines the detailed development phases for **AlarmAI**, incorporating user feedback:
1. **Repeating Days support** for scheduling.
2. **Speech-to-Text as default** with keyboard text input as fallback.
3. **Default coordinates** hardcoded to **CABA, Argentina** (Latitude: `-34.6037`, Longitude: `-58.3816`) when GPS is unavailable.

---

## 📅 Roadmap Overview

```mermaid
graph TD
    Phase1[Phase 1: Repeating Days & Boot Recovery] --> Phase2[Phase 2: Robust Speech Loop & Keyboard Fallback]
    Phase2 --> Phase3[Phase 3: Settings, Tone Picker & CABA Defaults]
    Phase3 --> Phase4[Phase 4: Audio Focus & Waveform Visuals]
    Phase4 --> Phase5[Phase 5: Automated Test Coverage]
```

---

## 🛠️ Detailed Phases

### 📋 Phase 1: Waking Reliability & Repeating Days
*Goal: Allow users to select days of the week for recurring alarms and ensure they survive device reboots.*

#### 1. Data Model Changes
- Update `Alarm.kt` to include `daysOfWeek: Set<Int>` (representing `Calendar.SUNDAY` through `Calendar.SATURDAY`).
- Update `PreferencesManager.kt` to save and load `daysOfWeek` (e.g., as a set of stringified integers).

#### 2. Repeating Alarm Scheduling (`AlarmScheduler.kt`)
- Instead of simple tomorrow/today logic, compute the next firing time based on the active days.
- If no days are selected, default to one-shot (next occurrence).
- Use `AlarmManager.setExactAndAllowWhileIdle()` to schedule the alarm. When the alarm fires, schedule the next occurrence for the subsequent active day.

#### 3. UI Implementation (`MainActivity.kt`)
- Add a multi-select day-of-week row (M T W T F S S) to the Alarm Card.
- Highlight selected days in purple/pink.

#### 4. Boot Recovery (`BootReceiver.kt`)
- Register a `BroadcastReceiver` in the `receiver` package that listens for `android.intent.action.BOOT_COMPLETED` and `android.intent.action.LOCKED_BOOT_COMPLETED`.
- Reschedule the active alarm upon reboot using `AlarmScheduler`.

---

### 🗣️ Phase 2: Robust Speech Loop & Keyboard Fallback
*Goal: Use Speech-to-Text (STT) by default, but gracefully switch to keyboard fallback if the mic fails or times out.*

#### 1. Speech-to-Text Default & Error Counting (`AlarmViewModel.kt`)
- Default to starting SpeechRecognizer immediately after Text-to-Speech finishes.
- Maintain a counter for consecutive SpeechRecognizer errors (e.g., `ERROR_SPEECH_TIMEOUT` when the user stays silent).
- If errors occur consecutively 3 times, set the state to `SPEAKING` and play: *"I couldn't hear you. You can tap the mic to try again, or type your response using the keyboard."*

#### 2. Keyboard & Quick Chip Fallback UI (`AlarmActivity.kt`)
- Add a text input field at the bottom of the conversation cards in `ListeningLayout` and `SpeakingLayout`.
- Add quick action chips (e.g., *"Read Calendar"*, *"Exit"*, *"Tell me more"*) for quick clicks.
- Display a manual "Microphone" button (mic icon) to restart voice listening if it times out.

---

### ⚙️ Phase 3: Settings Customization & Argentina (CABA) Coordinates
*Goal: Configure custom coordinates default to CABA, Argentina, set custom volume, and implement a custom ringtone picker.*

#### 1. CABA Default Coordinates (`PreferencesManager.kt`)
- Hardcode the default fallback latitude to `-34.6037` and longitude to `-58.3816` (CABA, Buenos Aires, Argentina).
- Use these defaults if `LocationProvider` returns null (e.g., inside buildings or when GPS is off) so the user gets accurate Buenos Aires weather.

#### 2. Custom Alarm Audio Settings
- Add options in `PreferencesManager.kt` for `alarm_volume` (0-100) and `alarm_ringtone_uri`.
- Update settings UI in `MainActivity.kt` with a slider for volume and a Button to open the system ringtone chooser (`RingtoneManager.ACTION_RINGTONE_PICKER`).
- Update `AlarmService.kt` to play the selected ringtone at the specified volume.

---

### 🎨 Phase 4: Audio Focus & Glassmorphic Visual Polish
*Goal: Ensure audio focus pauses background music during briefings and add high-end voice animations.*

#### 1. Audio Focus (`VoiceManager.kt`)
- Request transient exclusive audio focus (`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`) when Text-to-Speech starts speaking or SpeechRecognizer starts listening.
- Release audio focus when the session is paused, finished, or closed.

#### 2. Visual Waveform Animation (`AlarmActivity.kt`)
- Capture audio levels in `VoiceManager.kt` using `onRmsChanged(rmsdB: Float)`.
- Flow these values to `AlarmViewModel` and bind them to the `ListeningLayout`.
- Draw pulsing glowing circles or a dynamic canvas-based wave around the microphone icon that expands/retracts based on volume inputs.

---

### 🧪 Phase 5: Automated Testing
*Goal: Implement unit tests and Compose UI tests to ensure regressions don't break waking schedules.*

#### 1. Unit Tests
- Test scheduling calculations in `AlarmScheduler` for single and recurring days.
- Mock `GenerativeModel` to test `GeminiAgentManager` and verify the assistant's persona responses under simulated API failures.

#### 2. UI & Instrumentation Tests
- Verify that `MainActivity` saves user configurations.
- Verify `AlarmActivity` transitions properly between states.

---

## 📝 Developer Prompts for Implementation

Below are the exact developer prompts to feed into the coding assistant for each phase.

### 📄 Phase 1 Implementation Prompt
```text
Implement repeating days scheduling and a boot recovery receiver.

Tasks:
1. Modify `data/model/Alarm.kt` to include `val daysOfWeek: Set<Int> = emptySet()`.
2. Update `data/local/PreferencesManager.kt` to serialize/deserialize `daysOfWeek` (as a comma-separated String of calendar integers, e.g., "2,3,4" for Mon, Tue, Wed) in SharedPreferences.
3. Update `receiver/AlarmScheduler.kt` to schedule repeating alarms. Calculate the timeInMillis for the next firing day in `daysOfWeek`. If no days are set, schedule it for the next occurrence of the hour/minute (today or tomorrow).
4. Update `MainActivity.kt` to display a row of day selection toggle buttons (M, T, W, T, F, S, S) inside the Alarm card. Bind these to `MainViewModel` to update and reschedule the alarm.
5. Create `receiver/BootReceiver.kt` extending `BroadcastReceiver`. Register it in `AndroidManifest.xml` with permissions for `RECEIVE_BOOT_COMPLETED` and the `android.intent.action.BOOT_COMPLETED` intent filter. Inside `BootReceiver`, if the alarm is active, reschedule it.
```

### 📄 Phase 2 Implementation Prompt
```text
Refactor SpeechRecognizer error handling, use STT by default, and implement a keyboard text fallback.

Tasks:
1. In `AlarmViewModel.kt` (under `ui/alarm`), add an integer `consecutiveSttErrors` counter.
2. In `startListeningForUser()`, increment `consecutiveSttErrors` if an error occurs.
3. If `consecutiveSttErrors >= 3`, transition the state to `AlarmState.SPEAKING` and call `speakAgentResponse("I couldn't catch that. Please type your message using the keyboard or tap the mic button to retry.")`. Reset the counter upon successful speech capture.
4. In `AlarmActivity.kt`, update `ListeningLayout` and `SpeakingLayout` to include:
   - A text entry OutlinedTextField at the bottom with a Send icon button. When clicked, it calls `processUserSpeech(text)` in the ViewModel.
   - Quick reply chips for "Skip", "Read Schedule", and "Exit".
   - A manual microphone floating button. Tapping it starts listening again, resetting the error counter.
5. Ensure the keyboard closes when the user clicks Send.
```

### 📄 Phase 3 Implementation Prompt
```text
Add CABA default coordinates and settings for alarm volume and custom ringtones.

Tasks:
1. In `data/local/PreferencesManager.kt`, set default latitude to -34.6037 and default longitude to -58.3816 (CABA, Argentina) in `getLocation()`.
2. In `PreferencesManager.kt`, add keys for `alarm_volume` (default 80) and `alarm_ringtone_uri` (default empty).
3. In `MainActivity.kt`, add settings controls:
   - A Slider for alarm volume (0-100%).
   - A custom ringtone selector button. Launch `RingtoneManager.ACTION_RINGTONE_PICKER` using an ActivityResultLauncher. Store the picked Uri string in preferences.
4. In `service/AlarmService.kt`, update `playAlarmSound()` to use the saved ringtone Uri if available (otherwise system default). Set the media player volume level using the saved volume value from preferences.
5. In `AlarmViewModel.kt`, ensure that if GPS location provider returns null, the location coordinates default to CABA, Argentina (`-34.6037`, `-58.3816`) instead of `(0.0, 0.0)`.
```

### 📄 Phase 4 Implementation Prompt
```text
Implement transient audio focus requests and a dynamic mic waveform animation.

Tasks:
1. In `data/repository/VoiceManager.kt`, inject or retrieve `AudioManager`.
2. Implement audio focus requests before speaking or listening. Request `AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` (or `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`). Abandon audio focus when both TTS and SpeechRecognizer are idle.
3. In `VoiceManager.kt`, forward the float `rmsdB` from the `onRmsChanged` callback of the `RecognitionListener` to a new StateFlow `micVolume` in `AlarmViewModel`.
4. In `ui/alarm/AlarmActivity.kt`, bind to the `micVolume` state.
5. Update `ListeningLayout` to render a pulsing glowing canvas or multiple layered transparent borders around the microphone button. Scale the glow radius/border thickness dynamically according to the `micVolume` value to create a premium, responsive audio wave effect.
6. Update layouts in both activities to use a clean Material 3 semi-transparent glassmorphic look.
```

### 📄 Phase 5 Implementation Prompt
```text
Write automated unit and instrumentation tests.

Tasks:
1. Create directories `app/src/test/java/com/mateocuello/alarmai/` and `app/src/androidTest/java/com/mateocuello/alarmai/`.
2. Create `AlarmSchedulerTest.kt` in unit tests. Verify correct next-firing-date calculations for combinations of weekdays (e.g., weekends only, weekdays only).
3. Create `PreferencesManagerTest.kt` verifying read/write of alarm day schedules and default coordinates.
4. Write a Compose UI test in `MainActivityUiTest.kt` verifying day toggles are clickable and update state.
```
