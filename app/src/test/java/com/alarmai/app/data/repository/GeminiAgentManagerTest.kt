package com.alarmai.app.data.repository

import com.alarmai.app.data.local.PreferencesManager
import com.alarmai.app.data.model.GeminiModels
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class GeminiAgentManagerTest {

    @Mock
    private lateinit var prefs: PreferencesManager

    private lateinit var geminiAgentManager: GeminiAgentManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        geminiAgentManager = GeminiAgentManager(prefs)
    }

    /** Minimal valid generateContent response carrying a single text part. */
    private fun textResponse(text: String) =
        """{"candidates":[{"content":{"role":"model","parts":[{"text":"$text"}]}}]}"""

    /** A response containing only an unanswered functionCall - the history-poisoning case. */
    private fun functionCallResponse() =
        """{"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"googleSearch","args":{"q":"x"}}}]}}]}"""

    private fun managerWith(
        responder: suspend (url: String, apiKey: String, body: String) -> String
    ) = GeminiAgentManager(prefs, responder)

    @Test
    fun testStartSessionDemoMode_Spanish() = runBlocking {
        `when`(prefs.getLanguage()).thenReturn("es")
        
        val result = geminiAgentManager.startSession(
            apiKey = "",
            weatherData = "sunny, 22C",
            newsData = "Headline news",
            calendarData = "Meeting at 9 AM"
        )
        
        assertTrue(result.contains("simulación de tu alarma de IA"))
        assertTrue(result.contains("¿Cómo te sientes hoy?"))
    }

    @Test
    fun testStartSessionDemoMode_English() = runBlocking {
        `when`(prefs.getLanguage()).thenReturn("en")
        
        val result = geminiAgentManager.startSession(
            apiKey = "",
            weatherData = "sunny, 22C",
            newsData = "Headline news",
            calendarData = "Meeting at 9 AM"
        )
        
        assertTrue(result.contains("demo of your AI alarm"))
        assertTrue(result.contains("How are you feeling today?"))
    }

    @Test
    fun testSendMessageDemoMode_Spanish() = runBlocking {
        `when`(prefs.getLanguage()).thenReturn("es")
        
        // Ensure session is null (demo mode)
        geminiAgentManager.clearSession()
        
        val weatherResp = geminiAgentManager.sendMessage("¿Cómo está el clima?")
        assertEquals("El pronóstico de hoy es soleado y agradable, alrededor de veintidós grados Celsius.", weatherResp)
        
        val newsResp = geminiAgentManager.sendMessage("noticias")
        assertEquals("En noticias tecnológicas, investigadores lograron avances en la eficiencia de paneles solares.", newsResp)
        
        val otherResp = geminiAgentManager.sendMessage("Hola")
        assertTrue(otherResp.contains("modo demo"))
    }

    @Test
    fun testSendMessageDemoMode_English() = runBlocking {
        `when`(prefs.getLanguage()).thenReturn("en")
        
        // Ensure session is null (demo mode)
        geminiAgentManager.clearSession()
        
        val weatherResp = geminiAgentManager.sendMessage("how is the weather?")
        assertEquals("Today's forecast is sunny and pleasant, about twenty-two degrees Celsius.", weatherResp)
        
        val newsResp = geminiAgentManager.sendMessage("what's the news?")
        assertEquals("In technology news, researchers have made breakthrough progress in solar panel efficiency.", newsResp)
        
        val otherResp = geminiAgentManager.sendMessage("Hello")
        assertTrue(otherResp.contains("demo mode"))
    }

    @Test
    fun testFunctionCallOnlyResponseIsNotCommittedToHistory() = runBlocking {
        // Regression: an unanswered functionCall used to be appended to history, so the NEXT
        // request was rejected with 400 INVALID_ARGUMENT and the session dead-ended permanently.
        `when`(prefs.getLanguage()).thenReturn("en")
        `when`(prefs.getTonePreference()).thenReturn("warm")

        val bodies = mutableListOf<String>()
        var call = 0
        val manager = managerWith { _, _, body ->
            bodies += body
            call++
            if (call == 1) functionCallResponse() else textResponse("Good morning!")
        }

        manager.startSession("key", "sunny", "news", "calendar")
        val reply = manager.sendMessage("hello")

        assertEquals("Good morning!", reply)
        // The follow-up request must carry no functionCall left over from the first response.
        assertFalse(bodies.last().contains("functionCall"))
    }

    @Test
    fun testUserTurnRolledBackWhenRequestFails() = runBlocking {
        // A failed turn used to leave its user message stranded in history, so every later
        // retry re-sent it and compounded the failure.
        `when`(prefs.getLanguage()).thenReturn("en")
        `when`(prefs.getTonePreference()).thenReturn("warm")

        val bodies = mutableListOf<String>()
        var call = 0
        val manager = managerWith { _, _, body ->
            bodies += body
            call++
            // 1 = startSession (ok), 2..7 = failing turn walking the chain, 8 = recovery
            if (call == 1 || call > 7) textResponse("ok") else throw Exception("HTTP 503")
        }

        manager.startSession("key", "sunny", "news", "calendar")
        manager.sendMessage("this turn fails")
        manager.sendMessage("recovery")

        // The recovery request must contain the recovery turn but NOT the failed one.
        val last = bodies.last()
        assertTrue(last.contains("recovery"))
        assertFalse(last.contains("this turn fails"))
    }

    @Test
    fun testQuotaErrorWalksTheFallbackChain() = runBlocking {
        `when`(prefs.getLanguage()).thenReturn("en")
        `when`(prefs.getTonePreference()).thenReturn("warm")

        val urls = mutableListOf<String>()
        var call = 0
        val manager = managerWith { url, _, _ ->
            urls += url
            call++
            if (call < 3) throw Exception("HTTP 429 quota exceeded") else textResponse("ok")
        }

        manager.startSession("key", "sunny", "news", "calendar", GeminiModels.DEFAULT)

        assertEquals(3, urls.size)
        assertTrue(urls[0].contains(GeminiModels.DEFAULT))
        assertTrue(urls[1].contains(GeminiModels.CHAIN[1]))
        assertTrue(urls[2].contains(GeminiModels.CHAIN[2]))
    }

    @Test
    fun testUnknownModel404FallsThroughInsteadOfHardFailing() = runBlocking {
        // Safety net: if a model id in the chain is not recognised by the API, degrade to the
        // next one rather than dead-ending the morning briefing with a hard error.
        `when`(prefs.getLanguage()).thenReturn("en")
        `when`(prefs.getTonePreference()).thenReturn("warm")

        var call = 0
        val manager = managerWith { _, _, _ ->
            call++
            if (call == 1) throw Exception("HTTP 404: models/x is not found") else textResponse("recovered")
        }

        val result = manager.startSession("key", "sunny", "news", "calendar")

        assertEquals("recovered", result)
        assertEquals(2, call)
    }

    @Test
    fun testNonRetryableErrorDoesNotWalkTheChain() = runBlocking {
        `when`(prefs.getLanguage()).thenReturn("en")
        `when`(prefs.getTonePreference()).thenReturn("warm")

        var call = 0
        val manager = managerWith { _, _, _ ->
            call++
            throw Exception("HTTP 400: malformed request")
        }

        // startSession swallows the error into a spoken message; the point is it stopped at one try.
        manager.startSession("key", "sunny", "news", "calendar")
        assertEquals(1, call)
    }

    @Test
    fun testApiKeyIsNotPlacedInTheUrl() = runBlocking {
        // The URL shows up in OkHttp exception messages, which get logged and shown on screen.
        `when`(prefs.getLanguage()).thenReturn("en")
        `when`(prefs.getTonePreference()).thenReturn("warm")

        var seenUrl = ""
        var seenKey = ""
        val manager = managerWith { url, apiKey, _ ->
            seenUrl = url
            seenKey = apiKey
            textResponse("ok")
        }

        manager.startSession("super-secret-key", "sunny", "news", "calendar")

        assertFalse(seenUrl.contains("super-secret-key"))
        assertFalse(seenUrl.contains("key="))
        assertEquals("super-secret-key", seenKey)
    }

    @Test
    fun testStartSessionDetailedReturnsThePromptItActuallySent() = runBlocking {
        // PrefetchWorker caches this prompt for reconstructSession; it used to rebuild its own
        // divergent copy, so the replayed history did not match what generated the briefing.
        `when`(prefs.getLanguage()).thenReturn("en")
        `when`(prefs.getTonePreference()).thenReturn("warm")

        var sentBody = ""
        val manager = managerWith { _, _, body ->
            sentBody = body
            textResponse("Briefing text")
        }

        val result = manager.startSessionDetailed("key", "sunny 22C", "Big headline", "Standup 9am")

        assertEquals("Briefing text", result.text)
        assertTrue(result.prompt.contains("sunny 22C"))
        assertTrue(result.prompt.contains("Big headline"))
        // The returned prompt must be the one that went over the wire.
        assertTrue(sentBody.contains("Big headline"))
    }

    @Test
    fun testSerializationWithThoughtSignature() {
        val content = GeminiAgentManager.Content(
            role = "model",
            parts = listOf(
                GeminiAgentManager.FunctionCallPart(
                    name = "getWorldCupMatchesForDate",
                    args = mapOf("dateString" to "2026-06-12"),
                    thoughtSignature = "test_signature_abc_123"
                )
            )
        )
        val json = geminiAgentManager.contentToJson(content)
        assertEquals("model", json.getString("role"))
        
        val partsArray = json.getJSONArray("parts")
        assertEquals(1, partsArray.length())
        
        val partObj = partsArray.getJSONObject(0)
        assertTrue(partObj.has("functionCall"))
        assertEquals("test_signature_abc_123", partObj.getString("thoughtSignature"))
    }

    @Test
    fun testDeserializationWithThoughtSignature() {
        val jsonString = """
            {
                "candidates": [
                    {
                        "content": {
                            "role": "model",
                            "parts": [
                                {
                                    "functionCall": {
                                        "name": "getWorldCupMatchesForDate",
                                        "args": {
                                            "dateString": "2026-06-12"
                                        }
                                    },
                                    "thoughtSignature": "test_signature_abc_123"
                                }
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()
        
        val content = geminiAgentManager.parseResponseContent(jsonString)
        assertEquals("model", content?.role)
        assertEquals(1, content?.parts?.size)
        
        val part = content?.parts?.get(0)
        assertTrue(part is GeminiAgentManager.FunctionCallPart)
        val fcPart = part as GeminiAgentManager.FunctionCallPart
        assertEquals("getWorldCupMatchesForDate", fcPart.name)
        assertEquals("2026-06-12", fcPart.args["dateString"])
        assertEquals("test_signature_abc_123", fcPart.thoughtSignature)
    }

    @Test
    fun testDeserializationWithThoughtSignatureSnakeCase() {
        val jsonString = """
            {
                "candidates": [
                    {
                        "content": {
                            "role": "model",
                            "parts": [
                                {
                                    "functionCall": {
                                        "name": "getWorldCupMatchesForDate",
                                        "args": {
                                            "dateString": "2026-06-12"
                                        }
                                    },
                                    "thought_signature": "test_signature_abc_123"
                                }
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()
        
        val content = geminiAgentManager.parseResponseContent(jsonString)
        assertEquals("model", content?.role)
        assertEquals(1, content?.parts?.size)
        
        val part = content?.parts?.get(0)
        assertTrue(part is GeminiAgentManager.FunctionCallPart)
        val fcPart = part as GeminiAgentManager.FunctionCallPart
        assertEquals("getWorldCupMatchesForDate", fcPart.name)
        assertEquals("2026-06-12", fcPart.args["dateString"])
        assertEquals("test_signature_abc_123", fcPart.thoughtSignature)
    }
}
