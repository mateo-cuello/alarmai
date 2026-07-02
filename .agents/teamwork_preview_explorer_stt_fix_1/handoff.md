# Handoff Report - Speech-to-Text (STT) Diagnosis on Android 14

## 1. Observation
We analyzed the codebase and the log files for AlarmAI. The following exact lines and behaviors were observed:
*   **Logcat error traces**:
    - In `c:\Users\usuario\alarmai\.agents\teamwork_preview_explorer_stt_fix_1\logcat_utf8.txt` at line 105332:
        `06-24 01:29:17.950 25159 25213 W RecognitionClient: #onError space agsa_transcription_NO_SPEECH_DETECTED code -1!`
    - In `logcat_utf8.txt` at line 105334:
        `06-24 01:29:17.951 17626 17626 E STT     : STT error code = 7` (corresponds to `SpeechRecognizer.ERROR_NO_MATCH`).
    - During the retry loop, subsequent errors occur instantly. In `logcat_utf8.txt` at lines 43603-43803:
        ```
        06-13 02:57:42.587 13359 13359 E STT     : STT error code = 5
        06-13 02:57:45.008 13359 13359 E STT     : STT error code = 5
        ```
        (corresponds to `SpeechRecognizer.ERROR_CLIENT`).
*   **SpeechRecognizer Reuse**:
    - In `c:\Users\usuario\alarmai\app\src\main\java\com\mateocuello\alarmai\data/repository/VoiceManager.kt` lines 238-240:
        ```kotlin
        if (speechRecognizer == null) {
            speechRecognizer = speechRecognizerFactory(context)
        }
        ```
        The class reuses the same instance of `SpeechRecognizer` directly without releasing, resetting, or recreating it after errors.
*   **Context usage**:
    - In `c:\Users\usuario\alarmai\app\src\main\java\com\mateocuello\alarmai\ui/alarm/AlarmViewModel.kt` line 50:
        `private val voiceManager: VoiceManager = VoiceManager(application, prefs)`
        The `Application` context is passed into `VoiceManager`, which is used to initialize the `SpeechRecognizer`.
*   **Intent Extra Type Mismatch**:
    - In `VoiceManager.kt` line 248:
        `putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, locale.toLanguageTag())`
        A string is passed instead of a boolean, and the extra itself is only used for details querying.

---

## 2. Logic Chain
1. The initial STT failure is triggered by the system `RecognitionClient` returning `NO_SPEECH_DETECTED` (mapped to `ERROR_NO_MATCH` code 7). This typically happens due to network issues on the emulator, missing offline language packs (demonstrated by `Failed to get language pack of required locale: error 13`), or the mic opening too fast while TTS is releasing audio.
2. In `AlarmViewModel.kt`, the `onError` callback in the view model receives this failure and immediately triggers a retry via `startListeningForUser()`.
3. Because `VoiceManager.startListening()` checks `if (speechRecognizer == null)` and sees it is not null, it reuses the same `speechRecognizer` instance without calling `cancel()` or recreating it.
4. Reusing a `SpeechRecognizer` instance that has previously encountered an error throws it into an unrecoverable state, leading to immediate `ERROR_CLIENT` (code 5) on all subsequent automatic retries.
5. On Android 13/14, initializing `SpeechRecognizer` with the `Application` context instead of an `Activity` context makes binding and permission validation fragile, contributing to the instant `ERROR_CLIENT` (code 5) failures.
6. Once the consecutive error count reaches 5, the view model transitions to `AlarmState.ERROR`, propagating the error message "No se pudo iniciar la entrada de voz" to the UI.

---

## 3. Caveats
*   We did not test the behavior on physical devices running Android 14, only on emulator logs. On physical devices with cellular/Wi-Fi and downloaded voice packages, the initial `ERROR_NO_MATCH` might not occur as frequently, but the state corruption/reuse bug will still trigger under any transient network failure or interruption.
*   We assumed that on-device recognition is supported by Google Play Services on the target runtime environment. If it is not, it will fall back to online recognition.
*   **Test Suite Compilation failure**: Running the test command `.\gradlew.bat test` currently fails compilation due to unresolved references to `WorldCupRepository` and method signature changes in `GeminiAgentManager` left over from a prior unrelated refactoring.

---

## 4. Conclusion
The instant speech recognition failure is caused by:
1. Reusing a corrupted `SpeechRecognizer` instance after an initial error, which locks it into returning `ERROR_CLIENT` (code 5).
2. Using the `Application` context to create the recognizer, which is problematic under Android 13/14 background service limitations.
3. Network connection timeouts / missing locale packs on the emulator causing the initial `ERROR_NO_MATCH` (code 7).

We suggest three major fixes:
1. **Always recreate `SpeechRecognizer` on every start/retry**: Destroy the previous instance and initialize a new one when `startListening` is called.
2. **On-Device recognition fallback**: Call `SpeechRecognizer.createOnDeviceSpeechRecognizer` on API 33+ when available.
3. **Pass Activity Context where possible** or ensure a clean wrapper is used.

---

## 5. Verification Method
To verify the diagnosis and the proposed fixes:
1. Note that the unit tests are currently failing compilation because of unrelated legacy code (such as missing `WorldCupRepository` imports/stubs in test files). The subsequent implementer agent will need to first stub or fix these unresolved references in the test sources before running:
   `.\gradlew.bat test`
2. Inspect the proposed changes in `analysis.md` and verify that `VoiceManager.kt` contains the recreation of `SpeechRecognizer` and a proper cleanup routine.
3. In a runtime test, simulate an error in the first recognition turn (e.g., stay silent to force code 7) and confirm that the subsequent retries no longer fail instantly with code 5, but instead successfully open the microphone (the green dot remains or blinks) and wait for the correct backoff delay.

