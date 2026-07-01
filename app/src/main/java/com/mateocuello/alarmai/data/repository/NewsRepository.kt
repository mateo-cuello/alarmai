package com.mateocuello.alarmai.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsRepository {

    /**
     * Provides a prompt instruction for Gemini to fetch news using its internal Google Search tool.
     * No API keys or HTTP clients required anymore.
     */
    suspend fun getNews(topics: String, language: String = "en"): String = withContext(Dispatchers.IO) {
        if (language == "es") {
            "Por favor, busca las últimas noticias sobre estos temas usando la herramienta de Google Search: $topics. Resume los titulares más importantes."
        } else {
            "Please search for the latest news headlines about these topics using the Google Search tool: $topics. Summarize the top headlines."
        }
    }

    /**
     * Provides on-demand prompt instructions for searching specific queries.
     */
    suspend fun searchNewsByQuery(query: String, language: String = "en"): String = withContext(Dispatchers.IO) {
        if (language == "es") {
            "Por favor usa tu herramienta de búsqueda de Google (googleSearch) para encontrar noticias sobre: $query"
        } else {
            "Please use your Google Search tool (googleSearch) to find news about: $query"
        }
    }
}
