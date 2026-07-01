package com.mateocuello.alarmai.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsRepository {

    /**
     * Provides a prompt instruction for Gemini to fetch news using its internal Google Search tool.
     * No API keys or HTTP clients required anymore.
     */
    suspend fun getNews(topics: String, language: String = "en"): String = withContext(Dispatchers.IO) {
        topics
    }

    /**
     * Provides on-demand prompt instructions for searching specific queries.
     */
    suspend fun searchNewsByQuery(query: String, language: String = "en"): String = withContext(Dispatchers.IO) {
        query
    }
}
