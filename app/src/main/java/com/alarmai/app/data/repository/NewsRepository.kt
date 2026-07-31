package com.alarmai.app.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

class NewsRepository {

    /**
     * Returns headlines, or an empty string on failure. Blank means "no news section" to the
     * prompt builder — an error string here would be interpolated into the briefing and read
     * aloud to a half-asleep user.
     */
    suspend fun getNews(topics: String, language: String = "en"): String = withContext(Dispatchers.IO) {
        try {
            // Encode rather than only swapping spaces: topics are free text, so an "&", "#" or
            // accented character would otherwise corrupt the query or the surrounding URL.
            val rawQuery = if (topics.isNotBlank()) topics else "news"
            val query = URLEncoder.encode(rawQuery, "UTF-8")
            val urlString = if (language == "es") {
                "https://news.google.com/rss/search?q=$query&hl=es-419&gl=AR&ceid=AR:es-419"
            } else {
                "https://news.google.com/rss/search?q=$query&hl=en-US&gl=US&ceid=US:en"
            }

            val url = URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val builder = newSecureDocumentBuilder()
            // use { } so a parse failure can't leak the socket; the old code left it open.
            val doc = connection.getInputStream().use { builder.parse(it) }

            val items = doc.getElementsByTagName("item")
            val headlines = mutableListOf<String>()

            for (i in 0 until minOf(items.length, 3)) {
                val node = items.item(i)
                if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    val element = node as org.w3c.dom.Element
                    val titleNode = element.getElementsByTagName("title").item(0)
                    if (titleNode != null) {
                        headlines.add(titleNode.textContent)
                    }
                }
            }

            headlines.joinToString(". ")
        } catch (e: Exception) {
            Log.e("NewsRepository", "Failed to fetch news: ${e.localizedMessage}")
            ""
        }
    }

    /**
     * Parsing a remote feed with the default factory allows DOCTYPE/external entity resolution,
     * which is a file-disclosure and SSRF vector if the response is ever tampered with.
     */
    private fun newSecureDocumentBuilder() =
        DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }.newDocumentBuilder()

    suspend fun searchNewsByQuery(query: String, language: String = "en"): String = withContext(Dispatchers.IO) {
        getNews(query, language)
    }
}
