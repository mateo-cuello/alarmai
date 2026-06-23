# Analysis: Android 16 (API 36) Compatibility - Speech Recognition Updates

This report outlines findings and proposed compatibility updates for Android 16 (API 36) regarding speech recognition setup in `VoiceManager.kt` and its associated unit tests.

## Summary of Findings
- The application currently uses standard network-based speech recognition initialized through `SpeechRecognizer.createSpeechRecognizer(context)`.
- To support Android 16 and modern Android best practices, the speech recognition factory in `VoiceManager.kt` must prefer the on-device `SpeechRecognizer.createOnDeviceSpeechRecognizer(context)` for devices running Android 12 (API 31/S) or higher when on-device recognition is available, falling back to standard network-based recognition otherwise.
- All existing unit tests pass successfully. Introducing this new behavior will not cause regressions since existing tests explicitly inject a mocked speech recognizer factory.

---

## 1. Observation
### Current Implementation in `VoiceManager.kt`
- **File Path**: `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt`
- **Line 22**:
```kotlin
    private val speechRecognizerFactory: (Context) -> SpeechRecognizer = { ctx -> SpeechRecognizer.createSpeechRecognizer(ctx) }
```
- **Instantiation and usage (Lines 75-77 and Lines 232-234)**:
```kotlin
            if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = speechRecognizerFactory(context)
            }
```

### Current Test suite status
- **Test execution command**: `.\gradlew.bat test`
- **Result**: `BUILD SUCCESSFUL` (all unit tests passed).
- **Unit test file**: `app/src/test/java/com/mateocuello/alarmai/data/repository/VoiceManagerTest.kt`
- **Observation of Mock Injection (e.g. Lines 126-134)**:
```kotlin
        VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, listener ->
                capturedListener = listener
                mockTts
            },
            speechRecognizerFactory = { mockSpeechRecognizer }
        )
```
Existing tests always override `speechRecognizerFactory` with a mock closure returning `mockSpeechRecognizer`. Thus, the default implementation containing `SpeechRecognizer.createSpeechRecognizer` is never run or asserted during existing tests.

---

## 2. Logic Chain
1. Android 12 (API 31) introduced `SpeechRecognizer.createOnDeviceSpeechRecognizer(Context)` to support offline/on-device recognition, which provides lower latency and works offline.
2. Android 16 (API 36) recommends preferring on-device recognition for performance and user privacy reasons where supported.
3. Therefore, `speechRecognizerFactory`'s default constructor value should check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` and whether `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)` returns `true` before attempting to create the on-device speech recognizer.
4. Directly using `Build.VERSION.SDK_INT` inside primary constructor parameters of `VoiceManager` makes it difficult to mock the SDK level in a pure JUnit test.
5. Delegating the creation to a helper method in a `companion object` and providing a testable `sdkVersionProvider` lets tests simulate older and newer SDK environments without needing complex static field reflection.

---

## 3. Caveats
- **On-Device Availability**: On-device speech recognition requires appropriate Google Search app/speech service packages pre-installed on the device, which is why `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)` must be checked.
- **Audio Permission**: The calling context must still request and hold `RECORD_AUDIO` permission regardless of whether the recognition is performed on-device or over the network.
- **Min SDK version**: The project specifies `minSdk = 26`. Because we use runtime guards (`SDK_INT >= 31`), using these APIs is safe and backward-compatible.

---

## 4. Conclusion & Proposed Change Strategy

We propose the following clean, backward-compatible, and fully testable updates:

### Proposed Code Updates for `VoiceManager.kt`
1. Add import:
```kotlin
import android.os.Build
```
2. Update constructor in `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt`:
```kotlin
class VoiceManager(
    private val context: Context,
    private val prefs: PreferencesManager = PreferencesManager(context),
    private val ttsFactory: (Context, TextToSpeech.OnInitListener) -> TextToSpeech = { ctx, listener -> TextToSpeech(ctx, listener) },
    private val speechRecognizerFactory: (Context) -> SpeechRecognizer = { ctx ->
        createSpeechRecognizerDefault(ctx)
    }
) {
```
3. Add a helper companion object at the bottom of the `VoiceManager` class:
```kotlin
    companion object {
        internal var sdkVersionProvider: () -> Int = { Build.VERSION.SDK_INT }

        private fun createSpeechRecognizerDefault(ctx: Context): SpeechRecognizer {
            return if (sdkVersionProvider() >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
            } else {
                SpeechRecognizer.createSpeechRecognizer(ctx)
            }
        }
    }
```

### Proposed Test Updates for `VoiceManagerTest.kt`
1. In `app/src/test/java/com/mateocuello/alarmai/data/repository/VoiceManagerTest.kt`, add a reset statement to `tearDown()` to clean up static state:
```kotlin
    @After
    fun tearDown() {
        VoiceManager.sdkVersionProvider = { android.os.Build.VERSION.SDK_INT }
        logMock.close()
        srMockStatic.close()
        // ...
    }
```
2. Add the following unit tests at the end of the `VoiceManagerTest` class to verify factory selection:
```kotlin
    @Test
    fun testDefaultSpeechRecognizerFactory_preferOnDeviceWhenAvailable() {
        VoiceManager.sdkVersionProvider = { android.os.Build.VERSION_CODES.S }

        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, _ -> mockTts }
        )

        srMockStatic.`when`<Boolean> { SpeechRecognizer.isOnDeviceRecognitionAvailable(any()) }.thenReturn(true)
        val mockOnDeviceRecognizer = mock<SpeechRecognizer>()
        srMockStatic.`when`<SpeechRecognizer> { SpeechRecognizer.createOnDeviceSpeechRecognizer(any()) }.thenReturn(mockOnDeviceRecognizer)
        srMockStatic.`when`<Boolean> { SpeechRecognizer.isRecognitionAvailable(any()) }.thenReturn(true)

        voiceManager.startListening(
            onResult = {},
            onError = {},
            onRmsChanged = {}
        )

        srMockStatic.verify { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
    }

    @Test
    fun testDefaultSpeechRecognizerFactory_fallbackToStandardWhenOnDeviceNotAvailable() {
        VoiceManager.sdkVersionProvider = { android.os.Build.VERSION_CODES.S }

        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, _ -> mockTts }
        )

        srMockStatic.`when`<Boolean> { SpeechRecognizer.isOnDeviceRecognitionAvailable(any()) }.thenReturn(false)
        val mockStandardRecognizer = mock<SpeechRecognizer>()
        srMockStatic.`when`<SpeechRecognizer> { SpeechRecognizer.createSpeechRecognizer(any()) }.thenReturn(mockStandardRecognizer)
        srMockStatic.`when`<Boolean> { SpeechRecognizer.isRecognitionAvailable(any()) }.thenReturn(true)

        voiceManager.startListening(
            onResult = {},
            onError = {},
            onRmsChanged = {}
        )

        srMockStatic.verify { SpeechRecognizer.createSpeechRecognizer(context) }
    }

    @Test
    fun testDefaultSpeechRecognizerFactory_fallbackToStandardOnOlderApi() {
        VoiceManager.sdkVersionProvider = { android.os.Build.VERSION_CODES.R }

        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, _ -> mockTts }
        )

        val mockStandardRecognizer = mock<SpeechRecognizer>()
        srMockStatic.`when`<SpeechRecognizer> { SpeechRecognizer.createSpeechRecognizer(any()) }.thenReturn(mockStandardRecognizer)
        srMockStatic.`when`<Boolean> { SpeechRecognizer.isRecognitionAvailable(any()) }.thenReturn(true)

        voiceManager.startListening(
            onResult = {},
            onError = {},
            onRmsChanged = {}
        )

        srMockStatic.verify { SpeechRecognizer.createSpeechRecognizer(context) }
    }
```

---

## 5. Verification Method
- **Verification Command**: Run `.\gradlew.bat test` from the root directory.
- **Verification Condition**:
  - The build compiles and executes unit tests successfully.
  - The new tests (`testDefaultSpeechRecognizerFactory_preferOnDeviceWhenAvailable`, etc.) pass, proving the runtime logic switches correctly based on `sdkVersionProvider` and on-device recognition status.
