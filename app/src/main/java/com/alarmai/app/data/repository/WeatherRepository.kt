package com.alarmai.app.data.repository

import android.util.Log
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current_weather") currentWeather: Boolean = true
    ): WeatherResponse
}

data class WeatherResponse(
    @SerializedName("current_weather") val currentWeather: CurrentWeather?
)

data class CurrentWeather(
    val temperature: Double,
    val windspeed: Double,
    val weathercode: Int
)

class WeatherRepository {
    private val api: OpenMeteoApi = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenMeteoApi::class.java)

    /**
     * Returns a spoken-ready summary, or an empty string when unavailable. Blank means "skip the
     * weather section"; returning the error text made the briefing say things like
     * "Today's weather: Failed to retrieve weather, SocketTimeoutException" out loud.
     */
    suspend fun getWeather(lat: Double, lon: Double): String {
        return try {
            val response = api.getForecast(lat, lon)
            val current = response.currentWeather
            if (current != null) {
                val description = getWeatherDescription(current.weathercode)
                "Temperature: ${current.temperature}°C, Condition: $description, Wind speed: ${current.windspeed} km/h"
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e("WeatherRepository", "Failed to retrieve weather: ${e.localizedMessage}")
            ""
        }
    }

    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Mainly clear, partly cloudy, or overcast"
            45, 48 -> "Fog and depositing rime fog"
            51, 53, 55 -> "Drizzle: Light, moderate, or dense intensity"
            61, 63, 65 -> "Rain: Slight, moderate, or heavy intensity"
            71, 73, 75 -> "Snow fall: Slight, moderate, or heavy intensity"
            77 -> "Snow grains"
            80, 81, 82 -> "Rain showers: Slight, moderate, or violent"
            85, 86 -> "Snow showers: Slight or heavy"
            95 -> "Thunderstorm: Slight or moderate"
            96, 99 -> "Thunderstorm with slight or heavy hail"
            else -> "Unknown condition (Code $code)"
        }
    }
}
