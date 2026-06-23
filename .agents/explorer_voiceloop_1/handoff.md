# Handoff Report — Voice Loop Exploration

## 1. Observation
We examined the conversational loop implementation in the AlarmAI application and observed the following details in the codebase:
- **File Paths and Lines Referenced**:
  - `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt`
    - Line 251: `android.os.SystemClock.sleep(50)` (Main thread blocking)
    - Line 331: `fun stopListening() { ... }` (Does not call `unmuteBeep()`)
    - Line 324: `fun stopSpeaking() { ttsCompleteCallback = null; tts?.stop(); ... }` (Callback cleared before `stop()`, potential thread race)
    - Line 253: `speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)` (No availability check preceding this)
    - Line 222: `tts?.stop()` called within `ttsCompleteCallback` (Can cause deadlock/re-entry issues)
  - `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`
    - Lines 202-209: `voiceManager.speak(text) { ... voiceManager.stopSpeaking(); kotlinx.coroutines.delay(600); startListeningForUser() }` (Creates a 600ms gap where audio focus is lost and then re-requested)
    - Lines 226-243: `onError` block under `startListeningForUser()` handles max retries by simply logging: `Log.w("AlarmViewModel", "STT max retries reached, staying in LISTENING state")` (Leaving the UI stuck with a silent microphone)
    - Lines 104-170: `dismissAndTalk()` and Lines 258-290: `processUserSpeech()` (Coroutine scopes lack try-catch for external repository/Gemini API calls, leaving exceptions unhandled and UI state frozen in `FETCHING_DATA` or `THINKING`)
  - `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmActivity.kt`
    - Lines 192-206: The Compose UI handles `AlarmState` transitions, but has `else -> {}` branch for `AlarmState.ERROR` resulting in an empty view.
- **Unit Test Execution**:
  - Running task `.\gradlew.bat testDebugUnitTest` compiled successfully:
    ```
    > Task :app:testDebugUnitTest UP-TO-DATE
    BUILD SUCCESSFUL in 9s
    ```
  - Running grep search in `app/src/test` and `app/src/androidTest` for `VoiceManager` and `AlarmViewModel` returned `No results found`.

## 2. Logic Chain
1. **Blocking UI**: Because `SystemClock.sleep(50)` is executed in the `mainHandler` thread post block (Observation 1.1), it freezes the Android main thread, preventing UI renders or handling touch inputs for 50ms.
2. **Permanent Stream Muting**: Since `stopListening()` is called when ending speech segments, and it does not call `unmuteBeep()` (Observation 1.2), the system streams (`STREAM_MUSIC`, `STREAM_SYSTEM`, `STREAM_NOTIFICATION`) remain muted. This disables all media and alarm sounds.
3. **TTS Race & Deadlock**: In `stopSpeaking()`, nullifying `ttsCompleteCallback` before `tts?.stop()` runs (Observation 1.3) allows listener threads to race with the clearing. Calling `tts?.stop()` within the naturally triggered callback (Observation 1.3) can cause deadlocks on the engine threads. Synchronized wrappers (`synchronized(ttsLock)`) can isolate the callback execution from stop actions.
4. **App Crashes**: Instantiating `SpeechRecognizer` without checking `SpeechRecognizer.isRecognitionAvailable(context)` (Observation 1.4) will crash the application on any device lacking a speech-to-text service provider.
5. **Audio Focus Drop**: Releasing focus inside `checkAndAbandonFocus()` during natural TTS done transitions, only to delay 600ms and re-request it for STT (Observation 1.5), causes a 600ms gap where audio focus is abandoned. Tracking session state (`isSessionActive`) prevents this focus release until the conversational session is explicitly terminated.
6. **UI Stalling**: Because exceptions from STT retries (Observation 1.6), Gemini, or repositories (Observation 1.8) are unhandled, the application states (`THINKING`, `FETCHING_DATA`, `LISTENING`) get stuck. Since `AlarmState.ERROR` is unhandled in Compose (Observation 1.7), the app transitions to a blank view when errors occur instead of presenting a retry interface.

## 3. Caveats
- Android `SpeechRecognizer` behavior is heavily dependent on the device manufacturer's speech service. Verification must test physical devices and Android emulators.
- No unit tests currently mock TTS/STT interfaces, so test execution only verifies build compatibility and existing FIFA repository logic.

## 4. Conclusion
The conversational loop contains multiple critical vulnerabilities that cause UI thread lockups, permanent stream muting, focus loss, and state machines getting stuck upon exceptions. Implementing session-aware audio focus, thread-safe synchronization on TTS callbacks, non-blocking handlers, robust error-catching coroutines, and an `ErrorLayout` in Compose fixes all these problems.

## 5. Verification Method
- **Compilation & Test Suite**: Run `.\gradlew.bat testDebugUnitTest` to ensure all existing unit tests compile and pass.
- **Inspect Files**: Confirm code layout updates inside:
  - `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt`
  - `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`
  - `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmActivity.kt`
- **Verification of Fixes**:
  - Verify UI thread is not blocked by checking that no `SystemClock.sleep` exists in main-thread callbacks.
  - Verify stream muting is restored by invoking `stopListening()` and verifying that volume streams are unmuted.
  - Verify that simulated errors (e.g. invalid API key or network failure) correctly trigger `AlarmState.ERROR` and render the retry/close UI in `AlarmActivity`.
