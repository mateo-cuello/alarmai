package com.mateocuello.alarmai.data.repository

import android.content.Context
import android.content.res.AssetManager
import com.mateocuello.alarmai.data.local.PreferencesManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.ByteArrayInputStream

class GeminiAgentManagerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var assetManager: AssetManager

    @Mock
    private lateinit var prefs: PreferencesManager

    private lateinit var geminiAgentManager: GeminiAgentManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(context.assets).thenReturn(assetManager)
        `when`(assetManager.open("worldcup_context.txt")).thenReturn(ByteArrayInputStream("Mock World Cup Context".toByteArray()))
        geminiAgentManager = GeminiAgentManager(context, prefs)
    }

    @Test
    fun testStartSessionDemoMode_Spanish() = runBlocking {
        `when`(prefs.getLanguage()).thenReturn("es")
        
        val result = geminiAgentManager.startSession(
            apiKey = "",
            weatherData = "sunny, 22C",
            newsData = "Headline news",
            calendarData = "Meeting at 9 AM",
            worldCupData = ""
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
            calendarData = "Meeting at 9 AM",
            worldCupData = ""
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
