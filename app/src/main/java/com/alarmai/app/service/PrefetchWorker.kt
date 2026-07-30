package com.alarmai.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.alarmai.app.data.local.PreferencesManager
import com.alarmai.app.data.repository.CalendarRepository
import com.alarmai.app.data.repository.GeminiAgentManager
import com.alarmai.app.data.repository.LocationProvider
import com.alarmai.app.data.repository.NewsRepository
import com.alarmai.app.data.repository.WeatherRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

class PrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "alarm_ai_prefetch"
        private const val NOTIFICATION_ID = 9998
        private const val MAX_ATTEMPTS = 3

        /** Reuse a cached briefing rather than paying for another one. Matches AlarmViewModel. */
        private const val CACHE_VALID_MS = 1800_000L
    }

    /**
     * Required for [androidx.work.OutOfQuotaPolicy] expedited work below API 31, where the system
     * runs it as a short-lived foreground service. Without this override `setExpedited` throws
     * IllegalStateException, and minSdk here is 26.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        createChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Preparing your morning briefing")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Briefing preparation",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    override suspend fun doWork(): Result {
        Log.d("PrefetchWorker", "Executing background pre-fetch via WorkManager...")
        return try {
            executePrefetch(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e("PrefetchWorker", "Error pre-fetching data: ${e.localizedMessage}")
            // A transient network blip used to kill the whole morning's prefetch permanently.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private suspend fun executePrefetch(context: Context) {
        val prefs = PreferencesManager(context)

        // Alarm edits reschedule on every toggle, and a <2min alarm prefetches immediately, so
        // this can be entered repeatedly. Each run is a billed Gemini call; skip if one is fresh.
        val (cachedBriefing, _, cachedTime) = prefs.getPrefetchedBriefing()
        if (cachedBriefing.isNotEmpty() && System.currentTimeMillis() - cachedTime < CACHE_VALID_MS) {
            Log.d("PrefetchWorker", "A recent briefing is already cached; skipping")
            return
        }

        // 1. Resolve location
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

        // 3. Call Gemini for the briefing
        val geminiAgentManager = GeminiAgentManager(prefs)
        val geminiKey = prefs.getGeminiKey()
        val modelName = prefs.getGeminiModel()

        Log.d("PrefetchWorker", "Calling Gemini for pre-generated briefing")
        // startSessionDetailed returns the prompt that was actually sent. Rebuilding our own copy
        // here (as this used to) meant reconstructSession replayed a user turn that never happened.
        val result = geminiAgentManager.startSessionDetailed(
            apiKey = geminiKey,
            weatherData = weatherData,
            newsData = newsData,
            calendarData = calendarData,
            modelName = modelName
        )

        val timestamp = System.currentTimeMillis()
        prefs.savePrefetchedBriefing(result.text, result.prompt, timestamp)
        Log.d("PrefetchWorker", "Successfully saved prefetched briefing. Timestamp: $timestamp")
    }
}
