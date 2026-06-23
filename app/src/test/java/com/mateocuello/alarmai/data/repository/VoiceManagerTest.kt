package com.mateocuello.alarmai.data.repository

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.mateocuello.alarmai.data.local.PreferencesManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify

class VoiceManagerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var prefs: PreferencesManager

    @Mock
    private lateinit var audioManager: AudioManager

    @Mock
    private lateinit var looper: android.os.Looper

    @Mock
    private lateinit var mockTts: TextToSpeech

    @Mock
    private lateinit var mockSpeechRecognizer: SpeechRecognizer

    private lateinit var logMock: MockedStatic<android.util.Log>
    private lateinit var srMockStatic: MockedStatic<SpeechRecognizer>
    private lateinit var handlerConstruction: MockedConstruction<android.os.Handler>
    private lateinit var audioAttributesBuilderConstruction: MockedConstruction<AudioAttributes.Builder>
    private lateinit var audioFocusRequestBuilderConstruction: MockedConstruction<AudioFocusRequest.Builder>
    private lateinit var intentConstruction: MockedConstruction<Intent>
    private lateinit var bundleConstruction: MockedConstruction<Bundle>

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        logMock = Mockito.mockStatic(android.util.Log::class.java)
        logMock.`when`<Int> { android.util.Log.d(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { android.util.Log.e(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { android.util.Log.e(any<String>(), any<String>(), any()) }.thenReturn(0)
        logMock.`when`<Int> { android.util.Log.w(any<String>(), any<String>()) }.thenReturn(0)

        srMockStatic = Mockito.mockStatic(SpeechRecognizer::class.java)

        handlerConstruction = Mockito.mockConstruction(android.os.Handler::class.java) { mock, _ ->
            whenever(mock.post(any())).thenAnswer { invocation ->
                val runnable = invocation.getArgument<Runnable>(0)
                runnable.run()
                true
            }
            whenever(mock.postDelayed(any(), any())).thenAnswer { invocation ->
                val runnable = invocation.getArgument<Runnable>(0)
                runnable.run()
                true
            }
        }

        val mockAudioAttributes = mock<AudioAttributes>()
        audioAttributesBuilderConstruction = Mockito.mockConstruction(AudioAttributes.Builder::class.java) { mock, _ ->
            whenever(mock.setUsage(any())).thenReturn(mock)
            whenever(mock.setContentType(any())).thenReturn(mock)
            whenever(mock.build()).thenReturn(mockAudioAttributes)
        }

        val mockAudioFocusRequest = mock<AudioFocusRequest>()
        audioFocusRequestBuilderConstruction = Mockito.mockConstruction(AudioFocusRequest.Builder::class.java) { mock, _ ->
            whenever(mock.setAudioAttributes(any())).thenReturn(mock)
            whenever(mock.setOnAudioFocusChangeListener(any())).thenReturn(mock)
            whenever(mock.build()).thenReturn(mockAudioFocusRequest)
        }

        intentConstruction = Mockito.mockConstruction(Intent::class.java) { mock, _ ->
            whenever(mock.putExtra(any<String>(), any<String>())).thenReturn(mock)
            whenever(mock.putExtra(any<String>(), anyInt())).thenReturn(mock)
        }

        bundleConstruction = Mockito.mockConstruction(Bundle::class.java) { _, _ -> }

        whenever(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(audioManager)
        whenever(context.mainLooper).thenReturn(looper)
        whenever(prefs.getLanguage()).thenReturn("en")
        whenever(prefs.getVoiceName()).thenReturn("")
    }

    @After
    fun tearDown() {
        logMock.close()
        srMockStatic.close()
        handlerConstruction.close()
        audioAttributesBuilderConstruction.close()
        audioFocusRequestBuilderConstruction.close()
        intentConstruction.close()
        bundleConstruction.close()
    }

    @Test
    fun testTtsInitializationSuccess() {
        var capturedListener: TextToSpeech.OnInitListener? = null

        VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, listener ->
                capturedListener = listener
                mockTts
            },
            speechRecognizerFactory = { mockSpeechRecognizer }
        )

        assertNotNull(capturedListener)
        capturedListener?.onInit(TextToSpeech.SUCCESS)

        verify(mockTts).setLanguage(any())
    }

    @Test
    fun testTtsInitializationFailure() {
        var capturedListener: TextToSpeech.OnInitListener? = null

        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, listener ->
                capturedListener = listener
                mockTts
            },
            speechRecognizerFactory = { mockSpeechRecognizer }
        )

        assertNotNull(capturedListener)
        capturedListener?.onInit(TextToSpeech.ERROR)

        var callbackCalled = false
        voiceManager.speak("Hello") {
            callbackCalled = true
        }
        assertTrue(callbackCalled)
    }

    @Test
    fun testSpeakAndCallbackExecutionFlow() {
        var initListener: TextToSpeech.OnInitListener? = null

        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, listener ->
                initListener = listener
                mockTts
            },
            speechRecognizerFactory = { mockSpeechRecognizer }
        )

        initListener?.onInit(TextToSpeech.SUCCESS)

        val progressListenerCaptor = ArgumentCaptor.forClass(UtteranceProgressListener::class.java)
        verify(mockTts).setOnUtteranceProgressListener(progressListenerCaptor.capture())
        val progressListener = progressListenerCaptor.value

        var callbackCalled = false
        voiceManager.speak("Hello") {
            callbackCalled = true
        }

        verify(mockTts).speak(eq("Hello"), eq(TextToSpeech.QUEUE_FLUSH), any(), eq("alarm_briefing"))

        progressListener.onDone("alarm_briefing")
        assertTrue(callbackCalled)
    }

    @Test
    fun testStopSpeaking() {
        var initListener: TextToSpeech.OnInitListener? = null
        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, listener ->
                initListener = listener
                mockTts
            },
            speechRecognizerFactory = { mockSpeechRecognizer }
        )
        initListener?.onInit(TextToSpeech.SUCCESS)

        voiceManager.stopSpeaking()
        verify(mockTts).stop()
    }

    @Test
    fun testStartListeningCheckAvailability() {
        srMockStatic.`when`<Boolean> { SpeechRecognizer.isRecognitionAvailable(any()) }.thenReturn(false)

        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, _ -> mockTts },
            speechRecognizerFactory = { mockSpeechRecognizer }
        )

        var errorMsg: String? = null
        voiceManager.startListening(
            onResult = {},
            onError = { errorMsg = it },
            onRmsChanged = {}
        )

        assertEquals("Speech recognition not available on this device", errorMsg)
    }

    @Test
    fun testStartListeningCreationAndInteraction() {
        srMockStatic.`when`<Boolean> { SpeechRecognizer.isRecognitionAvailable(any()) }.thenReturn(true)

        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, _ -> mockTts },
            speechRecognizerFactory = { mockSpeechRecognizer }
        )

        voiceManager.startListening(
            onResult = {},
            onError = {},
            onRmsChanged = {}
        )

        verify(mockSpeechRecognizer).setRecognitionListener(any())
        verify(mockSpeechRecognizer).startListening(any())
    }

    @Test
    fun testSpeechRecognizerOnResults() {
        srMockStatic.`when`<Boolean> { SpeechRecognizer.isRecognitionAvailable(any()) }.thenReturn(true)

        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, _ -> mockTts },
            speechRecognizerFactory = { mockSpeechRecognizer }
        )

        var resultText: String? = null
        voiceManager.startListening(
            onResult = { resultText = it },
            onError = {},
            onRmsChanged = {}
        )

        val listenerCaptor = ArgumentCaptor.forClass(RecognitionListener::class.java)
        verify(mockSpeechRecognizer).setRecognitionListener(listenerCaptor.capture())
        val listener = listenerCaptor.value

        val bundle = mock<Bundle>()
        val matches = arrayListOf("hello user")
        whenever(bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)).thenReturn(matches)

        listener.onResults(bundle)

        assertEquals("hello user", resultText)
        verify(audioManager).adjustStreamVolume(eq(AudioManager.STREAM_MUSIC), eq(AudioManager.ADJUST_UNMUTE), anyInt())
    }

    @Test
    fun testSpeechRecognizerOnError() {
        srMockStatic.`when`<Boolean> { SpeechRecognizer.isRecognitionAvailable(any()) }.thenReturn(true)

        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, _ -> mockTts },
            speechRecognizerFactory = { mockSpeechRecognizer }
        )

        var errorMsg: String? = null
        voiceManager.startListening(
            onResult = {},
            onError = { errorMsg = it },
            onRmsChanged = {}
        )

        val listenerCaptor = ArgumentCaptor.forClass(RecognitionListener::class.java)
        verify(mockSpeechRecognizer).setRecognitionListener(listenerCaptor.capture())
        val listener = listenerCaptor.value

        listener.onError(SpeechRecognizer.ERROR_NO_MATCH)

        assertEquals("No speech match found", errorMsg)
        verify(audioManager).adjustStreamVolume(eq(AudioManager.STREAM_MUSIC), eq(AudioManager.ADJUST_UNMUTE), anyInt())
    }

    @Test
    fun testStopListening() {
        srMockStatic.`when`<Boolean> { SpeechRecognizer.isRecognitionAvailable(any()) }.thenReturn(true)

        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, _ -> mockTts },
            speechRecognizerFactory = { mockSpeechRecognizer }
        )

        voiceManager.startListening(
            onResult = {},
            onError = {},
            onRmsChanged = {}
        )

        voiceManager.stopListening()

        verify(mockSpeechRecognizer).stopListening()
        verify(audioManager).adjustStreamVolume(eq(AudioManager.STREAM_MUSIC), eq(AudioManager.ADJUST_UNMUTE), anyInt())
    }

    @Test
    fun testMuteBeepAndUnmuteBeep() {
        srMockStatic.`when`<Boolean> { SpeechRecognizer.isRecognitionAvailable(any()) }.thenReturn(true)

        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, _ -> mockTts },
            speechRecognizerFactory = { mockSpeechRecognizer }
        )

        voiceManager.startListening(
            onResult = {},
            onError = {},
            onRmsChanged = {}
        )

        verify(audioManager).adjustStreamVolume(eq(AudioManager.STREAM_MUSIC), eq(AudioManager.ADJUST_MUTE), eq(0))
        verify(audioManager).adjustStreamVolume(eq(AudioManager.STREAM_SYSTEM), eq(AudioManager.ADJUST_MUTE), eq(0))
        verify(audioManager).adjustStreamVolume(eq(AudioManager.STREAM_NOTIFICATION), eq(AudioManager.ADJUST_MUTE), eq(0))

        voiceManager.stopListening()

        verify(audioManager).adjustStreamVolume(eq(AudioManager.STREAM_MUSIC), eq(AudioManager.ADJUST_UNMUTE), eq(0))
        verify(audioManager).adjustStreamVolume(eq(AudioManager.STREAM_SYSTEM), eq(AudioManager.ADJUST_UNMUTE), eq(0))
        verify(audioManager).adjustStreamVolume(eq(AudioManager.STREAM_NOTIFICATION), eq(AudioManager.ADJUST_UNMUTE), eq(0))
    }

    @Test
    fun testShutdown() {
        val voiceManager = VoiceManager(
            context = context,
            prefs = prefs,
            ttsFactory = { _, _ -> mockTts },
            speechRecognizerFactory = { mockSpeechRecognizer }
        )
        srMockStatic.`when`<Boolean> { SpeechRecognizer.isRecognitionAvailable(any()) }.thenReturn(true)
        voiceManager.startListening(
            onResult = {},
            onError = {},
            onRmsChanged = {}
        )

        voiceManager.shutdown()

        verify(mockTts).stop()
        verify(mockTts).shutdown()
        verify(mockSpeechRecognizer).destroy()
        verify(audioManager, Mockito.atLeastOnce()).abandonAudioFocusRequest(any())
    }
}
