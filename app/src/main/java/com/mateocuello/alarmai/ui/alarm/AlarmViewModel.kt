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
    private val geminiAgentManager = GeminiAgentManager()
    private val voiceManager = VoiceManager(application)

    private var consecutiveSttErrors = 0

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

    fun dismissAndTalk() {
        viewModelScope.launch {
            // 1. Stop the alarm service ringtone
            getApplication<Application>().stopService(Intent(getApplication(), AlarmService::class.java))
            
            // 2. Fetch data & start AI agent
            _uiState.value = AlarmState.FETCHING_DATA
            _statusMessage.value = "Detecting location..."
            
            // Get location coordinates (either pre-fetched or fetch on demand)
            val (lat, lon) = prefetchedLocation
                ?: locationProvider.getCurrentLocation()?.also { prefs.saveLocation(it.first, it.second) }
                ?: prefs.getLocation()
            
            _statusMessage.value = "Fetching weather & news..."
            val weatherData = weatherRepository.getWeather(lat, lon)
            
            val newsKey = prefs.getNewsKey()
            val newsTopics = prefs.getNewsTopics()
            val newsData = newsRepository.getNews(newsKey, newsTopics)
            
            _statusMessage.value = "Reading today's schedule..."
            val calendarData = calendarRepository.getTodayEvents()
            
            _statusMessage.value = "Calling Gemini AI..."
            val geminiKey = prefs.getGeminiKey()
            val modelName = prefs.getGeminiModel()
            val initialBriefing = geminiAgentManager.startSession(
                apiKey = geminiKey,
                weatherData = weatherData,
                newsData = newsData,
                calendarData = calendarData,
                modelName = modelName
            )
            
            speakAgentResponse(initialBriefing)
        }
    }

    private fun speakAgentResponse(text: String) {
        _agentSpeech.value = text
        _uiState.value = AlarmState.SPEAKING
        _userSpeech.value = ""
        
        voiceManager.speak(text) {
            // Callback when agent finishes speaking: start listening automatically!
            startListeningForUser()
        }
    }

    private fun startListeningForUser() {
        _uiState.value = AlarmState.LISTENING
        voiceManager.startListening(
            onResult = { result ->
                consecutiveSttErrors = 0
                _userSpeech.value = result
                processUserSpeech(result)
            },
            onError = { error ->
                Log.e("AlarmViewModel", "STT Error: $error")
                consecutiveSttErrors++
                if (consecutiveSttErrors >= 3) {
                    _uiState.value = AlarmState.SPEAKING
                    speakAgentResponse("I couldn't catch that. Please type your message using the keyboard or tap the mic button to retry.")
                } else {
                    _statusMessage.value = "Did not catch that: $error"
                    _uiState.value = AlarmState.SPEAKING
                    // Wait 2 seconds and try listening again
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000)
                        startListeningForUser()
                    }
                }
            }
        )
    }

    fun startListeningManual() {
        consecutiveSttErrors = 0
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        startListeningForUser()
    }

    fun processUserSpeech(text: String) {
        voiceManager.stopListening()
        voiceManager.stopSpeaking()

        // If the user says goodbye or close, finish the session
        val goodbyeKeywords = listOf("goodbye", "exit", "stop", "close", "bye", "adios")
        if (goodbyeKeywords.any { text.contains(it, ignoreCase = true) }) {
            viewModelScope.launch {
                _agentSpeech.value = "Have a great day ahead! Goodbye."
                _uiState.value = AlarmState.SPEAKING
                voiceManager.speak("Have a great day ahead! Goodbye.") {
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
        // Stop any current speaking or listening
        voiceManager.stopListening()
        getApplication<Application>().stopService(Intent(getApplication(), AlarmService::class.java))
        _uiState.value = AlarmState.FINISHED
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.shutdown()
        geminiAgentManager.clearSession()
    }
}
