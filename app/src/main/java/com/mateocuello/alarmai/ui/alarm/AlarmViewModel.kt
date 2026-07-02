package com.mateocuello.alarmai.ui.alarm

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateocuello.alarmai.data.local.PreferencesManager
import com.mateocuello.alarmai.data.repository.CalendarRepository
import com.mateocuello.alarmai.data.repository.GeminiAgentManager
import com.mateocuello.alarmai.data.repository.LocationProvider
import com.mateocuello.alarmai.data.repository.NewsRepository
import com.mateocuello.alarmai.data.repository.VoiceManager
import com.mateocuello.alarmai.data.repository.WeatherRepository
import com.mateocuello.alarmai.service.AlarmService
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
    private val geminiAgentManager: GeminiAgentManager = GeminiAgentManager(application, prefs),
    private val voiceManager: VoiceManager = VoiceManager(application, prefs)
) : AndroidViewModel(application) {

    private var noSpeechTimeoutJob: kotlinx.coroutines.Job? = null
    private var consecutiveSttErrors = 0
    private var wasCachedLocationUsed: Boolean = false

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
        wasCachedLocationUsed = false
        viewModelScope.launch {
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
            if (_uiState.value == AlarmState.LISTENING) {
                val noSpeechMessage = if (prefs.getLanguage() == "es") {
                    "No he escuchado nada. ¿Sigues ahí?"
                } else {
                    "I haven't heard anything. Are you still there?"
                }
                speakAgentResponse(noSpeechMessage)
            }
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
            onResult = { result: String ->
                cancelNoSpeechTimeout()
                consecutiveSttErrors = 0 // Reset on success
                _userSpeech.value = result
                _micVolume.value = 0f
                processUserSpeech(result)
            },
            onError = { errorMsg: String ->
                Log.e("AlarmViewModel", "STT Error ($consecutiveSttErrors): $errorMsg")
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
                    Log.w("AlarmViewModel", "STT max retries reached, waiting for manual text input.")
                    // Fallback to manual text input without showing error screen
                }
            },
            onRmsChanged = { rmsdB: Float ->
                _micVolume.value = rmsdB
            }
        )
    }

    fun startListeningManual() {
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        consecutiveSttErrors = 0 // Reset on manual retry
        _micVolume.value = 0f
        startListeningForUser()
    }

    fun processUserSpeech(text: String) {
        cancelNoSpeechTimeout()
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

    fun forceClose() {
        cancelNoSpeechTimeout()
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
        voiceManager.shutdown()
        geminiAgentManager.clearSession()
        _chatMessages.value = emptyList()
    }
}
