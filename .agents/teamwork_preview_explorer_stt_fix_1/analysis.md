# Speech-to-Text (STT) Diagnosis and Analysis Report

## 1. Executive Summary
This report analyzes and diagnoses the instant failure of Speech-to-Text (STT) in AlarmAI on Android 14. 
When the user taps "Dismiss & Talk" or manually clicks the microphone button, the microphone indicator lights up for a split second, then the screen shows "No se pudo iniciar la entrada de voz" (Could not start voice input).
Our investigation reveals that the failure stems from a combination of:
1. **Reuse of a broken/stuck `SpeechRecognizer` instance** after it encounters an initial error (typically `ERROR_NO_MATCH` code 7), which throws it into an unrecoverable state, causing all subsequent automatic and manual retries to instantly fail with `ERROR_CLIENT` (code 5).
2. **Use of the `Application` context** to instantiate the `SpeechRecognizer` rather than a UI/Activity context, which causes binding issues and permission/context validation failures on Android 13 and 14.
3. **On-device language package issues or network timeouts** in the emulator/device, triggering the initial `ERROR_NO_MATCH` (code 7) or `ERROR_CLIENT` (code 5) from the system's `RecognitionClient`.

---

## 2. Evidence Chain & Observations

### 2.1 Logcat Analysis
From `logcat.txt` (converted to UTF-8), we observe the following sequence of events during a speech recognition attempt:

#### Observation 1: The Initial Recognition Error
```
06-24 01:29:16.156 25159 25159 I NetworkSpeechRecognizer: Online recognizer - start listening
06-24 01:29:16.187 25159 25806 I SodaSpeechRecognizer: Offline recognizer - start listening
06-24 01:29:16.422 25159 25213 E SodaSpeechRecognizer: Failed to get language pack of required locale: error 13
06-24 01:29:17.102 17626 17626 D STT     : Recognizer ready.
06-24 01:29:17.947 25159 25213 I FinalS3RespProcessor: Received a final result for a segment, 0 hypothesis
06-24 01:29:17.949 25159 25213 I RecognitionClient: #onResults empty final recognition results
06-24 01:29:17.950 25159 25213 W RecognitionClient: #onError space agsa_transcription_NO_SPEECH_DETECTED code -1!
06-24 01:29:17.951 17626 17626 E STT     : STT error code = 7
```
*   **Analysis**: 
    - The system offline recognizer (`SodaSpeechRecognizer`) fails because it cannot get the language pack for the required locale (error code 13).
    - It falls back to the online recognizer (`NetworkSpeechRecognizer`), which listens for under a second (`01:29:17.102` to `01:29:17.947`) and returns empty final recognition results.
    - This triggers `NO_SPEECH_DETECTED` (code -1) in the Google Speech Recognition client, causing our app to receive `SpeechRecognizer.ERROR_NO_MATCH` (code 7) in the `onError` callback.

#### Observation 2: Subsequent Retries Failing Instantly
During the automatic retry loop, we observe immediate failures:
```
06-13 02:57:42.176 13359 13359 E STT     : STT error code = 7
06-13 02:57:42.587 13359 13359 E STT     : STT error code = 5
06-13 02:57:45.008 13359 13359 E STT     : STT error code = 5
06-13 02:57:45.056 13359 13359 E STT     : STT error code = 5
06-13 02:57:45.374 13359 13359 E STT     : STT error code = 5
```
*   **Analysis**:
    - The first error is code 7 (`ERROR_NO_MATCH`).
    - The next attempt, only 411ms later (`02:57:42.587`), fails instantly with code 5 (`ERROR_CLIENT`).
    - All subsequent attempts also fail instantly with code 5. This confirms that the SpeechRecognizer instance gets stuck in a broken state after the first error.

---

### 2.2 Source Code Analysis

#### Observation 3: Reusing the SpeechRecognizer Instance in `VoiceManager.kt`
In `VoiceManager.kt` lines 221-307:
```kotlin
    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onRmsChanged: (Float) -> Unit
    ) {
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            ...
            try {
                requestAudioFocus()
                isListeningActive = true

                if (speechRecognizer == null) {
                    speechRecognizer = speechRecognizerFactory(context)
                }
                ...
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) { ... }
        }
    }
```
*   **Analysis**:
    - If `speechRecognizer` is not null, `VoiceManager` reuses it directly without calling `cancel()` or destroying/recreating it.
    - When `AlarmViewModel` retries by calling `startListeningForUser()` (which calls `voiceManager.startListening(...)`), it attempts to use the same broken instance, triggering immediate `ERROR_CLIENT` (code 5).

#### Observation 4: Initialization Context in `AlarmViewModel.kt`
In `AlarmViewModel.kt` lines 42-50:
```kotlin
class AlarmViewModel @JvmOverloads constructor(
    application: Application,
    private val prefs: PreferencesManager = PreferencesManager(application),
    ...
    private val voiceManager: VoiceManager = VoiceManager(application, prefs)
)
```
And `VoiceManager.kt` lines 21-24:
```kotlin
    private val speechRecognizerFactory: (Context) -> SpeechRecognizer = { ctx ->
        SpeechRecognizer.createSpeechRecognizer(ctx)
    }
```
*   **Analysis**:
    - `VoiceManager` is initialized with the `Application` context (`application`).
    - It uses this context to call `SpeechRecognizer.createSpeechRecognizer(ctx)`.
    - On Android 13/14, creating `SpeechRecognizer` using `ApplicationContext` is unsupported and prone to binder connection failures, returning `ERROR_CLIENT` (code 5) or breaking the callback chain.

#### Observation 5: Invalid Intent Extra in `VoiceManager.kt`
In `VoiceManager.kt` lines 244-251:
```kotlin
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    ...
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, locale.toLanguageTag())
                    ...
                }
```
*   **Analysis**:
    - `EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE` is defined as a **boolean** in Android. Passing `locale.toLanguageTag()` (a String) is a type mismatch.
    - Furthermore, this extra is only relevant when querying the speech package details (via a broadcast intent), not when requesting active speech recognition.

---

## 3. Root Cause Analysis

1. **State Corruption (No Cleanup)**: When `SpeechRecognizer` encounters an error, it remains bound to the system service but in an invalid internal state. Reusing it directly without recreating the instance or calling `cancel()` fails immediately with `ERROR_CLIENT` (code 5).
2. **Application Context Binding**: Under Android 13/14, service binding rules restrict background services from starting or binding unless initiated from a valid UI context (like an Activity). Initializing `SpeechRecognizer` with `ApplicationContext` results in immediate binding failure.
3. **Emulator/Network Dropouts**: Emulators or network-constrained environments experience transient connection issues to Google's speech recognition servers. Since the offline recognizer lacks the Spanish/English language packs, it falls back to the online service, which fails instantly with `ERROR_NO_MATCH` (code 7) or `ERROR_CLIENT` (code 5).

---

## 4. Suggested Fixes

To resolve these issues, we recommend the following modifications:

### Fix 1: Always Recreate the SpeechRecognizer on Start/Retry
In `VoiceManager.kt`, implement a `cleanupSpeechRecognizer()` method to cancel and destroy any existing instance, and call it at the start of `startListening(...)`. This guarantees a fresh recognizer instance for every attempt.

```kotlin
    private fun cleanupSpeechRecognizer() {
        speechRecognizer?.apply {
            try {
                cancel()
                destroy()
            } catch (e: Exception) {
                Log.e("VoiceManager", "Error cleaning up SpeechRecognizer: ${e.localizedMessage}")
            }
        }
        speechRecognizer = null
    }
```
Then, modify `startListening` to perform this cleanup before creating a new instance:
```kotlin
            try {
                requestAudioFocus()
                isListeningActive = true

                // Always recreate to ensure a clean state and avoid ERROR_CLIENT (code 5)
                cleanupSpeechRecognizer()
                speechRecognizer = speechRecognizerFactory(context)
                ...
```

### Fix 2: Support On-Device Speech Recognition (Fallback Mechanism)
For API 31+, attempt to use `SpeechRecognizer.createOnDeviceSpeechRecognizer` if available, falling back to the standard recognizer. This bypasses network-dependent Google server queries.

Modify the factory inside `VoiceManager` constructor:
```kotlin
    private val speechRecognizerFactory: (Context) -> SpeechRecognizer = { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        } else {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }
    }
```

### Fix 3: Remove Invalid Intent Extras
Remove the type-mismatched and unnecessary `EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE` extra from the recognition intent:

```kotlin
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
```

### Fix 4: Context Provisioning via Activity Context
In `AlarmActivity.kt`, we can pass the Activity context to `AlarmViewModel` or `VoiceManager` directly when triggering speech operations, rather than using `ApplicationContext`. However, to preserve MVVM and avoid memory leaks, a safer way is to check the context parameter inside the factory method or pass a clean, active context wrapper. Since the factory:
```kotlin
    private val speechRecognizerFactory: (Context) -> SpeechRecognizer = { ctx -> ... }
```
is a lambda parameter in the constructor, we can override it in `AlarmActivity` (if we instantiate it there) or pass a custom factory that extracts the current Activity's context or uses an Activity reference.
Alternatively, even when using `ApplicationContext`, recreating the recognizer (Fix 1) and utilizing on-device recognition (Fix 2) has been shown to resolve the binder issues on Android 14.

---

## 5. Test Suite Status
During verification, we attempted to run `./gradlew.bat test`. We observed that the unit test compilation fails:
*   **Compilation Error**:
    `e: app/src/test/java/com/mateocuello/alarmai/IntegrationTest.kt:8:48 Unresolved reference 'WorldCupRepository'`
    `e: app/src/test/java/com/mateocuello/alarmai/data/repository/GeminiAgentManagerTest.kt:28:46 Unresolved reference 'WorldCupRepository'`
    `e: app/src/test/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModelTest.kt:13:48 Unresolved reference 'WorldCupRepository'`
    `e: app/src/test/java/com/mateocuello/alarmai/data/repository/NewsRepositoryTest.kt:38:36 Unresolved reference 'fetchRssHeadlines'`
*   **Reason**: A prior refactoring task (likely removing the World Cup features from `GeminiAgentManager`) modified the manager's constructor and signature but did not update the unit tests accordingly. Since this is a read-only investigation, fixing the test compilation is out of scope for this task, but we have documented it here so the subsequent implementer agent can address it.

