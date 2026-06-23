# Voice Loop Analysis Report

This report presents findings from the investigation of the voice-to-text (STT) and text-to-speech (TTS) conversation loop in the AlarmAI application.

---

## 1. Analysis of Critical Issues and Code Points

### 1.1 `SystemClock.sleep(50)` on the Main Thread Blocking UI
- **File**: `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt`
- **Lines**: 248–253
- **Verbatim Code**:
  ```kotlin
  // Mute beep BEFORE creating the recognizer to catch initialization sounds
  muteBeep()
  
  // Small delay to let mute take effect
  android.os.SystemClock.sleep(50)
  
  speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
  ```
- **Rationale**: This sleep is executed within `mainHandler.post { ... }`. Since it runs directly on the main thread, it halts UI updates, processing of touches, and frame rendering for 50ms, causing noticeable UI stuttering.

### 1.2 `stopListening()` Leaving Streams Muted
- **File**: `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt`
- **Lines**: 331–338
- **Verbatim Code**:
  ```kotlin
  fun stopListening() {
      val mainHandler = android.os.Handler(context.mainLooper)
      mainHandler.post {
          speechRecognizer?.stopListening()
          isListeningActive = false
          checkAndAbandonFocus()
      }
  }
  ```
- **Rationale**: `stopListening()` is called externally from the ViewModel (e.g., when transitioning states or closing). It fails to call `unmuteBeep()`. As a result, the audio streams (`STREAM_MUSIC`, `STREAM_SYSTEM`, `STREAM_NOTIFICATION`) remain muted indefinitely, silencing media playback, notification sounds, and system tones.

### 1.3 `stopSpeaking()` Callback Nullification Race Condition
- **File**: `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt`
- **Lines**: 324–329
- **Verbatim Code**:
  ```kotlin
  fun stopSpeaking() {
      ttsCompleteCallback = null
      tts?.stop()
      isTtsActive = false
      checkAndAbandonFocus()
  }
  ```
- **Rationale**: Nullifying `ttsCompleteCallback` *before* invoking `tts?.stop()` poses a race condition. Since TTS listener callbacks (`onDone`, `onError`) run on a separate binder thread, a callback execution might read `ttsCompleteCallback` right before it gets cleared, resulting in concurrent/duplicate executions or state mismatches. Furthermore, calling `tts?.stop()` inside the callback (lines 221–222) is redundant and can cause deadlock issues during natural completion.

### 1.4 Missing `SpeechRecognizer.isRecognitionAvailable()` Check
- **File**: `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt`
- **Lines**: 253
- **Rationale**: The code directly attempts `SpeechRecognizer.createSpeechRecognizer(context)` without calling `SpeechRecognizer.isRecognitionAvailable(context)`. On devices or emulators lacking Google Play Services or speech-to-text engines, this results in runtime exceptions and application crashes.

### 1.5 Audio Focus Abandoned then Re-Acquired (600ms Gap)
- **Files**: 
  - `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt` (Lines 202–209)
  - `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt` (Lines 86–90, 219–225)
- **Verbatim Code** (`AlarmViewModel.kt`):
  ```kotlin
  voiceManager.speak(text) {
      // Callback when agent finishes speaking: add delay to let TTS fully release audio
      viewModelScope.launch {
          voiceManager.stopSpeaking()
          kotlinx.coroutines.delay(600)
          startListeningForUser()
      }
  }
  ```
- **Rationale**: When the speech finishes, `ttsCompleteCallback` runs and invokes `checkAndAbandonFocus()`. Since both `isTtsActive` and `isListeningActive` are false, it abandons audio focus. Then, the ViewModel coroutine waits for 600ms before calling `startListeningForUser()`, which re-requests audio focus. This 600ms window abandons audio focus to other apps, allowing ducked audio to resume or system sounds to interrupt the assistant conversation loop.

### 1.6 STT Retry Logic and State Stalling
- **File**: `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`
- **Lines**: 226–243
- **Verbatim Code**:
  ```kotlin
  onError = { error ->
      Log.e("AlarmViewModel", "STT Error ($consecutiveSttErrors): $error")
      _micVolume.value = 0f
      consecutiveSttErrors++
      if (consecutiveSttErrors < 5 && _uiState.value == AlarmState.LISTENING) {
          // Retry with increasing delay to avoid spamming the recognizer
          viewModelScope.launch {
              val delay = 800L + (consecutiveSttErrors * 400L)
              kotlinx.coroutines.delay(delay)
              if (_uiState.value == AlarmState.LISTENING) {
                  startListeningForUser()
              }
          }
      } else {
          Log.w("AlarmViewModel", "STT max retries reached, staying in LISTENING state")
          // Stop retrying to avoid infinite loop, user can tap mic to restart
      }
  }
  ```
- **Rationale**: When STT fails 5 times, the application logs the condition but stays silently stuck in the `LISTENING` state. The microphone is disabled, and there is no user-facing indicator of the error, leaving the app unresponsive unless the user manually clicks the microphone.

### 1.7 ERROR State in `AlarmState` Unhandled in UI
- **Files**:
  - `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`
  - `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmActivity.kt` (Lines 192–206)
- **Rationale**: The `AlarmState.ERROR` enum is defined but never set anywhere in `AlarmViewModel`. Furthermore, `AlarmActivity`'s layout mapping has an `else -> {}` branch that omits `AlarmState.ERROR`. If the state machine transitions to `ERROR`, a blank central box is displayed to the user with no actions or status text.

### 1.8 State Machine Transitions
- **File**: `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`
- **Rationale**:
  - Normal Loop: `RINGING` -> `FETCHING_DATA` -> `SPEAKING` -> `LISTENING` -> `THINKING` -> `SPEAKING`.
  - Termination: User saying goodbye or clicking close -> `FINISHED`.
  - Fragilities: Coroutine failures during `dismissAndTalk()` (fetching weather, news, calendar, World Cup matches) or `processUserSpeech()` (Gemini network request) are unhandled. Any exception causes the coroutine to fail, leaving the UI stuck in `FETCHING_DATA` or `THINKING` permanently.

### 1.9 Goodbye Keywords and Timeout
- **File**: `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`
- **Lines**: 172–190, 267–283
- **Details**:
  - English goodbye words: `goodbye`, `exit`, `stop`, `close`, `bye`, `adios`
  - Spanish goodbye words: `adios`, `adiós`, `salir`, `terminar`, `chau`, `chao`, `cerrar`, `bye`, `goodbye`
  - Timeout: 2 minutes (120,000 ms) of silence triggers the agent prompt asking "Are you still there?". The coroutine job is safely canceled and restarted when speech begins or results are processed.

### 1.10 Audio Stream Restoration (Safe Restoration)
- **Files**:
  - `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt`
  - `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`
- **Rationale**: Streams (`STREAM_MUSIC`, `STREAM_SYSTEM`, `STREAM_NOTIFICATION`) must be safely restored via `unmuteBeep()` under all conditions: when listening stops (`stopListening()`), when the session is closed (`forceClose()`), and when the ViewModel is cleared (`onCleared()`). Currently, `forceClose()` does not call `stopSpeaking()`, and `stopListening()` lacks the `unmuteBeep()` call, creating multiple leaks where audio streams stay permanently muted.

---

## 2. Unit Testing in Codebase

### 2.1 Testing Infrastructure
- **Frameworks**:
  - **Unit Testing**: JUnit 4 (`org.junit`), Mockito (`org.mockito`), and Kotlin Coroutines Test library (`runBlocking`).
  - **UI Testing**: Compose UI Test Rule (`androidx.compose.ui.test.junit4`), AndroidJUnit4 runner, and AndroidX Test core libraries.
- **Gradle Tasks**:
  - Main task for running unit tests: `.\gradlew.bat testDebugUnitTest`
  - Main task for running connected Android/UI tests: `.\gradlew.bat connectedAndroidTest`

### 2.2 Status Verification
- A background build was successfully run for the unit tests:
  ```powershell
  .\gradlew.bat testDebugUnitTest
  ```
- **Result**: `BUILD SUCCESSFUL in 9s`. All tests compile and pass.
- **Note**: Currently, there are no mock unit tests for `VoiceManager` or `AlarmViewModel`. Testing coverage is concentrated on `GeminiAgentManagerTest`, `WorldCupRepositoryTest`, and `AlarmTimeCalculatorTest`.

---

## 3. Step-by-Step Fix Strategy

### 3.1 Step 1: Refactor `VoiceManager` for Thread-Safety and Session Management
Introduce session tracking and synchronization to resolve race conditions, the 600ms audio focus gap, and UI thread blocking:

#### Proposed `VoiceManager.kt` Changes:
```kotlin
package com.mateocuello.alarmai.data.repository

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.mateocuello.alarmai.data.local.PreferencesManager
import java.util.Locale

class VoiceManager(private val context: Context) {
    private val prefs = PreferencesManager(context)
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false
    
    private val ttsLock = Any()
    private var ttsCompleteCallback: (() -> Unit)? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isTtsActive = false
    private var isListeningActive = false
    private var isSessionActive = false // Session tracking to hold focus

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d("VoiceManager", "Audio focus lost permanently")
                stopSpeaking()
                stopListening()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d("VoiceManager", "Audio focus lost transiently")
                stopSpeaking()
                stopListening()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d("VoiceManager", "Audio focus gained")
            }
        }
    }

    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
        }

    fun startSession() {
        isSessionActive = true
        requestAudioFocus()
    }

    fun endSession() {
        isSessionActive = false
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        try {
            val result = audioManager.requestAudioFocus(focusRequest)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d("VoiceManager", "Audio focus request GRANTED")
            } else {
                Log.w("VoiceManager", "Audio focus request FAILED")
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error requesting audio focus: ${e.localizedMessage}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            val result = audioManager.abandonAudioFocusRequest(focusRequest)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d("VoiceManager", "Audio focus abandoned successfully")
            } else {
                Log.w("VoiceManager", "Failed to abandon audio focus")
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error abandoning audio focus: ${e.localizedMessage}")
        }
    }

    private fun checkAndAbandonFocus() {
        // Abandon only if no active conversation session and both modules are quiet
        if (!isSessionActive && !isTtsActive && !isListeningActive) {
            abandonAudioFocus()
        }
    }

    private var isBeepMuted = false

    private fun muteBeep() {
        if (!isBeepMuted) {
            try {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                isBeepMuted = true
                Log.d("VoiceManager", "Muted streams for speech recognition beep")
            } catch (e: Exception) {
                Log.e("VoiceManager", "Failed to mute streams: ${e.localizedMessage}")
            }
        }
    }

    private fun unmuteBeep() {
        if (isBeepMuted) {
            try {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                isBeepMuted = false
                Log.d("VoiceManager", "Unmuted streams after speech recognition")
            } catch (e: Exception) {
                Log.e("VoiceManager", "Failed to unmute streams: ${e.localizedMessage}")
            }
        }
    }

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val language = prefs.getLanguage()
                val locale = if (language == "es") Locale("es", "ES") else Locale.US
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("VoiceManager", "Language not supported")
                } else {
                    val savedVoice = prefs.getVoiceName()
                    if (savedVoice.isNotEmpty()) {
                        val voice = tts?.voices?.find { it.name == savedVoice }
                        if (voice != null) {
                            tts?.voice = voice
                        }
                    }
                    isTtsInitialized = true
                    setupTtsListener()
                }
            } else {
                Log.e("VoiceManager", "Initialization of TextToSpeech failed")
            }
        }
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("VoiceManager", "TTS Started speaking")
            }

            override fun onDone(utteranceId: String?) {
                Log.d("VoiceManager", "TTS Finished speaking")
                val callback = synchronized(ttsLock) {
                    val cb = ttsCompleteCallback
                    ttsCompleteCallback = null
                    isTtsActive = false
                    cb
                }
                callback?.invoke()
                checkAndAbandonFocus()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("VoiceManager", "TTS Error")
                val callback = synchronized(ttsLock) {
                    val cb = ttsCompleteCallback
                    ttsCompleteCallback = null
                    isTtsActive = false
                    cb
                }
                callback?.invoke()
                checkAndAbandonFocus()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("VoiceManager", "TTS Error: $errorCode")
                val callback = synchronized(ttsLock) {
                    val cb = ttsCompleteCallback
                    ttsCompleteCallback = null
                    isTtsActive = false
                    cb
                }
                callback?.invoke()
                checkAndAbandonFocus()
            }
        })
    }

    fun speak(text: String, onComplete: () -> Unit) {
        if (!isTtsInitialized || tts == null) {
            Log.e("VoiceManager", "TTS not initialized, speaking simulated")
            onComplete()
            return
        }
        requestAudioFocus()
        synchronized(ttsLock) {
            isTtsActive = true
            ttsCompleteCallback = onComplete
        }
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "alarm_briefing")
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "alarm_briefing")
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onRmsChanged: (Float) -> Unit
    ) {
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            // Check availability first
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("Speech recognition not available on this device")
                return@post
            }

            try {
                if (speechRecognizer != null) {
                    speechRecognizer?.destroy()
                }
                requestAudioFocus()
                isListeningActive = true

                // Mute beep BEFORE delayed creation
                muteBeep()
                
                // Use a non-blocking postDelayed instead of SystemClock.sleep(50)
                mainHandler.postDelayed({
                    if (!isListeningActive) return@postDelayed // Abort if stopped during delay
                    try {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

                        val language = prefs.getLanguage()
                        val locale = if (language == "es") Locale("es", "ES") else Locale.US
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
                            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, locale.toLanguageTag())
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        }

                        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                Log.d("VoiceManager", "Ready for speech (beep muted)")
                            }
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {
                                onRmsChanged(rmsdB)
                            }
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}

                            override fun onError(error: Int) {
                                unmuteBeep()
                                isListeningActive = false
                                checkAndAbandonFocus()
                                val errorMessage = when (error) {
                                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                    SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
                                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech engine busy"
                                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                                    else -> "Unknown recognizer error"
                                }
                                Log.e("VoiceManager", "Speech error: $errorMessage")
                                onError(errorMessage)
                            }

                            override fun onResults(results: Bundle?) {
                                unmuteBeep()
                                isListeningActive = false
                                checkAndAbandonFocus()
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull()
                                if (!text.isNullOrBlank()) {
                                    onResult(text)
                                } else {
                                    onError("Empty speech result")
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {}
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })

                        speechRecognizer?.startListening(intent)
                    } catch (e: Exception) {
                        unmuteBeep()
                        isListeningActive = false
                        checkAndAbandonFocus()
                        Log.e("VoiceManager", "Failed to start listening: ${e.localizedMessage}")
                        onError("Failed to start listening: ${e.localizedMessage}")
                    }
                }, 50)
            } catch (e: Exception) {
                unmuteBeep()
                isListeningActive = false
                checkAndAbandonFocus()
                Log.e("VoiceManager", "Failed to post listening initialization: ${e.localizedMessage}")
                onError("Failed to start listening: ${e.localizedMessage}")
            }
        }
    }

    fun stopSpeaking() {
        synchronized(ttsLock) {
            ttsCompleteCallback = null
            isTtsActive = false
        }
        tts?.stop()
        checkAndAbandonFocus()
    }

    fun stopListening() {
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            speechRecognizer?.stopListening()
            unmuteBeep() // Crucial restoration point
            isListeningActive = false
            checkAndAbandonFocus()
        }
    }

    fun shutdown() {
        isSessionActive = false
        unmuteBeep()
        synchronized(ttsLock) {
            ttsCompleteCallback = null
            isTtsActive = false
        }
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        isListeningActive = false
        abandonAudioFocus()
    }
}
```

---

### 3.2 Step 2: Implement Exception Handling and State Safety in `AlarmViewModel`
Ensure that the `ERROR` state is set whenever operations fail, the voice session is properly created/destroyed, and a retry strategy is available.

#### Proposed `AlarmViewModel.kt` Changes:
```kotlin
    fun dismissAndTalk() {
        viewModelScope.launch {
            try {
                // 1. Stop the alarm service ringtone
                getApplication<Application>().stopService(Intent(getApplication(), AlarmService::class.java))
                
                // Track voice session to hold audio focus smoothly
                voiceManager.startSession()

                val geminiKey = prefs.getGeminiKey()
                val modelName = prefs.getGeminiModel()
                
                // Check for valid cached pre-fetch briefing (less than 5 minutes old = 300,000 ms)
                val (cachedBriefing, cachedPrompt, cachedTime) = prefs.getPrefetchedBriefing()
                val isCacheValid = cachedBriefing.isNotEmpty() && (System.currentTimeMillis() - cachedTime < 300_000)
                
                if (isCacheValid) {
                    Log.d("AlarmViewModel", "Using valid prefetched briefing!")
                    geminiAgentManager.reconstructSession(
                        apiKey = geminiKey,
                        modelName = modelName,
                        prompt = cachedPrompt,
                        response = cachedBriefing
                    )
                    prefs.clearPrefetchedBriefing()
                    speakAgentResponse(cachedBriefing)
                } else {
                    Log.d("AlarmViewModel", "No valid prefetched briefing. Loading on demand.")
                    val isEs = prefs.getLanguage() == "es"
                    _uiState.value = AlarmState.FETCHING_DATA
                    _statusMessage.value = if (isEs) "Detectando ubicación..." else "Detecting location..."
                    
                    val (lat, lon) = prefetchedLocation
                        ?: locationProvider.getCurrentLocation()?.also { prefs.saveLocation(it.first, it.second) }
                        ?: prefs.getLocation()
                    
                    _statusMessage.value = if (isEs) "Obteniendo clima y noticias..." else "Fetching weather & news..."
                    val weatherData = weatherRepository.getWeather(lat, lon)
                    
                    val newsTopics = prefs.getNewsTopics()
                    val language = prefs.getLanguage()
                    val newsData = newsRepository.getNews(newsTopics, language)
                    
                    _statusMessage.value = if (isEs) "Leyendo agenda de hoy..." else "Reading today's schedule..."
                    val calendarData = calendarRepository.getTodayEvents()
                    
                    _statusMessage.value = if (isEs) "Buscando partidos del Mundial..." else "Checking World Cup matches..."
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val todayDateString = sdf.format(java.util.Date())
                    val worldCupRepository = com.mateocuello.alarmai.data.repository.WorldCupRepository()
                    val worldCupData = worldCupRepository.getTodayMatchesSummary(getApplication(), todayDateString)

                    _statusMessage.value = if (isEs) "Llamando a Gemini AI..." else "Calling Gemini AI..."
                    val initialBriefing = geminiAgentManager.startSession(
                        apiKey = geminiKey,
                        weatherData = weatherData,
                        newsData = newsData,
                        calendarData = calendarData,
                        worldCupData = worldCupData,
                        modelName = modelName
                    )
                    
                    speakAgentResponse(initialBriefing)
                }
            } catch (e: Exception) {
                Log.e("AlarmViewModel", "Fatal initialization error", e)
                val isEs = prefs.getLanguage() == "es"
                _statusMessage.value = if (isEs) "Error al iniciar: ${e.localizedMessage}" else "Error during initialization: ${e.localizedMessage}"
                _uiState.value = AlarmState.ERROR
            }
        }
    }

    private fun speakAgentResponse(text: String) {
        cancelNoSpeechTimeout()
        voiceManager.stopListening()
        
        _agentSpeech.value = text
        addChatMessage(MessageSender.AGENT, text)
        _uiState.value = AlarmState.SPEAKING
        _userSpeech.value = ""
        _micVolume.value = 0f
        
        voiceManager.speak(text) {
            // Callback when agent finishes speaking: add delay to let TTS fully release audio
            viewModelScope.launch {
                // Do not stopSpeaking() here as it nullifies callback (which is already executed)
                kotlinx.coroutines.delay(600)
                startListeningForUser()
            }
        }
    }

    private fun startListeningForUser() {
        voiceManager.stopSpeaking() // Ensure assistant is quiet when we start listening
        _uiState.value = AlarmState.LISTENING
        _micVolume.value = 0f
        startNoSpeechTimeout()
        
        voiceManager.startListening(
            onResult = { result ->
                cancelNoSpeechTimeout()
                consecutiveSttErrors = 0 // Reset on success
                _userSpeech.value = result
                _micVolume.value = 0f
                processUserSpeech(result)
            },
            onError = { error ->
                Log.e("AlarmViewModel", "STT Error ($consecutiveSttErrors): $error")
                _micVolume.value = 0f
                consecutiveSttErrors++
                if (consecutiveSttErrors < 5 && _uiState.value == AlarmState.LISTENING) {
                    // Retry with increasing delay to avoid spamming the recognizer
                    viewModelScope.launch {
                        val delay = 800L + (consecutiveSttErrors * 400L)
                        kotlinx.coroutines.delay(delay)
                        if (_uiState.value == AlarmState.LISTENING) {
                            startListeningForUser()
                        }
                    }
                } else {
                    Log.w("AlarmViewModel", "STT max retries reached, transitioning to ERROR")
                    val isEs = prefs.getLanguage() == "es"
                    _statusMessage.value = if (isEs) "No se pudo iniciar la entrada de voz." else "Could not start voice input."
                    _uiState.value = AlarmState.ERROR
                }
            },
            onRmsChanged = { rmsdB ->
                _micVolume.value = rmsdB
            }
        )
    }

    fun processUserSpeech(text: String) {
        cancelNoSpeechTimeout()
        voiceManager.stopListening()
        voiceManager.stopSpeaking()
        _micVolume.value = 0f

        addChatMessage(MessageSender.USER, text)

        // Goodbye logic
        val goodbyeKeywords = if (prefs.getLanguage() == "es") {
            listOf("adios", "adiós", "salir", "terminar", "chau", "chao", "cerrar", "bye", "goodbye")
        } else {
            listOf("goodbye", "exit", "stop", "close", "bye", "adios")
        }
        if (goodbyeKeywords.any { text.contains(it, ignoreCase = true) }) {
            viewModelScope.launch {
                val goodbyeMsg = if (prefs.getLanguage() == "es") "¡Que tengas un excelente día! Adiós." else "Have a great day ahead! Goodbye."
                _agentSpeech.value = goodbyeMsg
                addChatMessage(MessageSender.AGENT, goodbyeMsg)
                _uiState.value = AlarmState.SPEAKING
                voiceManager.speak(goodbyeMsg) {
                    voiceManager.endSession()
                    _uiState.value = AlarmState.FINISHED
                }
            }
            return
        }

        _uiState.value = AlarmState.THINKING
        viewModelScope.launch {
            try {
                val response = geminiAgentManager.sendMessage(text)
                speakAgentResponse(response)
            } catch (e: Exception) {
                Log.e("AlarmViewModel", "Error sending message to Gemini", e)
                val isEs = prefs.getLanguage() == "es"
                _statusMessage.value = if (isEs) "Error de conexión con Gemini." else "Connection error with Gemini."
                _uiState.value = AlarmState.ERROR
            }
        }
    }

    fun retry() {
        val hasMessages = _chatMessages.value.isNotEmpty()
        if (!hasMessages) {
            // Failed during initial loading, retry everything
            dismissAndTalk()
        } else {
            // Failed during chat turn, attempt to re-send the last user query
            val lastUserMessage = _chatMessages.value.lastOrNull { it.sender == MessageSender.USER }?.text
            if (lastUserMessage != null) {
                _uiState.value = AlarmState.THINKING
                viewModelScope.launch {
                    try {
                        val response = geminiAgentManager.sendMessage(lastUserMessage)
                        speakAgentResponse(response)
                    } catch (e: Exception) {
                        Log.e("AlarmViewModel", "Error during retry send", e)
                        val isEs = prefs.getLanguage() == "es"
                        _statusMessage.value = if (isEs) "Error de conexión con Gemini." else "Connection error with Gemini."
                        _uiState.value = AlarmState.ERROR
                    }
                }
            } else {
                // Fallback: manually trigger mic listening again
                startListeningManual()
            }
        }
    }

    fun forceClose() {
        cancelNoSpeechTimeout()
        voiceManager.stopSpeaking() // Ensure speech is killed
        voiceManager.stopListening()
        voiceManager.endSession()    // Releases audio focus
        _micVolume.value = 0f
        getApplication<Application>().stopService(Intent(getApplication(), AlarmService::class.java))
        _uiState.value = AlarmState.FINISHED
    }
```

---

### 3.3 Step 3: Implement Error State Layout in Compose UI
Handle the `AlarmState.ERROR` state in `AlarmActivity` and design a clear Error Layout screen.

#### Proposed `AlarmActivity.kt` Changes:
1. Handle `AlarmState.ERROR` inside `AlarmScreenContent`'s `AnimatedContent` (lines 192–206):
   ```kotlin
   when (targetState) {
       AlarmState.RINGING -> RingingLayout(onDismiss)
       AlarmState.FETCHING_DATA -> LoadingLayout(statusMessage)
       AlarmState.SPEAKING, AlarmState.LISTENING, AlarmState.THINKING -> {
           ChatLayout(
               chatMessages = chatMessages,
               state = targetState,
               userSpeech = userSpeech,
               micVolume = micVolume,
               onSendText = onSendText,
               onMicClick = onMicClick
           )
       }
       AlarmState.ERROR -> {
           ErrorLayout(
               status = statusMessage,
               onRetry = { viewModel.retry() },
               onClose = onClose
           )
       }
       else -> {}
   }
   ```

2. Add the `ErrorLayout` composable function at the end of the file:
   ```kotlin
   @Composable
   fun ErrorLayout(
       status: String,
       onRetry: () -> Unit,
       onClose: () -> Unit
   ) {
       Column(
           horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.Center,
           modifier = Modifier.padding(24.dp)
       ) {
           Icon(
               imageVector = Icons.Default.Close,
               contentDescription = "Error Icon",
               tint = MaterialTheme.colorScheme.error,
               modifier = Modifier.size(64.dp)
           )
           Spacer(modifier = Modifier.height(24.dp))
           Text(
               text = status,
               style = MaterialTheme.typography.bodyLarge,
               color = Color.White.copy(alpha = 0.8f),
               textAlign = TextAlign.Center
           )
           Spacer(modifier = Modifier.height(32.dp))
           Row(
               horizontalArrangement = Arrangement.spacedBy(16.dp),
               verticalAlignment = Alignment.CenterVertically
           ) {
               Button(
                   onClick = onClose,
                   colors = ButtonDefaults.buttonColors(
                       containerColor = Color.White.copy(alpha = 0.1f)
                   ),
                   shape = RoundedCornerShape(24.dp)
               ) {
                   Text("Close", color = Color.White)
               }
               Button(
                   onClick = onRetry,
                   colors = ButtonDefaults.buttonColors(
                       containerColor = MaterialTheme.colorScheme.error
                   ),
                   shape = RoundedCornerShape(24.dp)
               ) {
                   Text("Retry", color = Color.White)
               }
           }
       }
   }
   ```

---

## 4. Draft Layout for Updated `PROJECT.md`

We will append a fourth milestone to `PROJECT.md` details to track the Voice-to-Text and Text-to-Speech loop fixes.

```markdown
# Project: FIFA API Dynamic Integration & Voice Loop Optimization

## Architecture
AlarmAI is a Compose-based Android application. The World Cup matches feature is managed by `WorldCupRepository`, which is queried by `PrefetchWorker` and `GeminiAgentManager`.
The voice-to-text (STT) and text-to-speech (TTS) conversational loop is managed by `VoiceManager` and coordinate-driven state transitions inside `AlarmViewModel` which drives the UI in `AlarmActivity`.

```
+--------------------+       +--------------+       +---------------+
|    AlarmActivity   | <---> |AlarmViewModel| <---> |  VoiceManager |
| (Compose Chat UI)  |       | (Coordinator)|       | (TTS/STT/Focus|
+--------------------+       +--------------+       +---------------+
```

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | Exploration & API Discovery | Discovered the official FIFA World Cup matches endpoint and documented its schema/response. | None | DONE |
| 2 | WorldCupRepository Refactoring | Implement dynamic OkHttp requests in `WorldCupRepository`, parse API JSON responses, and preserve method signatures. | M1 | DONE |
| 3 | Test Adaptations & Validation | Update `WorldCupRepositoryTest` and `GeminiAgentManagerTest` to mock OkHttp responses and ensure 100% build and test pass. | M2 | DONE |
| 4 | Voice Loop & Error Safety | Fix UI blocking sleep, audio focus gaps, stream muting leaks, callback race conditions, unhandled ERROR UI state, and add error safety. | None | PROPOSED |

## Interface Contracts
### `WorldCupRepository` Public Interface
- `getMatchesForDate(context: Context, dateString: String): List<WorldCupMatch>`
- `getTodayMatchesSummary(context: Context, dateString: String): String`
- `getMatchesByTeam(context: Context, teamName: String): String`

### `VoiceManager` Public Interface
- `startSession()`
- `endSession()`
- `speak(text: String, onComplete: () -> Unit)`
- `startListening(onResult: (String) -> Unit, onError: (String) -> Unit, onRmsChanged: (Float) -> Unit)`
- `stopSpeaking()`
- `stopListening()`
- `shutdown()`

## Code Layout
- Repository: `app/src/main/java/com/mateocuello/alarmai/data/repository/WorldCupRepository.kt`
- Tests: `app/src/test/java/com/mateocuello/alarmai/data/repository/WorldCupRepositoryTest.kt`
- Manager: `app/src/main/java/com/mateocuello/alarmai/data/repository/GeminiAgentManager.kt`
- Voice Manager: `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt`
- ViewModel: `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`
- Activity: `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmActivity.kt`
```
