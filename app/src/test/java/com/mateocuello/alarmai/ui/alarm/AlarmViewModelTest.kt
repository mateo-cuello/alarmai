package com.mateocuello.alarmai.ui.alarm

import android.app.Application
import android.content.Context
import android.content.Intent
import com.mateocuello.alarmai.data.local.PreferencesManager
import com.mateocuello.alarmai.data.repository.CalendarRepository
import com.mateocuello.alarmai.data.repository.GeminiAgentManager
import com.mateocuello.alarmai.data.repository.LocationProvider
import com.mateocuello.alarmai.data.repository.NewsRepository
import com.mateocuello.alarmai.data.repository.VoiceManager
import com.mateocuello.alarmai.data.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var prefs: PreferencesManager

    @Mock
    private lateinit var locationProvider: LocationProvider

    @Mock
    private lateinit var weatherRepository: WeatherRepository

    @Mock
    private lateinit var newsRepository: NewsRepository

    @Mock
    private lateinit var calendarRepository: CalendarRepository

    @Mock
    private lateinit var geminiAgentManager: GeminiAgentManager

    @Mock
    private lateinit var voiceManager: VoiceManager

    private lateinit var logMock: MockedStatic<android.util.Log>
    private lateinit var intentConstruction: MockedConstruction<Intent>

    private lateinit var viewModel: AlarmViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)

        logMock = Mockito.mockStatic(android.util.Log::class.java)
        logMock.`when`<Int> { android.util.Log.d(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { android.util.Log.e(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { android.util.Log.e(any<String>(), any<String>(), any()) }.thenReturn(0)
        logMock.`when`<Int> { android.util.Log.w(any<String>(), any<String>()) }.thenReturn(0)

        intentConstruction = Mockito.mockConstruction(Intent::class.java) { mock, _ ->
            whenever(mock.putExtra(any<String>(), any<String>())).thenReturn(mock)
        }



        // Default preferences stubs
        whenever(prefs.getGeminiModel()).thenReturn("gemini-1.5-flash")
        whenever(prefs.getGeminiKey()).thenReturn("fake-key")
        whenever(prefs.getLanguage()).thenReturn("en")
        whenever(prefs.getPrefetchedBriefing()).thenReturn(Triple("", "", 0L))
        whenever(prefs.getNewsTopics()).thenReturn("tech")
        whenever(prefs.getLocation()).thenReturn(Pair(0.0, 0.0))

        // Default location provider stub (prefetchLocation is called in init)
        runBlocking {
            whenever(locationProvider.getCurrentLocation()).doReturn(null)
        }

        viewModel = AlarmViewModel(
            application = application,
            prefs = prefs,
            locationProvider = locationProvider,
            weatherRepository = weatherRepository,
            newsRepository = newsRepository,
            calendarRepository = calendarRepository,
            geminiAgentManager = geminiAgentManager,
            voiceManager = voiceManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        logMock.close()
        intentConstruction.close()
    }

    @Test
    fun testDismissAndTalk_UsesCachedBriefing() = runBlocking {
        whenever(prefs.getPrefetchedBriefing()).thenReturn(Triple("cached briefing", "cached prompt", System.currentTimeMillis()))

        viewModel.dismissAndTalk()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(voiceManager).startSession()
        verify(geminiAgentManager).reconstructSession(eq("fake-key"), eq("gemini-1.5-flash"), eq("cached prompt"), eq("cached briefing"))
        verify(prefs).clearPrefetchedBriefing()
        verify(voiceManager).speak(eq("cached briefing"), any())
        assertEquals(AlarmState.SPEAKING, viewModel.uiState.value)
    }

    @Test
    fun testDismissAndTalk_NoCache_FetchesDataAndStartsSession() = runBlocking {
        whenever(prefs.getPrefetchedBriefing()).thenReturn(Triple("", "", 0L))
        whenever(locationProvider.getCurrentLocation()).doReturn(Pair(10.0, 20.0))
        whenever(weatherRepository.getWeather(eq(10.0), eq(20.0))).doReturn("Clear sky, 20C")
        whenever(newsRepository.getNews(any(), any())).doReturn("News summary")
        whenever(calendarRepository.getTodayEvents()).doReturn("Events summary")
        whenever(geminiAgentManager.startSession(any(), eq("Clear sky, 20C"), eq("News summary"), eq("Events summary"), any())).doReturn("AI Greeting")

        viewModel.dismissAndTalk()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(voiceManager).startSession()
        verify(voiceManager).speak(eq("AI Greeting"), any())
        assertEquals(AlarmState.SPEAKING, viewModel.uiState.value)
    }

    @Test
    fun testDismissAndTalk_NoCache_UsesCachedLocation() = runBlocking {
        whenever(prefs.getPrefetchedBriefing()).thenReturn(Triple("", "", 0L))
        whenever(prefs.hasCachedLocation()).thenReturn(true)
        whenever(prefs.getLocation()).thenReturn(Pair(15.0, 25.0))
        whenever(weatherRepository.getWeather(eq(15.0), eq(25.0))).doReturn("Cloudy, 18C")
        whenever(newsRepository.getNews(any(), any())).doReturn("News summary")
        whenever(calendarRepository.getTodayEvents()).doReturn("Events summary")
        whenever(geminiAgentManager.startSession(any(), eq("Cloudy, 18C"), eq("News summary"), eq("Events summary"), any())).doReturn("AI Greeting")
        whenever(locationProvider.getCurrentLocation()).doReturn(Pair(16.0, 26.0))

        viewModel.dismissAndTalk()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(voiceManager).startSession()
        verify(voiceManager).speak(eq("AI Greeting"), any())
        assertEquals(AlarmState.SPEAKING, viewModel.uiState.value)
        // Verify that locationProvider.getCurrentLocation was called (once in init, once in background silent refresh)
        verify(locationProvider, times(2)).getCurrentLocation()
        // Verify that the new location got saved to cache
        verify(prefs, atLeastOnce()).saveLocation(eq(16.0), eq(26.0))
    }

    @Test
    fun testDismissAndTalk_ThrowsException_TransitionsToError() = runBlocking {
        whenever(prefs.getPrefetchedBriefing()).thenThrow(RuntimeException("Mock init exception"))

        viewModel.dismissAndTalk()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AlarmState.ERROR, viewModel.uiState.value)
        assertTrue(viewModel.statusMessage.value.contains("Mock init exception"))
    }

    @Test
    fun testNormalTransitionFlow() = runBlocking {
        // RINGING initially
        assertEquals(AlarmState.RINGING, viewModel.uiState.value)

        // Setup for dismissAndTalk without cache
        whenever(prefs.getPrefetchedBriefing()).thenReturn(Triple("", "", 0L))
        whenever(locationProvider.getCurrentLocation()).doReturn(Pair(10.0, 20.0))
        whenever(weatherRepository.getWeather(eq(10.0), eq(20.0))).doReturn("Clear sky")
        whenever(newsRepository.getNews(any(), any())).doReturn("News summary")
        whenever(calendarRepository.getTodayEvents()).doReturn("Events summary")
        whenever(geminiAgentManager.startSession(any(), eq("Clear sky"), eq("News summary"), eq("Events summary"), any())).doReturn("Greeting")

        viewModel.dismissAndTalk()
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Transitions to SPEAKING
        assertEquals(AlarmState.SPEAKING, viewModel.uiState.value)

        // Capture TTS speak callback
        val speakCallbackCaptor = argumentCaptor<() -> Unit>()
        verify(voiceManager).speak(eq("Greeting"), speakCallbackCaptor.capture())
        val speakCallback = speakCallbackCaptor.firstValue

        // Execute speak callback (simulates speech done)
        speakCallback.invoke()

        // Delay 600ms inside VM before listening starts
        testDispatcher.scheduler.advanceTimeBy(600)
        testDispatcher.scheduler.runCurrent()

        // 2. Transitions to LISTENING
        assertEquals(AlarmState.LISTENING, viewModel.uiState.value)

        // Capture startListening callbacks
        val onResultCaptor = argumentCaptor<(String) -> Unit>()
        val onErrorCaptor = argumentCaptor<(String) -> Unit>()
        val onRmsCaptor = argumentCaptor<(Float) -> Unit>()
        verify(voiceManager).startListening(onResultCaptor.capture(), onErrorCaptor.capture(), onRmsCaptor.capture())
        val onResultCallback = onResultCaptor.firstValue

        // Simulate user speech result
        whenever(geminiAgentManager.sendMessage(eq("user query"))).doReturn("response text")
        onResultCallback.invoke("user query")

        // 3. Transitions to THINKING
        assertEquals(AlarmState.THINKING, viewModel.uiState.value)

        testDispatcher.scheduler.advanceUntilIdle()

        // 4. Transitions back to SPEAKING
        assertEquals(AlarmState.SPEAKING, viewModel.uiState.value)
        verify(voiceManager).speak(eq("response text"), any())
    }

    @Test
    fun testRetryLogic_EmptyChat() = runBlocking {
        // Chat is empty, verify retry runs dismissAndTalk
        whenever(prefs.getPrefetchedBriefing()).thenReturn(Triple("cached briefing", "cached prompt", System.currentTimeMillis()))

        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(voiceManager).startSession()
        verify(voiceManager).speak(eq("cached briefing"), any())
    }

    @Test
    fun testRetryLogic_HasUserMessage() = runBlocking {
        // Add a message by initiating processUserSpeech
        whenever(geminiAgentManager.sendMessage(eq("hello"))).thenThrow(RuntimeException("network failure"))

        viewModel.processUserSpeech("hello")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AlarmState.ERROR, viewModel.uiState.value)

        // Now mock recovery
        doReturn("recovered response").whenever(geminiAgentManager).sendMessage(eq("hello"))

        viewModel.retry()
        assertEquals(AlarmState.THINKING, viewModel.uiState.value)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AlarmState.SPEAKING, viewModel.uiState.value)
        verify(voiceManager).speak(eq("recovered response"), any())
    }

    @Test
    fun testSttErrorRetryLogic() = runBlocking {
        // Force the app into LISTENING state
        whenever(prefs.getPrefetchedBriefing()).thenReturn(Triple("cached briefing", "cached prompt", System.currentTimeMillis()))
        viewModel.dismissAndTalk()
        testDispatcher.scheduler.advanceUntilIdle()

        val speakCallbackCaptor = argumentCaptor<() -> Unit>()
        verify(voiceManager).speak(eq("cached briefing"), speakCallbackCaptor.capture())
        speakCallbackCaptor.firstValue.invoke()
        testDispatcher.scheduler.advanceTimeBy(600)
        testDispatcher.scheduler.runCurrent()

        assertEquals(AlarmState.LISTENING, viewModel.uiState.value)

        val onResultCaptor = argumentCaptor<(String) -> Unit>()
        val onErrorCaptor = argumentCaptor<(String) -> Unit>()
        val onRmsCaptor = argumentCaptor<(Float) -> Unit>()
        verify(voiceManager, times(1)).startListening(onResultCaptor.capture(), onErrorCaptor.capture(), onRmsCaptor.capture())
        val onErrorCallback = onErrorCaptor.firstValue

        // Trigger STT error 1
        onErrorCallback.invoke("STT failed")
        testDispatcher.scheduler.runCurrent()

        // Verify that startListening is not immediately called again but scheduled with delay
        verify(voiceManager, times(1)).startListening(any(), any(), any())

        // Advance by retry delay (consecutiveSttErrors = 1 -> delay = 800 + 400 = 1200ms)
        testDispatcher.scheduler.advanceTimeBy(1200)
        testDispatcher.scheduler.runCurrent()

        // Verify startListening is called for a second time
        verify(voiceManager, times(2)).startListening(any(), any(), any())
    }

    @Test
    fun testSttErrorLimitReached() = runBlocking {
        whenever(prefs.getPrefetchedBriefing()).thenReturn(Triple("cached briefing", "cached prompt", System.currentTimeMillis()))
        viewModel.dismissAndTalk()
        testDispatcher.scheduler.advanceUntilIdle()

        val speakCallbackCaptor = argumentCaptor<() -> Unit>()
        verify(voiceManager).speak(eq("cached briefing"), speakCallbackCaptor.capture())
        speakCallbackCaptor.firstValue.invoke()
        testDispatcher.scheduler.advanceTimeBy(600)
        testDispatcher.scheduler.runCurrent()

        val onResultCaptor = argumentCaptor<(String) -> Unit>()
        val onErrorCaptor = argumentCaptor<(String) -> Unit>()
        val onRmsCaptor = argumentCaptor<(Float) -> Unit>()
        verify(voiceManager, times(1)).startListening(onResultCaptor.capture(), onErrorCaptor.capture(), onRmsCaptor.capture())
        val onErrorCallback = onErrorCaptor.firstValue

        // Trigger error 5 times
        // 1st error
        onErrorCallback.invoke("Err")
        testDispatcher.scheduler.advanceTimeBy(1200) // consecutive = 1 -> delay = 1200
        testDispatcher.scheduler.runCurrent()

        // 2nd error
        onErrorCallback.invoke("Err")
        testDispatcher.scheduler.advanceTimeBy(1600) // consecutive = 2 -> delay = 1600
        testDispatcher.scheduler.runCurrent()

        // 3rd error
        onErrorCallback.invoke("Err")
        testDispatcher.scheduler.advanceTimeBy(2000) // consecutive = 3 -> delay = 2000
        testDispatcher.scheduler.runCurrent()

        // 4th error
        onErrorCallback.invoke("Err")
        testDispatcher.scheduler.advanceTimeBy(2400) // consecutive = 4 -> delay = 2400
        testDispatcher.scheduler.runCurrent()

        // 5th error - this should hit the limit and transition to ERROR
        onErrorCallback.invoke("Err")
        testDispatcher.scheduler.runCurrent()

        assertEquals(AlarmState.ERROR, viewModel.uiState.value)
    }

    @Test
    fun testSpanishGoodbyeKeywords() = runBlocking {
        whenever(prefs.getLanguage()).thenReturn("es")

        viewModel.processUserSpeech("adiós y gracias")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(voiceManager).speak(eq("¡Que tengas un excelente día! Adiós."), any())
        
        // Capture goodbye speak callback to trigger end session
        val goodbyeCallbackCaptor = argumentCaptor<() -> Unit>()
        verify(voiceManager).speak(eq("¡Que tengas un excelente día! Adiós."), goodbyeCallbackCaptor.capture())
        goodbyeCallbackCaptor.firstValue.invoke()

        verify(voiceManager).endSession()
        assertEquals(AlarmState.FINISHED, viewModel.uiState.value)
    }

    @Test
    fun testEnglishGoodbyeKeywords() = runBlocking {
        whenever(prefs.getLanguage()).thenReturn("en")

        viewModel.processUserSpeech("exit now")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(voiceManager).speak(eq("Have a great day ahead! Goodbye."), any())

        // Capture goodbye speak callback to trigger end session
        val goodbyeCallbackCaptor = argumentCaptor<() -> Unit>()
        verify(voiceManager).speak(eq("Have a great day ahead! Goodbye."), goodbyeCallbackCaptor.capture())
        goodbyeCallbackCaptor.firstValue.invoke()

        verify(voiceManager).endSession()
        assertEquals(AlarmState.FINISHED, viewModel.uiState.value)
    }

    @Test
    fun testNoSpeechTimeout() = runBlocking {
        whenever(prefs.getPrefetchedBriefing()).thenReturn(Triple("briefing", "prompt", System.currentTimeMillis()))
        viewModel.dismissAndTalk()
        testDispatcher.scheduler.advanceUntilIdle()

        // Transition from speaking to listening
        val speakCallbackCaptor = argumentCaptor<() -> Unit>()
        verify(voiceManager).speak(eq("briefing"), speakCallbackCaptor.capture())
        speakCallbackCaptor.firstValue.invoke()
        testDispatcher.scheduler.advanceTimeBy(600)
        testDispatcher.scheduler.runCurrent()

        assertEquals(AlarmState.LISTENING, viewModel.uiState.value)

        // Advance time by 2 minutes (120,000 ms) to trigger timeout
        testDispatcher.scheduler.advanceTimeBy(120_000)
        testDispatcher.scheduler.runCurrent()

        verify(voiceManager).speak(eq("I haven't heard anything. Are you still there?"), any())
    }

    @Test
    fun testForceClose() = runBlocking {
        viewModel.forceClose()

        verify(voiceManager).stopSpeaking()
        verify(voiceManager).stopListening()
        verify(voiceManager).endSession()
        assertEquals(AlarmState.FINISHED, viewModel.uiState.value)
    }

    @Test
    fun testOnCleared() = runBlocking {
        val method = AlarmViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)

        verify(voiceManager).shutdown()
        verify(geminiAgentManager).clearSession()
    }
}
