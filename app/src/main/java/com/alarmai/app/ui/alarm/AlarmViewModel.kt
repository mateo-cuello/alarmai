package com.alarmai.app.ui.alarm

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alarmai.app.data.local.PreferencesManager
import com.alarmai.app.data.repository.CalendarRepository
import com.alarmai.app.data.repository.GeminiAgentManager
import com.alarmai.app.data.repository.LocationProvider
import com.alarmai.app.data.repository.NewsRepository
import com.alarmai.app.data.repository.VoiceManager
import com.alarmai.app.data.repository.WeatherRepository
import com.alarmai.app.service.AlarmService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

enum class AlarmState {
    RINGING,
    FETCHING_DATA,
    SPEAKING,
    LISTENING,
    THINKING,
    ERROR,
    FINISHED
}

enum class MessageSender {
    USER,
    AGENT
}

data class ChatMessage(
    val sender: MessageSender,
    val text: String
)

class AlarmViewModel @JvmOverloads constructor(
    application: Application,
    private val prefs: PreferencesManager = PreferencesManager(application),
    private val locationProvider: LocationProvider = LocationProvider(application),
    private val weatherRepository: WeatherRepository = WeatherRepository(),
    private val newsRepository: NewsRepository = NewsRepository(),
    private val calendarRepository: CalendarRepository = CalendarRepository(application),
    private val geminiAgentManager: GeminiAgentManager = GeminiAgentManager(prefs),
    private val voiceManager: VoiceManager = VoiceManager(application, prefs)
) : AndroidViewModel(application) {

    private var noSpeechTimeoutJob: kotlinx.coroutines.Job? = null

    /**
     * The delayed "start listening after TTS finishes" job. Tracked so typed input or a quick
     * reply can cancel it; otherwise it fires mid-turn and reopens the mic while the agent is
     * already speaking, feeding the agent's own voice back in as user input.
     */
    private var postSpeechJob: kotlinx.coroutines.Job? = null

    /** Guards against a second concurrent session (e.g. a rotation re-running dismissAndTalk). */
    private var sessionJob: kotlinx.coroutines.Job? = null

    private var consecutiveSttErrors = 0
    private var noSpeechNudges = 0
    private var wasCachedLocationUsed: Boolean = false

    private companion object {
        const val MAX_STT_ERRORS = 5
        /** How many "are you still there?" prompts before giving up and ending the session. */
        const val MAX_NO_SPEECH_NUDGES = 2
    }

    private val _geminiModelName = MutableStateFlow(prefs.getGeminiModel())
    val geminiModelName: StateFlow<String> = _geminiModelName

    private val _uiState = MutableStateFlow(AlarmState.RINGING)
    val uiState: StateFlow<AlarmState> = _uiState

    private var prefetchedLocation: Pair<Double, Double>? = null

    init {
        prefetchLocation()
        voiceManager.onSessionInterrupted = {
            val isEs = prefs.getLanguage() == "es"
            _statusMessage.value = if (isEs) "Sesión de voz interrumpida por otra aplicación." else "Voice session interrupted by another application."
            _uiState.value = AlarmState.ERROR
        }
    }

    private fun prefetchLocation() {
        viewModelScope.launch {
            try {
                val location = locationProvider.getCurrentLocation()
                if (location != null) {
                    prefetchedLocation = location
                    prefs.saveLocation(location.first, location.second)
                    Log.d("AlarmViewModel", "Prefetched and saved location: $location")
                }
            } catch (e: Exception) {
                Log.e("AlarmViewModel", "Error prefetching location: ${e.localizedMessage}")
            }
        }
    }

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    private fun addChatMessage(sender: MessageSender, text: String) {
        if (text.isBlank()) return
        val currentList = _chatMessages.value
        if (currentList.isNotEmpty() && currentList.last().sender == sender && currentList.last().text == text) {
            return
        }
        _chatMessages.value = currentList + ChatMessage(sender, text)
    }

    private val _agentSpeech = MutableStateFlow("Tap 'Dismiss & Talk' to start your day.")
    val agentSpeech: StateFlow<String> = _agentSpeech

    private val _userSpeech = MutableStateFlow("")
    val userSpeech: StateFlow<String> = _userSpeech

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _micVolume = MutableStateFlow(0f)
    val micVolume: StateFlow<Float> = _micVolume

    fun dismissAndTalk() {
        // Activity recreation (rotation, fold) re-delivers the same launch intent, and retry()
        // can also land here. Without this guard a second session races the first on _uiState.
        if (sessionJob?.isActive == true) {
            Log.d("AlarmViewModel", "dismissAndTalk ignored: a session is already starting")
            return
        }
        wasCachedLocationUsed = false
        sessionJob = viewModelScope.launch {
            try {
                // 1. Stop the alarm service ringtone
                getApplication<Application>().stopService(Intent(getApplication(), AlarmService::class.java))
                
                // Track voice session to hold audio focus smoothly
                voiceManager.startSession()

                val geminiKey = prefs.getGeminiKey()
                val modelName = prefs.getGeminiModel()
                
                // Check for valid cached pre-fetch briefing (less than 30 minutes old = 1,800,000 ms)
                val (cachedBriefing, cachedPrompt, cachedTime) = prefs.getPrefetchedBriefing()
                val isCacheValid = cachedBriefing.isNotEmpty() && (System.currentTimeMillis() - cachedTime < 1800_000)
                
                if (isCacheValid) {
                    Log.d("AlarmViewModel", "Using valid prefetched briefing!")
                    // Reconstruct the Gemini session with the prompt and briefing history
                    geminiAgentManager.reconstructSession(
                        apiKey = geminiKey,
                        modelName = modelName,
                        prompt = cachedPrompt,
                        response = cachedBriefing
                    )
                    // Clear the cache so it's not reused next time
                    prefs.clearPrefetchedBriefing()
                    // Speak immediately!
                    speakAgentResponse(cachedBriefing)
                } else {
                    Log.d("AlarmViewModel", "No valid prefetched briefing. Loading on demand.")
                    // 2. Fetch data & start AI agent
                    val isEs = prefs.getLanguage() == "es"
                    _uiState.value = AlarmState.FETCHING_DATA
                    _statusMessage.value = if (isEs) "Detectando ubicación..." else "Detecting location..."
                    
                    // Get location coordinates (either pre-fetched or fetch on demand)
                    val (lat, lon) = if (prefs.hasCachedLocation()) {
                        wasCachedLocationUsed = true
                        prefs.getLocation()
                    } else {
                        wasCachedLocationUsed = false
                        val location = withTimeoutOrNull(3000) { locationProvider.getCurrentLocation() }
                        location?.also { prefs.saveLocation(it.first, it.second) }
                            ?: prefs.getLocation()
                    }
                    
                    val newsTopics = prefs.getNewsTopics()
                    val language = prefs.getLanguage()

                    _statusMessage.value = if (isEs) "Obteniendo datos..." else "Fetching data..."
                    
                    val weatherDeferred = async { weatherRepository.getWeather(lat, lon) }
                    val newsDeferred = async { newsRepository.getNews(newsTopics, language) }
                    val calendarDeferred = async { calendarRepository.getTodayEvents() }

                    val weatherData = weatherDeferred.await()
                    val newsData = newsDeferred.await()
                    val calendarData = calendarDeferred.await()

                    _statusMessage.value = if (isEs) "Llamando a Gemini AI..." else "Calling Gemini AI..."
                    val initialBriefing = geminiAgentManager.startSession(
                        apiKey = geminiKey,
                        weatherData = weatherData,
                        newsData = newsData,
                        calendarData = calendarData,
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

    private fun startNoSpeechTimeout() {
        noSpeechTimeoutJob?.cancel()
        noSpeechTimeoutJob = viewModelScope.launch {
            kotlinx.coroutines.delay(120_000) // 2 minutes (120,000 ms)
            if (_uiState.value != AlarmState.LISTENING) return@launch

            // Each nudge re-enters speak -> listen -> timeout, so without a cap the session
            // would hold the mic, audio focus and the screen on until the battery died.
            if (noSpeechNudges >= MAX_NO_SPEECH_NUDGES) {
                Log.d("AlarmViewModel", "No response after $noSpeechNudges nudges; ending session")
                forceClose()
                return@launch
            }
            noSpeechNudges++

            val noSpeechMessage = if (prefs.getLanguage() == "es") {
                "No he escuchado nada. ¿Sigues ahí?"
            } else {
                "I haven't heard anything. Are you still there?"
            }
            speakAgentResponse(noSpeechMessage)
        }
    }

    private fun cancelNoSpeechTimeout() {
        noSpeechTimeoutJob?.cancel()
        noSpeechTimeoutJob = null
    }

    private fun speakAgentResponse(text: String) {
        cancelNoSpeechTimeout()
        voiceManager.stopListening()
        
        _agentSpeech.value = text
        addChatMessage(MessageSender.AGENT, text)
        _uiState.value = AlarmState.SPEAKING
        _userSpeech.value = ""
        _micVolume.value = 0f
        
        if (wasCachedLocationUsed) {
            wasCachedLocationUsed = false
            viewModelScope.launch {
                try {
                    val location = locationProvider.getCurrentLocation()
                    if (location != null) {
                        prefs.saveLocation(location.first, location.second)
                    }
                } catch (e: Exception) {
                    Log.e("AlarmViewModel", "Error silently refreshing location: ${e.localizedMessage}")
                }
            }
        }
        
        voiceManager.speak(text) {
            // Callback when agent finishes speaking: add delay to let TTS fully release audio
            postSpeechJob?.cancel()
            postSpeechJob = viewModelScope.launch {
                // Do not stopSpeaking() here as it nullifies callback (which is already executed)
                kotlinx.coroutines.delay(600)
                // The user may have typed or tapped a quick reply during the delay, which moves
                // us to THINKING; reopening the mic here would corrupt that turn.
                if (_uiState.value == AlarmState.SPEAKING) {
                    startListeningForUser()
                }
            }
        }
    }

    private fun startListeningForUser() {
        voiceManager.stopSpeaking() // Ensure assistant is quiet when we start listening
        _uiState.value = AlarmState.LISTENING
        _micVolume.value = 0f
        startNoSpeechTimeout()
        
        voiceManager.startListening(
            onResult = { result: String ->
                cancelNoSpeechTimeout()
                consecutiveSttErrors = 0 // Reset on success
                noSpeechNudges = 0
                _userSpeech.value = result
                _micVolume.value = 0f
                processUserSpeech(result)
            },
            onError = { errorMsg: String ->
                Log.e("AlarmViewModel", "STT Error ($consecutiveSttErrors): $errorMsg")
                _micVolume.value = 0f
                consecutiveSttErrors++

                // No recognizer or no mic permission fails synchronously and will never recover,
                // so retrying just burns all five attempts behind a fake mic animation.
                val isUnrecoverable = errorMsg.contains("not available", ignoreCase = true) ||
                    errorMsg.contains("permission", ignoreCase = true)

                if (!isUnrecoverable && consecutiveSttErrors < MAX_STT_ERRORS && _uiState.value == AlarmState.LISTENING) {
                    // Retry with increasing delay to avoid spamming the recognizer
                    viewModelScope.launch {
                        val delay = 800L + (consecutiveSttErrors * 400L)
                        kotlinx.coroutines.delay(delay)
                        if (_uiState.value == AlarmState.LISTENING) {
                            startListeningForUser()
                        }
                    }
                } else {
                    // Stop pretending to listen. The UI kept animating the mic visualizer here
                    // while nothing was actually recording. ErrorLayout still offers text input.
                    Log.w("AlarmViewModel", "STT unusable after $consecutiveSttErrors attempts; surfacing error")
                    cancelNoSpeechTimeout()
                    voiceManager.stopListening()
                    val isEs = prefs.getLanguage() == "es"
                    _statusMessage.value = if (isEs) {
                        "No pude escucharte. Puedes escribir tu respuesta abajo."
                    } else {
                        "I couldn't hear you. You can type your reply below."
                    }
                    _uiState.value = AlarmState.ERROR
                }
            },
            onRmsChanged = { rmsdB: Float ->
                _micVolume.value = rmsdB
            }
        )
    }

    fun startListeningManual() {
        postSpeechJob?.cancel()
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        consecutiveSttErrors = 0 // Reset on manual retry
        _micVolume.value = 0f
        startListeningForUser()
    }

    fun processUserSpeech(text: String) {
        cancelNoSpeechTimeout()
        // The user answered, so the pending "reopen the mic" job must not fire mid-turn.
        postSpeechJob?.cancel()
        noSpeechNudges = 0
        voiceManager.stopListening()
        voiceManager.stopSpeaking()
        _micVolume.value = 0f

        addChatMessage(MessageSender.USER, text)

        // If the user says goodbye or close, finish the session
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

    /**
     * Returns the ViewModel to a clean RINGING state for a newly-delivered alarm.
     *
     * Needed because AlarmActivity is `singleInstance`: a second alarm reuses the existing
     * instance, which would otherwise still be showing the previous session's FINISHED or ERROR
     * screen while the new alarm rings.
     */
    fun resetForNewAlarm() {
        cancelNoSpeechTimeout()
        postSpeechJob?.cancel()
        sessionJob?.cancel()
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        geminiAgentManager.clearSession()
        consecutiveSttErrors = 0
        noSpeechNudges = 0
        wasCachedLocationUsed = false
        _chatMessages.value = emptyList()
        _userSpeech.value = ""
        _statusMessage.value = ""
        _micVolume.value = 0f
        _agentSpeech.value = "Tap 'Dismiss & Talk' to start your day."
        _uiState.value = AlarmState.RINGING
    }

    fun forceClose() {
        cancelNoSpeechTimeout()
        postSpeechJob?.cancel()
        sessionJob?.cancel()
        // Stop any current speaking or listening
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        voiceManager.endSession()
        _micVolume.value = 0f
        getApplication<Application>().stopService(Intent(getApplication(), AlarmService::class.java))
        _uiState.value = AlarmState.FINISHED
    }

    override fun onCleared() {
        super.onCleared()
        cancelNoSpeechTimeout()
        postSpeechJob?.cancel()
        sessionJob?.cancel()
        voiceManager.shutdown()
        geminiAgentManager.clearSession()
        _chatMessages.value = emptyList()
    }
}
