package com.alarmai.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alarmai.app.data.local.PreferencesManager
import com.alarmai.app.data.model.Alarm
import com.alarmai.app.data.repository.LocationProvider
import com.alarmai.app.receiver.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val locationProvider = LocationProvider(application)
    private val prefs = PreferencesManager(application)
    private val scheduler = AlarmScheduler(application)

    private val _alarm = MutableStateFlow(prefs.getAlarm())
    val alarm: StateFlow<Alarm> = _alarm

    private val _geminiKey = MutableStateFlow(prefs.getGeminiKey())
    val geminiKey: StateFlow<String> = _geminiKey

    private val _geminiModel = MutableStateFlow(prefs.getGeminiModel())
    val geminiModel: StateFlow<String> = _geminiModel

    private val _language = MutableStateFlow(prefs.getLanguage())
    val language: StateFlow<String> = _language

    private val _voiceName = MutableStateFlow(prefs.getVoiceName())
    val voiceName: StateFlow<String> = _voiceName

    private val _tonePreference = MutableStateFlow(prefs.getTonePreference())
    val tonePreference: StateFlow<String> = _tonePreference

    private var tempTts: android.speech.tts.TextToSpeech? = null
    private val _availableVoices = MutableStateFlow<List<String>>(emptyList())
    val availableVoices: StateFlow<List<String>> = _availableVoices

    init {
        loadVoicesForLanguage(prefs.getLanguage())
    }

    fun loadVoicesForLanguage(lang: String) {
        if (tempTts == null) {
            tempTts = android.speech.tts.TextToSpeech(getApplication()) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    updateVoicesList(lang)
                }
            }
        } else {
            updateVoicesList(lang)
        }
    }

    private fun updateVoicesList(lang: String) {
        val targetLocale = if (lang == "es") java.util.Locale("es") else java.util.Locale.US
        val voices = tempTts?.voices?.filter {
            it.locale.language.equals(targetLocale.language, ignoreCase = true)
        }?.map { it.name }?.sorted() ?: emptyList()
        _availableVoices.value = voices
    }

    private val _newsTopics = MutableStateFlow(prefs.getNewsTopics())
    val newsTopics: StateFlow<String> = _newsTopics

    private val _alarmVolume = MutableStateFlow(prefs.getAlarmVolume())
    val alarmVolume: StateFlow<Int> = _alarmVolume

    private val _alarmRingtoneUri = MutableStateFlow(prefs.getAlarmRingtoneUri())
    val alarmRingtoneUri: StateFlow<String> = _alarmRingtoneUri

    fun updateAlarmTime(hour: Int, minute: Int) {
        val current = _alarm.value
        val updated = current.copy(hour = hour, minute = minute)
        saveAndReschedule(updated)
    }

    fun toggleAlarmActive(isActive: Boolean) {
        val current = _alarm.value
        val updated = current.copy(isActive = isActive)
        saveAndReschedule(updated)
    }

    fun toggleAlarmDay(day: Int) {
        val current = _alarm.value
        val newDays = if (current.daysOfWeek.contains(day)) {
            current.daysOfWeek - day
        } else {
            current.daysOfWeek + day
        }
        val updated = current.copy(daysOfWeek = newDays)
        saveAndReschedule(updated)
    }

    fun reloadAlarm() {
        _alarm.value = prefs.getAlarm()
    }

    private fun saveAndReschedule(updatedAlarm: Alarm) {
        prefs.saveAlarm(updatedAlarm)
        _alarm.value = updatedAlarm
        scheduler.schedule(updatedAlarm)
    }

    fun saveGeminiKey(key: String) {
        prefs.saveGeminiKey(key)
        _geminiKey.value = key
    }

    fun saveGeminiModel(model: String) {
        prefs.saveGeminiModel(model)
        _geminiModel.value = model
    }

    fun saveNewsTopics(topics: String) {
        prefs.saveNewsTopics(topics)
        _newsTopics.value = topics
    }

    fun saveAlarmVolume(volume: Int) {
        prefs.saveAlarmVolume(volume)
        _alarmVolume.value = volume
    }

    fun saveAlarmRingtoneUri(uri: String) {
        prefs.saveAlarmRingtoneUri(uri)
        _alarmRingtoneUri.value = uri
    }

    fun saveLanguage(language: String) {
        prefs.saveLanguage(language)
        _language.value = language
        loadVoicesForLanguage(language)
    }

    fun saveVoiceName(voiceName: String) {
        prefs.saveVoiceName(voiceName)
        _voiceName.value = voiceName
    }

    fun saveTonePreference(tone: String) {
        prefs.saveTonePreference(tone)
        _tonePreference.value = tone
    }

    fun fetchLocation() {
        viewModelScope.launch {
            val location = locationProvider.getCurrentLocation()
            if (location != null) {
                prefs.saveLocation(location.first, location.second)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tempTts?.shutdown()
    }
}
