package com.mateocuello.alarmai.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class NewsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches news headlines for the configured topics using Google News RSS.
     * No API key required. Supports multiple comma-separated topics.
     *
     * @param topics Comma-separated topic keywords (e.g. "technology,science,world")
     * @param language "es" for Spanish or "en" for English
     */
    suspend fun getNews(topics: String, language: String = "en"): String = withContext(Dispatchers.IO) {
        try {
            val topicList = topics.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (topicList.isEmpty()) {
                return@withContext fetchRssHeadlines("news", language, 5)
            }

            val allHeadlines = mutableListOf<String>()
            val headlinesPerTopic = maxOf(2, 6 / topicList.size)

            for (topic in topicList) {
                try {
                    val headlines = fetchRssHeadlines(topic, language, headlinesPerTopic)
                    if (headlines.isNotEmpty()) {
                        allHeadlines.add(headlines)
                    }
                } catch (e: Exception) {
                    Log.w("NewsRepository", "Failed to fetch news for topic '$topic': ${e.localizedMessage}")
                }
            }

            if (allHeadlines.isEmpty()) {
                "No live news available. " + getMockNews(topics)
            } else {
                allHeadlines.joinToString("\n")
            }
        } catch (e: Exception) {
            Log.e("NewsRepository", "Failed to fetch news: ${e.localizedMessage}")
            "Failed to fetch live news. " + getMockNews(topics)
        }
    }

    /**
     * On-demand news search by query. Used by the Gemini searchNews function tool
     * so the AI can look up news about any topic during conversation.
     *
     * @param query The search query (e.g. "Argentina economy", "climate change")
     * @param language "es" or "en"
     */
    suspend fun searchNewsByQuery(query: String, language: String = "en"): String = withContext(Dispatchers.IO) {
        try {
            val headlines = fetchRssHeadlines(query, language, 5)
            if (headlines.isNotEmpty()) {
                "Latest news for \"$query\":\n$headlines"
            } else {
                "No news articles found for \"$query\"."
            }
        } catch (e: Exception) {
            Log.e("NewsRepository", "News search failed for '$query': ${e.localizedMessage}")
            "Failed to search news for \"$query\": ${e.localizedMessage}"
        }
    }

    /**
     * Fetches headlines from Google News RSS feed.
     */
    internal fun fetchRssHeadlines(query: String, language: String, maxResults: Int): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        val (hl, gl, ceid) = when (language) {
            "es" -> Triple("es", "AR", "AR:es")
            else -> Triple("en", "US", "US:en")
        }

        val url = "https://news.google.com/rss/search?q=$encodedQuery&hl=$hl&gl=$gl&ceid=$ceid"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AlarmAI/1.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w("NewsRepository", "Google News RSS returned HTTP ${response.code} for query '$query'")
            return ""
        }

        val xmlString = response.body?.string() ?: return ""
        return parseRssXml(xmlString, maxResults)
    }

    /**
     * Parses RSS XML and extracts article titles and sources.
     */
    private fun parseRssXml(xml: String, maxResults: Int): String {
        val headlines = mutableListOf<String>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var insideItem = false
            var currentTitle: String? = null
            var currentSource: String? = null
            var currentTag = ""

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name ?: ""
                        if (currentTag == "item") {
                            insideItem = true
                            currentTitle = null
                            currentSource = null
                        }
                        if (insideItem && currentTag == "source") {
                            // The source text is the content of the <source> tag
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideItem) {
                            val text = parser.text?.trim() ?: ""
                            if (text.isNotEmpty()) {
                                when (currentTag) {
                                    "title" -> currentTitle = text
                                    "source" -> currentSource = text
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val endTag = parser.name ?: ""
                        if (endTag == "item" && insideItem) {
                            insideItem = false
                            if (currentTitle != null && currentTitle.isNotBlank()) {
                                // Clean up title: Google News often appends " - Source" to the title
                                val cleanTitle = cleanGoogleNewsTitle(currentTitle, currentSource)
                                val sourceName = currentSource ?: "Unknown"
                                headlines.add("- $cleanTitle ($sourceName)")
                            }
                            if (headlines.size >= maxResults) break
                        }
                        if (endTag == currentTag) {
                            currentTag = ""
                        }
                    }
                }
                parser.next()
            }
        } catch (e: Exception) {
            Log.w("NewsRepository", "RSS XML parsing error: ${e.localizedMessage}")
        }

        return headlines.joinToString("\n")
    }

    /**
     * Google News titles often end with " - SourceName". Remove that suffix
     * since we show the source separately.
     */
    private fun cleanGoogleNewsTitle(title: String, source: String?): String {
        if (source != null && title.endsWith(" - $source")) {
            return title.removeSuffix(" - $source")
        }
        // Fallback: remove anything after the last " - " if it looks like a source
        val lastDash = title.lastIndexOf(" - ")
        if (lastDash > 0 && lastDash > title.length / 2) {
            return title.substring(0, lastDash)
        }
        return title
    }

    private fun getMockNews(topics: String): String {
        val topicList = topics.split(",").map { it.trim().lowercase() }
        val headlines = mutableListOf<String>()

        if (topicList.contains("technology") || topicList.contains("tech") || topicList.contains("tecnología")) {
            headlines.add("- Quantum computing chip achieves 99.9% gate fidelity (TechCrunch)")
            headlines.add("- Next-gen electric vehicle solid-state battery enters production (Wired)")
        }
        if (topicList.contains("science") || topicList.contains("space") || topicList.contains("ciencia")) {
            headlines.add("- Webb Telescope discovers atmosphere on rocky exoplanet (NASA)")
            headlines.add("- DeepMind AI predicts structures of 200 million proteins (Nature)")
        }
        if (headlines.isEmpty()) {
            headlines.add("- Global green energy capacity grows by record 40% (Reuters)")
            headlines.add("- Artemis lunar mission schedules astronauts for landing next year (BBC)")
        }

        return headlines.joinToString("\n")
    }
}
