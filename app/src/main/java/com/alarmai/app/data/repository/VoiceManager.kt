package com.alarmai.app.data.repository

import android.annotation.SuppressLint
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
import com.alarmai.app.data.local.PreferencesManager
import java.util.Locale
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest

class VoiceManager(
    private val context: Context,
    private val prefs: PreferencesManager = PreferencesManager(context),
    private val ttsFactory: (Context, TextToSpeech.OnInitListener) -> TextToSpeech = { ctx, listener -> TextToSpeech(ctx, listener) },
    private val speechRecognizerFactory: (Context) -> SpeechRecognizer = { ctx ->
        SpeechRecognizer.createSpeechRecognizer(ctx)
    }
) {
    companion object {
        internal var sdkVersionProvider: () -> Int = { android.os.Build.VERSION.SDK_INT }

        /** Streams silenced to suppress the SpeechRecognizer beep. See [mutedStreams]. */
        private val BEEP_STREAMS = intArrayOf(AudioManager.STREAM_SYSTEM, AudioManager.STREAM_NOTIFICATION)

        /**
         * Clears a mute left behind by a process that was killed mid-listen, which would otherwise
         * leave the device's system/notification volume at zero with no way for the user to know
         * why. Call on app start. Cheap and self-contained: does not construct a [VoiceManager]
         * (and so does not spin up a TTS engine).
         */
        fun recoverStaleMute(context: Context) {
            try {
                val prefs = PreferencesManager(context)
                if (!prefs.wasBeepMuted()) return
                Log.w("VoiceManager", "Recovering stale beep mute from a previous session")
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                for (stream in BEEP_STREAMS) {
                    try {
                        audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                    } catch (e: Exception) {
                        Log.w("VoiceManager", "Failed to unmute stream $stream: ${e.localizedMessage}")
                    }
                }
                prefs.saveBeepMuted(false)
            } catch (e: Exception) {
                Log.e("VoiceManager", "Failed to recover stale mute: ${e.localizedMessage}")
            }
        }
    }

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false
    
    private val ttsLock = Any()
    private var ttsCompleteCallback: (() -> Unit)? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isTtsActive = false
    private var isListeningActive = false
    private var isSessionActive = false // Session tracking to hold focus
    var onSessionInterrupted: (() -> Unit)? = null

    private var isBeepMuted = false

    /**
     * Streams silenced to suppress the SpeechRecognizer's start/stop beep.
     *
     * Deliberately excludes STREAM_MUSIC: that is the stream Android TTS renders on, so muting it
     * both swallowed the start of every spoken reply and left the user's media volume at zero if
     * the process died mid-listen.
     */
    private val mutedStreams = BEEP_STREAMS

    private fun adjustMutedStreams(direction: Int) {
        for (stream in mutedStreams) {
            try {
                audioManager.adjustStreamVolume(stream, direction, 0)
            } catch (e: Exception) {
                Log.w("VoiceManager", "Failed to adjust stream $stream: ${e.localizedMessage}")
            }
        }
    }

    private fun muteBeep() {
        if (isBeepMuted) return
        adjustMutedStreams(AudioManager.ADJUST_MUTE)
        isBeepMuted = true
        // Persisted so a process kill mid-listen can be recovered from on next launch; the
        // in-memory flag alone left the device silent with no path back.
        prefs.saveBeepMuted(true)
        Log.d("VoiceManager", "Muted streams for speech recognition beep")
    }

    private fun unmuteBeep() {
        if (!isBeepMuted) return
        adjustMutedStreams(AudioManager.ADJUST_UNMUTE)
        isBeepMuted = false
        prefs.saveBeepMuted(false)
        Log.d("VoiceManager", "Unmuted streams after speech recognition")
    }


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
        unmuteBeep()
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d("VoiceManager", "Audio focus lost permanently")
                stopSpeaking()
                stopListening()
                onSessionInterrupted?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d("VoiceManager", "Audio focus lost transiently, ignoring to preserve STT")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d("VoiceManager", "Audio focus gained")
            }
        }
    }

    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
    }

    /**
     * Prefers the on-device recognizer on API 33+ when one is installed. It keeps STT working when
     * the phone has no network yet at wake-up time, which is the single biggest source of
     * ERROR_NETWORK / ERROR_NETWORK_TIMEOUT during a morning alarm.
     */
    // Suppressed because sdkVersionProvider() is the version guard: it is an injectable seam so
    // tests can drive both branches, and lint cannot follow it the way it follows Build.VERSION.
    @SuppressLint("NewApi")
    private fun createRecognizer(): SpeechRecognizer {
        if (sdkVersionProvider() >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            Log.d("VoiceManager", "Using on-device speech recognizer")
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }
        return speechRecognizerFactory(context)
    }

    @SuppressLint("NewApi")
    private fun isUsingOnDeviceRecognizer(): Boolean =
        sdkVersionProvider() >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun startSession() {
        isSessionActive = true
        requestAudioFocus()
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = createRecognizer()
            }
        }
    }

    fun endSession() {
        isSessionActive = false
        abandonAudioFocus()
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
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

    init {
        initTts()
    }

    private fun initTts() {
        tts = ttsFactory(context) { status ->
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
        // Synchronous, unlike stopListening()'s handler-posted unmute, so speech never starts
        // against streams this session muted.
        unmuteBeep()
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
            unmuteBeep()
            speechRecognizer?.stopListening()
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
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            cleanupSpeechRecognizer()
        }
        isListeningActive = false
        abandonAudioFocus()
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onRmsChanged: (Float) -> Unit
    ) {
        // Check if speech recognition is available on device
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("VoiceManager", "Speech recognition not available on this device")
            onError("Speech recognition not available on this device")
            return
        }
        // Verify runtime RECORD_AUDIO permission (Android 6+)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("VoiceManager", "RECORD_AUDIO permission not granted")
            onError("Microphone permission not granted")
            return
        }

        // Always destroy the old recognizer to avoid reusing a corrupted instance
        cleanupSpeechRecognizer()

        // Request audio focus once
        requestAudioFocus()
        muteBeep()

        // Create a fresh recognizer on the main thread
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            try {
                speechRecognizer = createRecognizer()
                isListeningActive = true
                Log.d("VoiceManager", "SpeechRecognizer created, setting up listener")

                val listener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("VoiceManager", "onReadyForSpeech – mic is active")
                    }
                    override fun onBeginningOfSpeech() {
                        Log.d("VoiceManager", "onBeginningOfSpeech – user started talking")
                    }
                    override fun onRmsChanged(rmsdB: Float) {
                        onRmsChanged(rmsdB)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        Log.d("VoiceManager", "onEndOfSpeech – user stopped talking")
                    }
                    override fun onError(error: Int) {
                        Log.e("VoiceManager", "RecognitionListener.onError: $error")
                        isListeningActive = false
                        unmuteBeep()
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
                            SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                            else -> "Speech recognition error: $error"
                        }
                        onError(msg)
                    }
                    override fun onResults(results: Bundle?) {
                        Log.d("VoiceManager", "onResults received")
                        isListeningActive = false
                        unmuteBeep()
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        onResult(text)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }

                speechRecognizer?.setRecognitionListener(listener)

                // Build the intent with the user's language preference
                val language = prefs.getLanguage()
                val languageTag = if (language == "es") "es-ES" else "en-US"
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    if (isUsingOnDeviceRecognizer()) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }

                Log.d("VoiceManager", "Calling speechRecognizer.startListening (lang=$languageTag)")
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("VoiceManager", "Failed to create/start SpeechRecognizer: ${e.localizedMessage}", e)
                isListeningActive = false
                unmuteBeep()
                onError("Failed to start speech recognizer: ${e.localizedMessage}")
            }
        }
    }
}
