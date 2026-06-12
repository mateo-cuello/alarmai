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

enum class AlarmState {
    RINGING,
    FETCHING_DATA,
    SPEAKING,
    LISTENING,
    THINKING,
    ERROR,
    FINISHED
}

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val locationProvider = LocationProvider(application)
    private val weatherRepository = WeatherRepository()
    private val newsRepository = NewsRepository()
    private val calendarRepository = CalendarRepository(application)
    private val geminiAgentManager = GeminiAgentManager(prefs)
    private val voiceManager = VoiceManager(application)

    private var noSpeechTimeoutJob: kotlinx.coroutines.Job? = null

    private val _geminiModelName = MutableStateFlow(prefs.getGeminiModel())
    val geminiModelName: StateFlow<String> = _geminiModelName

    private val _uiState = MutableStateFlow(AlarmState.RINGING)
    val uiState: StateFlow<AlarmState> = _uiState

    private var prefetchedLocation: Pair<Double, Double>? = null

    init {
        prefetchLocation()
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

    private val _agentSpeech = MutableStateFlow("Tap 'Dismiss & Talk' to start your day.")
    val agentSpeech: StateFlow<String> = _agentSpeech

    private val _userSpeech = MutableStateFlow("")
    val userSpeech: StateFlow<String> = _userSpeech

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _micVolume = MutableStateFlow(0f)
    val micVolume: StateFlow<Float> = _micVolume

    fun dismissAndTalk() {
        viewModelScope.launch {
            // 1. Stop the alarm service ringtone
            getApplication<Application>().stopService(Intent(getApplication(), AlarmService::class.java))
            
            val geminiKey = prefs.getGeminiKey()
            val modelName = prefs.getGeminiModel()
            
            // Check for valid cached pre-fetch briefing (less than 5 minutes old = 300,000 ms)
            val (cachedBriefing, cachedPrompt, cachedTime) = prefs.getPrefetchedBriefing()
            val isCacheValid = cachedBriefing.isNotEmpty() && (System.currentTimeMillis() - cachedTime < 300_000)
            
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
                val (lat, lon) = prefetchedLocation
                    ?: locationProvider.getCurrentLocation()?.also { prefs.saveLocation(it.first, it.second) }
                    ?: prefs.getLocation()
                
                _statusMessage.value = if (isEs) "Obteniendo clima y noticias..." else "Fetching weather & news..."
                val weatherData = weatherRepository.getWeather(lat, lon)
                
                val newsKey = prefs.getNewsKey()
                val newsTopics = prefs.getNewsTopics()
                val newsData = newsRepository.getNews(newsKey, newsTopics)
                
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
        _uiState.value = AlarmState.SPEAKING
        _userSpeech.value = ""
        _micVolume.value = 0f
        
        voiceManager.speak(text) {
            // Callback when agent finishes speaking: start listening automatically!
            startListeningForUser()
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
                _userSpeech.value = result
                _micVolume.value = 0f
                processUserSpeech(result)
            },
            onError = { error ->
                Log.e("AlarmViewModel", "STT Error: $error")
                _micVolume.value = 0f
                // Continuous listening: silently restart listening after a short delay
                viewModelScope.launch {
                    kotlinx.coroutines.delay(500)
                    if (_uiState.value == AlarmState.LISTENING) {
                        startListeningForUser()
                    }
                }
            },
            onRmsChanged = { rmsdB ->
                _micVolume.value = rmsdB
            }
        )
    }

    fun startListeningManual() {
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        _micVolume.value = 0f
        startListeningForUser()
    }

    fun processUserSpeech(text: String) {
        cancelNoSpeechTimeout()
        voiceManager.stopListening()
        voiceManager.stopSpeaking()
        _micVolume.value = 0f

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
                _uiState.value = AlarmState.SPEAKING
                voiceManager.speak(goodbyeMsg) {
                    _uiState.value = AlarmState.FINISHED
                }
            }
            return
        }

        _uiState.value = AlarmState.THINKING
        viewModelScope.launch {
            val response = geminiAgentManager.sendMessage(text)
            speakAgentResponse(response)
        }
    }

    fun forceClose() {
        cancelNoSpeechTimeout()
        // Stop any current speaking or listening
        voiceManager.stopListening()
        _micVolume.value = 0f
        getApplication<Application>().stopService(Intent(getApplication(), AlarmService::class.java))
        _uiState.value = AlarmState.FINISHED
    }

    override fun onCleared() {
        super.onCleared()
        cancelNoSpeechTimeout()
        voiceManager.shutdown()
        geminiAgentManager.clearSession()
    }
}
