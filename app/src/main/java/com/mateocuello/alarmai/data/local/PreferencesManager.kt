package com.mateocuello.alarmai.data.local

import android.content.Context
import android.content.SharedPreferences
import com.mateocuello.alarmai.data.model.Alarm

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("AlarmAIPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALARM_HOUR = "alarm_hour"
        private const val KEY_ALARM_MINUTE = "alarm_minute"
        private const val KEY_ALARM_ACTIVE = "alarm_active"
        private const val KEY_ALARM_DAYS_OF_WEEK = "alarm_days_of_week"
        private const val KEY_GEMINI_KEY = "gemini_key"
        private const val KEY_NEWS_KEY = "news_key"
        private const val KEY_NEWS_TOPICS = "news_topics"
        private const val KEY_LAT = "latitude"
        private const val KEY_LON = "longitude"
    }

    fun saveAlarm(alarm: Alarm) {
        val daysString = alarm.daysOfWeek.joinToString(",")
        prefs.edit()
            .putInt(KEY_ALARM_HOUR, alarm.hour)
            .putInt(KEY_ALARM_MINUTE, alarm.minute)
            .putBoolean(KEY_ALARM_ACTIVE, alarm.isActive)
            .putString(KEY_ALARM_DAYS_OF_WEEK, daysString)
            .apply()
    }

    fun getAlarm(): Alarm {
        val hour = prefs.getInt(KEY_ALARM_HOUR, 7)
        val minute = prefs.getInt(KEY_ALARM_MINUTE, 0)
        val active = prefs.getBoolean(KEY_ALARM_ACTIVE, false)
        val daysString = prefs.getString(KEY_ALARM_DAYS_OF_WEEK, "") ?: ""
        val daysOfWeek = if (daysString.isEmpty()) {
            emptySet()
        } else {
            daysString.split(",")
                .mapNotNull { it.toIntOrNull() }
                .toSet()
        }
        return Alarm(hour, minute, active, daysOfWeek = daysOfWeek)
    }

    fun saveGeminiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_KEY, key).apply()
    }

    fun getGeminiKey(): String {
        return prefs.getString(KEY_GEMINI_KEY, "") ?: ""
    }

    fun saveNewsKey(key: String) {
        prefs.edit().putString(KEY_NEWS_KEY, key).apply()
    }

    fun getNewsKey(): String {
        return prefs.getString(KEY_NEWS_KEY, "") ?: ""
    }

    fun saveNewsTopics(topics: String) {
        prefs.edit().putString(KEY_NEWS_TOPICS, topics).apply()
    }

    fun getNewsTopics(): String {
        return prefs.getString(KEY_NEWS_TOPICS, "technology,science,world") ?: "technology,science,world"
    }

    fun saveLocation(lat: Double, lon: Double) {
        prefs.edit()
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LON, lon.toFloat())
            .apply()
    }

    fun getLocation(): Pair<Double, Double> {
        val lat = prefs.getFloat(KEY_LAT, 0.0f).toDouble()
        val lon = prefs.getFloat(KEY_LON, 0.0f).toDouble()
        return Pair(lat, lon)
    }
}
