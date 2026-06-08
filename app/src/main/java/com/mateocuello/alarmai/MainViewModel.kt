package com.mateocuello.alarmai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mateocuello.alarmai.data.local.PreferencesManager
import com.mateocuello.alarmai.data.model.Alarm
import com.mateocuello.alarmai.receiver.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    private val scheduler = AlarmScheduler(application)

    private val _alarm = MutableStateFlow(prefs.getAlarm())
    val alarm: StateFlow<Alarm> = _alarm

    private val _geminiKey = MutableStateFlow(prefs.getGeminiKey())
    val geminiKey: StateFlow<String> = _geminiKey

    private val _newsKey = MutableStateFlow(prefs.getNewsKey())
    val newsKey: StateFlow<String> = _newsKey

    private val _newsTopics = MutableStateFlow(prefs.getNewsTopics())
    val newsTopics: StateFlow<String> = _newsTopics

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

    fun saveNewsKey(key: String) {
        prefs.saveNewsKey(key)
        _newsKey.value = key
    }

    fun saveNewsTopics(topics: String) {
        prefs.saveNewsTopics(topics)
        _newsTopics.value = topics
    }
}
