package com.mateocuello.alarmai.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mateocuello.alarmai.data.local.PreferencesManager
import com.mateocuello.alarmai.data.repository.CalendarRepository
import com.mateocuello.alarmai.data.repository.GeminiAgentManager
import com.mateocuello.alarmai.data.repository.LocationProvider
import com.mateocuello.alarmai.data.repository.NewsRepository
import com.mateocuello.alarmai.data.repository.WeatherRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("PrefetchWorker", "Executing background pre-fetch via WorkManager...")
        return try {
            executePrefetch(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e("PrefetchWorker", "Error pre-fetching data: ${e.localizedMessage}")
            Result.failure()
        }
    }

    private suspend fun executePrefetch(context: Context) {
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

        // 6. Build initial prompt and call Gemini
        val geminiAgentManager = GeminiAgentManager(context, prefs)
        val geminiKey = prefs.getGeminiKey()
        val modelName = prefs.getGeminiModel()

        Log.d("PrefetchWorker", "Calling Gemini for pre-generated briefing")
        val briefing = geminiAgentManager.startSession(
            apiKey = geminiKey,
            weatherData = weatherData,
            newsData = newsData,
            calendarData = calendarData,
            modelName = modelName
        )

        // Save to Preferences cache
        val timestamp = System.currentTimeMillis()
        
        val initialPrompt = if (language == "es") {
            """
                Comienza el resumen matutino. Aquí están los datos del día:
                - Clima: $weatherData
                - Noticias: $newsData
                - Eventos de Calendario: $calendarData
                
                Por favor, saluda al usuario cálidamente, menciona la hora (o deséale un buen día), resume estos datos de manera muy atractiva y concisa, y pregúntale cómo le gustaría empezar el día.
            """.trimIndent()
        } else {
            """
                Start the morning briefing. Here is the daily data:
                - Weather: $weatherData
                - News: $newsData
                - Calendar Events: $calendarData
                
                Please greet the user warmly, state the time (or wish them a good morning), summarize this data in a highly engaging, concise way, and ask how they'd like to start their day.
            """.trimIndent()
        }

        prefs.savePrefetchedBriefing(briefing, initialPrompt, timestamp)
        Log.d("PrefetchWorker", "Successfully saved prefetched briefing. Timestamp: $timestamp")
    }
}
