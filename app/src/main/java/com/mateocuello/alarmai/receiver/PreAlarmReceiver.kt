package com.mateocuello.alarmai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mateocuello.alarmai.data.local.PreferencesManager
import com.mateocuello.alarmai.data.repository.CalendarRepository
import com.mateocuello.alarmai.data.repository.GeminiAgentManager
import com.mateocuello.alarmai.data.repository.LocationProvider
import com.mateocuello.alarmai.data.repository.NewsRepository
import com.mateocuello.alarmai.data.repository.WeatherRepository
import com.mateocuello.alarmai.data.repository.WorldCupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("PreAlarmReceiver", "Pre-alarm triggered for background pre-fetching!")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                executePrefetch(context)
            } catch (e: Exception) {
                Log.e("PreAlarmReceiver", "Error in onReceive: ${e.localizedMessage}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun startPrefetch(context: Context) {
            Log.d("PreAlarmReceiver", "Starting immediate background pre-fetch")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    executePrefetch(context)
                } catch (e: Exception) {
                    Log.e("PreAlarmReceiver", "Error in startPrefetch: ${e.localizedMessage}")
                }
            }
        }

        private suspend fun executePrefetch(context: Context) {
            Log.d("PreAlarmReceiver", "Executing pre-fetch sequence...")
            val prefs = PreferencesManager(context)
            
            // 1. Fetch Location
            val locationProvider = LocationProvider(context)
            val location = locationProvider.getCurrentLocation()
            val (lat, lon) = if (location != null) {
                prefs.saveLocation(location.first, location.second)
                location
            } else {
                prefs.getLocation()
            }

            // 2. Fetch Weather
            val weatherRepository = WeatherRepository()
            val weatherData = weatherRepository.getWeather(lat, lon)

            // 3. Fetch News (Google News RSS - no API key needed)
            val newsRepository = NewsRepository()
            val newsTopics = prefs.getNewsTopics()
            val language = prefs.getLanguage()
            val newsData = newsRepository.getNews(newsTopics, language)

            // 4. Fetch Calendar
            val calendarRepository = CalendarRepository(context)
            val calendarData = calendarRepository.getTodayEvents()

            // 5. Fetch World Cup matches
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayDateString = sdf.format(Date())
            val worldCupRepository = WorldCupRepository()
            val worldCupData = worldCupRepository.getTodayMatchesSummary(context, todayDateString)

            // 6. Build initial prompt and call Gemini
            val geminiAgentManager = GeminiAgentManager(context, prefs)
            val geminiKey = prefs.getGeminiKey()
            val modelName = prefs.getGeminiModel()

            Log.d("PreAlarmReceiver", "Calling Gemini for pre-generated briefing")
            val briefing = geminiAgentManager.startSession(
                apiKey = geminiKey,
                weatherData = weatherData,
                newsData = newsData,
                calendarData = calendarData,
                worldCupData = worldCupData,
                modelName = modelName
            )

            // Save to Preferences cache
            val timestamp = System.currentTimeMillis()
            
            val initialPrompt = """
                Start the morning briefing. Here is the daily data:
                - Weather: $weatherData
                - News: $newsData
                - Calendar Events: $calendarData
                - Today's FIFA World Cup 2026 Matches: $worldCupData
                
                Please greet the user warmly, state the time (or wish them a good morning), summarize this data in a highly engaging, concise way, and ask how they'd like to start their day.
            """.trimIndent()

            prefs.savePrefetchedBriefing(briefing, initialPrompt, timestamp)
            Log.d("PreAlarmReceiver", "Successfully saved prefetched briefing. Timestamp: $timestamp")
        }
    }
}
