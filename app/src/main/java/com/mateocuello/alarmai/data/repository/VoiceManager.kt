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
    private var ttsCompleteCallback: (() -> Unit)? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isTtsActive = false
    private var isListeningActive = false

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
        if (!isTtsActive && !isListeningActive) {
            abandonAudioFocus()
        }
    }

    private var isBeepMuted = false

    private fun muteBeep() {
        if (!isBeepMuted) {
            try {
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
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
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                isBeepMuted = false
                Log.d("VoiceManager", "Unmuted streams after speech recognition beep")
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
                ttsCompleteCallback?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("VoiceManager", "TTS Error")
                ttsCompleteCallback?.invoke()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("VoiceManager", "TTS Error: $errorCode")
                ttsCompleteCallback?.invoke()
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
        isTtsActive = true
        ttsCompleteCallback = {
            isTtsActive = false
            checkAndAbandonFocus()
            onComplete()
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
        // Run on Main UI Thread because SpeechRecognizer must be created/called on the main thread
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer?.destroy()
                }
                requestAudioFocus()
                isListeningActive = true

                muteBeep()
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
                        unmuteBeep()
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
        }
    }

    fun stopSpeaking() {
        ttsCompleteCallback = null
        tts?.stop()
        isTtsActive = false
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
        unmuteBeep()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        isTtsActive = false
        isListeningActive = false
        abandonAudioFocus()
    }
}

