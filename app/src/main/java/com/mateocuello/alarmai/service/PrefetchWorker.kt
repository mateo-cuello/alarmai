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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

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
        val (lat, lon) = if (prefs.hasCachedLocation()) {
            prefs.getLocation()
        } else {
            val location = withTimeoutOrNull(3000) { locationProvider.getCurrentLocation() }
            location?.also {
                prefs.saveLocation(it.first, it.second)
            } ?: prefs.getLocation()
        }

        val weatherRepository = WeatherRepository()
        val newsRepository = NewsRepository()
        val calendarRepository = CalendarRepository(context)
        val newsTopics = prefs.getNewsTopics()
        val language = prefs.getLanguage()

        val (weatherData, newsData, calendarData) = coroutineScope {
            val weatherDeferred = async { weatherRepository.getWeather(lat, lon) }
            val newsDeferred = async { newsRepository.getNews(newsTopics, language) }
            val calendarDeferred = async { calendarRepository.getTodayEvents() }
            
            Triple(
                weatherDeferred.await(),
                newsDeferred.await(),
                calendarDeferred.await()
            )
        }

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
                Comienza el resumen matutino.
                - Clima: $weatherData
                - Eventos de Calendario: $calendarData
                
                Usa tu herramienta de búsqueda de Google (googleSearch) para buscar las noticias más recientes sobre estos temas: $newsData.
                
                Por favor, saluda al usuario cálidamente, menciona la hora (o deséale un buen día), resume el clima, calendario, y los titulares más importantes de las noticias de manera muy atractiva y concisa, y pregúntale cómo le gustaría empezar el día. IMPORTANTE: No des un mensaje genérico de introducción antes de las noticias ni inventes datos. Da las noticias reales obtenidas de la búsqueda directamente.
            """.trimIndent()
        } else {
            """
                Start the morning briefing.
                - Weather: $weatherData
                - Calendar Events: $calendarData
                
                Use your Google Search tool (googleSearch) to find the latest news headlines about these topics: $newsData.
                
                Please greet the user warmly, state the time (or wish them a good morning), summarize the weather, calendar, and the top news headlines in a highly engaging, concise way, and ask how they'd like to start their day. IMPORTANT: Do not give a generic introductory message before the news or invent facts. Deliver the real news obtained from the search directly.
            """.trimIndent()
        }

        prefs.savePrefetchedBriefing(briefing, initialPrompt, timestamp)
        Log.d("PrefetchWorker", "Successfully saved prefetched briefing. Timestamp: $timestamp")
    }
}
