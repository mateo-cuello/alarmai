package com.mateocuello.alarmai.data.repository

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class VoiceManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false
    private var ttsCompleteCallback: (() -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("VoiceManager", "Language not supported")
                } else {
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
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("VoiceManager", "TTS Error: $errorCode")
            }
        })
    }

    fun speak(text: String, onComplete: () -> Unit) {
        if (!isTtsInitialized || tts == null) {
            Log.e("VoiceManager", "TTS not initialized, speaking simulated")
            onComplete()
            return
        }
        ttsCompleteCallback = onComplete
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "alarm_briefing")
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "alarm_briefing")
    }

    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        // Run on Main UI Thread because SpeechRecognizer must be created/called on the main thread
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer?.destroy()
                }
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
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
                Log.e("VoiceManager", "Failed to start listening: ${e.localizedMessage}")
                onError("Failed to start listening: ${e.localizedMessage}")
            }
        }
    }

    fun stopListening() {
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            speechRecognizer?.stopListening()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
