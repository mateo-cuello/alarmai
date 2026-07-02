package com.mateocuello.alarmai.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class NewsRepository {

    suspend fun getNews(topics: String, language: String = "en"): String = withContext(Dispatchers.IO) {
        try {
            val query = if (topics.isNotBlank()) topics.replace(" ", "+") else "news"
            val urlString = if (language == "es") {
                "https://news.google.com/rss/search?q=$query&hl=es-419&gl=AR&ceid=AR:es-419"
            } else {
                "https://news.google.com/rss/search?q=$query&hl=en-US&gl=US&ceid=US:en"
            }
            
            val url = URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(connection.getInputStream())
            
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
            
            if (headlines.isEmpty()) "No news found." else headlines.joinToString(". ")
        } catch (e: Exception) {
            "Failed to fetch news: ${e.localizedMessage}"
        }
    }

    suspend fun searchNewsByQuery(query: String, language: String = "en"): String = withContext(Dispatchers.IO) {
        getNews(query, language)
    }
}
