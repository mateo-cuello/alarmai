package com.mateocuello.alarmai.data.repository

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*

class NewsRepositoryTest {

    private lateinit var logMock: MockedStatic<Log>
    private lateinit var newsRepository: NewsRepository

    private var mockFetchRssResult = ""
    private var mockFetchRssException: Exception? = null

    @Before
    fun setUp() {
        logMock = Mockito.mockStatic(Log::class.java)
        logMock.`when`<Int> { Log.d(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.w(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any<String>(), any<String>(), any()) }.thenReturn(0)

        // Create a spy of NewsRepository to stub internal fetchRssHeadlines
        newsRepository = spy(NewsRepository())

        doAnswer {
            val exception = mockFetchRssException
            if (exception != null) {
                throw exception
            }
            mockFetchRssResult
        }.whenever(newsRepository).fetchRssHeadlines(any(), any(), any())
    }

    @After
    fun tearDown() {
        logMock.close()
    }

    @Test
    fun testGetNews_Success() = runBlocking {
        mockFetchRssException = null
        mockFetchRssResult = "- Major scientific discovery made (Science Journal)"

        val news = newsRepository.getNews("science", "en")
        assertTrue(news.contains("Major scientific discovery made"))
        assertTrue(news.contains("(Science Journal)"))
    }

    @Test
    fun testGetNews_HttpFailure_FallbackToMock() = runBlocking {
        // Force fetchRssHeadlines to throw an exception to trigger fallback
        mockFetchRssException = RuntimeException("Network error")

        val news = newsRepository.getNews("technology", "en")
        assertTrue(news.contains("No live news available") || news.contains("Failed to fetch live news"))
        assertTrue(news.contains("Quantum computing chip")) // comes from getMockNews fallback
    }

    @Test
    fun testSearchNewsByQuery_Success() = runBlocking {
        mockFetchRssException = null
        mockFetchRssResult = "- SpaceX launches new rocket (SpaceNews)"

        val result = newsRepository.searchNewsByQuery("space", "en")
        assertTrue(result.contains("SpaceX launches new rocket"))
    }
}
