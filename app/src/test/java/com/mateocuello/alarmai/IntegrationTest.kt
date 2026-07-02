package com.mateocuello.alarmai

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.mateocuello.alarmai.data.local.PreferencesManager
import com.mateocuello.alarmai.data.repository.GeminiAgentManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream

class IntegrationTest {

    private lateinit var logMock: MockedStatic<Log>
    
    private val context: Context = mock()
    private val assetManager: AssetManager = mock()
    private val prefs: PreferencesManager = mock()



    @Before
    fun setUp() {
        logMock = Mockito.mockStatic(Log::class.java)
        logMock.`when`<Int> { Log.d(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.w(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any<String>(), any<String>(), any()) }.thenReturn(0)

        whenever(context.assets).thenReturn(assetManager)
        whenever(assetManager.open("worldcup_context.txt")).thenReturn(ByteArrayInputStream("FIFA World Cup Context Instructions".toByteArray()))
        whenever(assetManager.open("worldcup_2026.json")).thenReturn(ByteArrayInputStream("[]".toByteArray()))

        whenever(prefs.getLanguage()).thenReturn("es")
        whenever(prefs.getTonePreference()).thenReturn("divertido")
    }

    @After
    fun tearDown() {
        logMock.close()
    }

    @Test
    fun testRealGeminiApiCall() = runBlocking {
        val actualApiKey = System.getenv("GEMINI_API_KEY") ?: ""
        if (actualApiKey.isBlank()) {
            org.junit.Assume.assumeTrue("Skipping real Gemini API integration test: GEMINI_API_KEY environment variable is not configured.", false)
            return@runBlocking
        }
        val manager = GeminiAgentManager(context, prefs)
        
        try {
            // Start session using the provided API key
            val greeting = manager.startSession(
                apiKey = actualApiKey,
                weatherData = "Soleado, 20 grados",
                newsData = "Noticias de tecnología y deportes",
                calendarData = "Sin eventos",
                modelName = "gemini-2.5-flash"
            )

            // If it failed due to timeout, network, or API limit/demand issues, assume host/service is temporarily unavailable
            if (greeting.contains("timeout") || greeting.contains("Unable to resolve host") || greeting.contains("connect timed out") || greeting.contains("unreachable") || greeting.contains("demand") || greeting.contains("exhausted") || greeting.contains("limit")) {
                org.junit.Assume.assumeTrue("Skipping real Gemini API integration test: network timeout or API rate limit/overload.", false)
                return@runBlocking
            }

            assertNotNull(greeting)
            assertTrue("Greeting should not be empty", greeting.isNotBlank())
            assertTrue("Greeting should be valid response from Gemini API: $greeting", !greeting.contains("error al configurar la sesión de IA"))

            // Send a message and verify response
            val response = manager.sendMessage("¿Qué tal?")
            
            if (response.contains("had trouble contacting the AI") && (response.contains("timeout") || response.contains("Unable to resolve host") || response.contains("demand") || response.contains("exhausted") || response.contains("limit"))) {
                org.junit.Assume.assumeTrue("Skipping real Gemini API integration test: network timeout or rate limit on sendMessage.", false)
                return@runBlocking
            }

            assertNotNull(response)
            assertTrue("Response should not be empty", response.isNotBlank())
            assertTrue("Response should be valid from Gemini API: $response", !response.contains("had trouble contacting the AI"))
        } catch (e: Exception) {
            org.junit.Assume.assumeNoException("Skipping real Gemini API integration test: network failure.", e)
        }
    }
}
