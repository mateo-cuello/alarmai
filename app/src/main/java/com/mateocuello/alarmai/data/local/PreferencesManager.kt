package com.mateocuello.alarmai.data.local

import android.content.Context
import android.content.SharedPreferences
import com.mateocuello.alarmai.BuildConfig
import com.mateocuello.alarmai.data.model.Alarm

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = run {
        val storageContext = if (!context.isDeviceProtectedStorage) {
            context.createDeviceProtectedStorageContext() ?: context
        } else {
            context
        }
        storageContext.getSharedPreferences("AlarmAIPrefs", Context.MODE_PRIVATE)
    }

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
        private const val KEY_ALARM_VOLUME = "alarm_volume"
        private const val KEY_ALARM_RINGTONE_URI = "alarm_ringtone_uri"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_PREFETCHED_BRIEFING = "prefetched_briefing"
        private const val KEY_PREFETCHED_PROMPT = "prefetched_prompt"
        private const val KEY_PREFETCHED_TIMESTAMP = "prefetched_timestamp"
        private const val KEY_LANGUAGE = "assistant_language"
        private const val KEY_VOICE_NAME = "assistant_voice_name"
        private const val KEY_TONE_PREFERENCE = "assistant_tone_preference"
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
        val saved = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
        if (saved.isNotEmpty()) return saved
        return BuildConfig.GEMINI_API_KEY
    }

    fun saveGeminiModel(model: String) {
        prefs.edit().putString(KEY_GEMINI_MODEL, model).apply()
    }

    fun getGeminiModel(): String {
        return prefs.getString(KEY_GEMINI_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash"
    }

    fun saveNewsKey(key: String) {
        prefs.edit().putString(KEY_NEWS_KEY, key).apply()
    }

    fun getNewsKey(): String {
        val saved = prefs.getString(KEY_NEWS_KEY, "") ?: ""
        if (saved.isNotEmpty()) return saved
        return BuildConfig.NEWS_API_KEY
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

    fun hasCachedLocation(): Boolean {
        return prefs.contains(KEY_LAT) && prefs.contains(KEY_LON)
    }


    fun getLocation(): Pair<Double, Double> {
        val lat = prefs.getFloat(KEY_LAT, -34.6037f).toDouble()
        val lon = prefs.getFloat(KEY_LON, -58.3816f).toDouble()
        if (lat == 0.0 && lon == 0.0) {
            return Pair(-34.6037, -58.3816)
        }
        return Pair(lat, lon)
    }

    fun saveAlarmVolume(volume: Int) {
        prefs.edit().putInt(KEY_ALARM_VOLUME, volume).apply()
    }

    fun getAlarmVolume(): Int {
        return prefs.getInt(KEY_ALARM_VOLUME, 80)
    }

    fun saveAlarmRingtoneUri(uri: String) {
        prefs.edit().putString(KEY_ALARM_RINGTONE_URI, uri).apply()
    }

    fun getAlarmRingtoneUri(): String {
        return prefs.getString(KEY_ALARM_RINGTONE_URI, "") ?: ""
    }

    fun savePrefetchedBriefing(briefing: String, prompt: String, timestamp: Long) {
        prefs.edit()
            .putString(KEY_PREFETCHED_BRIEFING, briefing)
            .putString(KEY_PREFETCHED_PROMPT, prompt)
            .putLong(KEY_PREFETCHED_TIMESTAMP, timestamp)
            .apply()
    }

    fun getPrefetchedBriefing(): Triple<String, String, Long> {
        val briefing = prefs.getString(KEY_PREFETCHED_BRIEFING, "") ?: ""
        val prompt = prefs.getString(KEY_PREFETCHED_PROMPT, "") ?: ""
        val timestamp = prefs.getLong(KEY_PREFETCHED_TIMESTAMP, 0L)
        return Triple(briefing, prompt, timestamp)
    }

    fun clearPrefetchedBriefing() {
        prefs.edit()
            .remove(KEY_PREFETCHED_BRIEFING)
            .remove(KEY_PREFETCHED_PROMPT)
            .remove(KEY_PREFETCHED_TIMESTAMP)
            .apply()
    }

    fun saveLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun getLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, "es") ?: "es"
    }

    fun saveVoiceName(voiceName: String) {
        prefs.edit().putString(KEY_VOICE_NAME, voiceName).apply()
    }

    fun getVoiceName(): String {
        return prefs.getString(KEY_VOICE_NAME, "") ?: ""
    }

    fun saveTonePreference(tone: String) {
        prefs.edit().putString(KEY_TONE_PREFERENCE, tone).apply()
    }

    fun getTonePreference(): String {
        return prefs.getString(KEY_TONE_PREFERENCE, "warm, helpful, and energetic") ?: "warm, helpful, and energetic"
    }
}
