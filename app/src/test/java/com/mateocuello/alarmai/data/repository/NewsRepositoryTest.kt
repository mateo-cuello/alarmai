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

    @Before
    fun setUp() {
        logMock = Mockito.mockStatic(Log::class.java)
        logMock.`when`<Int> { Log.d(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.w(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any<String>(), any<String>(), any()) }.thenReturn(0)

        // Create a spy of NewsRepository
        newsRepository = spy(NewsRepository())
    }

    @After
    fun tearDown() {
        logMock.close()
    }

    @Test
    fun testGetNews_Success() = runBlocking {
        doReturn("- Major scientific discovery made (Science Journal)")
            .whenever(newsRepository).getNews(eq("science"), eq("en"))

        val news = newsRepository.getNews("science", "en")
        assertTrue(news.contains("Major scientific discovery made"))
        assertTrue(news.contains("(Science Journal)"))
    }

    @Test
    fun testGetNews_HttpFailure_FallbackToMock() = runBlocking {
        doReturn("Failed to fetch news: Network error")
            .whenever(newsRepository).getNews(eq("technology"), eq("en"))

        val news = newsRepository.getNews("technology", "en")
        assertTrue(news.contains("Failed to fetch news"))
    }

    @Test
    fun testSearchNewsByQuery_Success() = runBlocking {
        doReturn("- SpaceX launches new rocket (SpaceNews)")
            .whenever(newsRepository).getNews(eq("space"), eq("en"))

        val result = newsRepository.searchNewsByQuery("space", "en")
        assertTrue(result.contains("SpaceX launches new rocket"))
    }
}
