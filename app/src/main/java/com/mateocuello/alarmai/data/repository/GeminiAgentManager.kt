@file:OptIn(com.google.ai.client.generativeai.type.GenerativeBeta::class)

package com.mateocuello.alarmai.data.repository

import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.FunctionResponsePart
import com.google.ai.client.generativeai.type.FunctionCallPart
import com.google.ai.client.generativeai.type.defineFunction
import com.mateocuello.alarmai.data.local.PreferencesManager
import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GeminiAgentManager(private val context: Context, private val prefs: PreferencesManager) {
    private var chatSession: Chat? = null

    private fun getWorldCupContextText(): String {
        return try {
            context.assets.open("worldcup_context.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun getSystemInstructionText(language: String, tone: String): String {
        val now = java.util.Date()
        val worldCupContext = getWorldCupContextText()
        return if (language == "es") {
            val dateSdf = java.text.SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", java.util.Locale("es", "ES"))
            val timeSdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale("es", "ES"))
            val dateStr = dateSdf.format(now)
            val timeStr = timeSdf.format(now)
            """
                Eres un asistente de voz matutino cálido, servicial y enérgico.
                Tu tarea es despertar al usuario y conversar con él.
                Dado que tus respuestas se leerán en voz alta mediante Text-to-Speech (TTS), DEBES:
                1. Mantener todas tus respuestas muy cortas, claras y fáciles de entender al hablar (menos de 120 palabras).
                2. No uses formato markdown (nada de asteriscos, viñetas, títulos o emojis). Habla con oraciones normales.
                3. Termina tus intervenciones con una pregunta amistosa y abierta para animar al usuario a responder.
                
                Contexto del usuario:
                - Fecha y hora actual del usuario: $dateStr, $timeStr
                - Copa Mundial FIFA 2026: Se está disputando en este momento (junio y julio de 2026).
                - Estilo y tono de comunicación preferido: $tone
                
                $worldCupContext
            """.trimIndent()
        } else {
            val dateSdf = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.US)
            val timeSdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
            val dateStr = dateSdf.format(now)
            val timeStr = timeSdf.format(now)
            """
                You are a warm, helpful, and energetic morning AI assistant. 
                Your task is to wake the user up and converse with them.
                Since your responses will be read out loud via Text-to-Speech (TTS), you MUST:
                1. Keep all responses very short, clear, and easy to understand when spoken (under 120 words).
                2. Do not use markdown (no asterisks, bullet points, headers, or emojis). Speak in plain sentences.
                3. End your statements with a friendly, open-ended question to encourage the user to reply.
                
                User Context:
                - User's current date and time: $dateStr at $timeStr
                - FIFA World Cup 2026: Currently ongoing (June/July 2026).
                - Preferred communication style/tone: $tone
                
                $worldCupContext
            """.trimIndent()
        }
    }

    suspend fun startSession(
        apiKey: String,
        weatherData: String,
        newsData: String,
        calendarData: String,
        worldCupData: String = "",
        modelName: String = "gemini-3.5-flash"
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            val language = prefs.getLanguage()
            if (language == "es") {
                val wcInfo = if (worldCupData.isNotBlank() && !worldCupData.contains("No matches", ignoreCase = true)) {
                    " Además, hoy hay programados partidos para el Mundial 2026. "
                } else " "
                return@withContext "¡Buenos días! Esta es una simulación de tu alarma de IA. Como tu clave de API de Gemini no está configurada, estoy funcionando en modo de simulación local. El clima de hoy se ve grandioso, tu agenda está libre y la computación cuántica es noticia de portada.${wcInfo}¿Cómo te sientes hoy?"
            } else {
                val wcInfo = if (worldCupData.isNotBlank() && !worldCupData.contains("No matches", ignoreCase = true)) {
                    " Also, today there are some matches scheduled for the 2026 World Cup. "
                } else " "
                return@withContext "Good morning! This is a demo of your AI alarm. Since your Gemini API key is not configured in settings, I am running in local simulation mode. Today's weather looks great, your calendar is clear, and quantum computing made headlines.${wcInfo}How are you feeling today?"
            }
        }

        try {
            val language = prefs.getLanguage()
            val tone = prefs.getTonePreference()

            val systemInstructionText = getSystemInstructionText(language, tone)

            val updateToneFunction = defineFunction(
                name = "updateTonePreference",
                description = "Updates the user's preferred communication tone or style of the assistant (e.g. sarcastic, formal, energetic, funny, etc.).",
                arg1 = Schema.str(
                    name = "newPreference",
                    description = "The new preferred tone or style of communication requested by the user"
                ),
                function = { newPreference: String ->
                    prefs.saveTonePreference(newPreference)
                    JSONObject().apply { put("success", true) }
                }
            )

            val getWorldCupMatchesForDateFunction = defineFunction(
                name = "getWorldCupMatchesForDate",
                description = "Retrieves the scheduled FIFA World Cup 2026 matches for a specific date.",
                arg1 = Schema.str(
                    name = "dateString",
                    description = "The date to query in yyyy-MM-dd format (e.g. 2026-06-12)"
                ),
                function = { dateString: String ->
                    val repo = WorldCupRepository()
                    val summary = repo.getTodayMatchesSummary(context, dateString)
                    JSONObject().apply { put("summary", summary) }
                }
            )

            val model = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                systemInstruction = content { text(systemInstructionText) },
                tools = listOf(Tool(functionDeclarations = listOf(updateToneFunction, getWorldCupMatchesForDateFunction)))
            )

            val session = model.startChat()
            chatSession = session

            val initialPrompt = if (language == "es") {
                """
                    Comienza el resumen matutino. Aquí están los datos del día:
                    - Clima: $weatherData
                    - Noticias: $newsData
                    - Eventos de Calendario: $calendarData
                    - Partidos de la Copa Mundial FIFA 2026 de hoy: $worldCupData
                    
                    Por favor, saluda al usuario cálidamente, menciona la hora (o deséale un buen día), resume estos datos (incluyendo los partidos del Mundial de hoy si los hay) de manera muy atractiva y concisa, y pregúntale cómo le gustaría empezar el día.
                """.trimIndent()
            } else {
                """
                    Start the morning briefing. Here is the daily data:
                    - Weather: $weatherData
                    - News: $newsData
                    - Calendar Events: $calendarData
                    - Today's FIFA World Cup 2026 Matches: $worldCupData
                    
                    Please greet the user warmly, state the time (or wish them a good morning), summarize this data (including today's World Cup matches if any are scheduled) in a highly engaging, concise way, and ask how they'd like to start their day.
                """.trimIndent()
            }

            val response = session.sendMessage(initialPrompt)
            response.text ?: (if (language == "es") "¡Buenos días! Tuve problemas para generar tu resumen. ¿Qué puedo hacer por ti hoy?" else "Good morning! I had trouble generating your briefing. What can I do for you today?")
        } catch (e: Exception) {
            val language = prefs.getLanguage()
            if (language == "es") {
                "¡Buenos días! Encontré un error al configurar la sesión de IA: ${e.localizedMessage}. ¿Cómo puedo ayudarte manualmente?"
            } else {
                "Good morning! I encountered an error setting up the AI session: ${e.localizedMessage}. How can I assist you manually?"
            }
        }
    }

    fun reconstructSession(
        apiKey: String,
        modelName: String,
        prompt: String,
        response: String
    ) {
        if (apiKey.isBlank()) {
            chatSession = null
            return
        }

        try {
            val language = prefs.getLanguage()
            val tone = prefs.getTonePreference()

            val systemInstructionText = getSystemInstructionText(language, tone)

            val updateToneFunction = defineFunction(
                name = "updateTonePreference",
                description = "Updates the user's preferred communication tone or style of the assistant (e.g. sarcastic, formal, energetic, funny, etc.).",
                arg1 = Schema.str(
                    name = "newPreference",
                    description = "The new preferred tone or style of communication requested by the user"
                ),
                function = { newPreference: String ->
                    prefs.saveTonePreference(newPreference)
                    JSONObject().apply { put("success", true) }
                }
            )

            val getWorldCupMatchesForDateFunction = defineFunction(
                name = "getWorldCupMatchesForDate",
                description = "Retrieves the scheduled FIFA World Cup 2026 matches for a specific date.",
                arg1 = Schema.str(
                    name = "dateString",
                    description = "The date to query in yyyy-MM-dd format (e.g. 2026-06-12)"
                ),
                function = { dateString: String ->
                    val repo = WorldCupRepository()
                    val summary = repo.getTodayMatchesSummary(context, dateString)
                    JSONObject().apply { put("summary", summary) }
                }
            )

            val model = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                systemInstruction = content { text(systemInstructionText) },
                tools = listOf(Tool(functionDeclarations = listOf(updateToneFunction, getWorldCupMatchesForDateFunction)))
            )

            val history = listOf(
                content(role = "user") { text(prompt) },
                content(role = "model") { text(response) }
            )
            chatSession = model.startChat(history = history)
        } catch (e: Exception) {
            e.printStackTrace()
            chatSession = null
        }
    }

    suspend fun sendMessage(userInput: String): String = withContext(Dispatchers.IO) {
        val session = chatSession
        if (session == null) {
            // Demo mode fallback conversation
            val language = prefs.getLanguage()
            if (language == "es") {
                return@withContext when {
                    userInput.contains("clima", ignoreCase = true) || userInput.contains("tiempo", ignoreCase = true) -> "El pronóstico de hoy es soleado y agradable, alrededor de veintidós grados Celsius."
                    userInput.contains("noticias", ignoreCase = true) -> "En noticias tecnológicas, investigadores lograron avances en la eficiencia de paneles solares."
                    userInput.contains("calendario", ignoreCase = true) || userInput.contains("agenda", ignoreCase = true) -> "No tienes eventos próximos en tu calendario."
                    userInput.contains("mundial", ignoreCase = true) || userInput.contains("copa", ignoreCase = true) || userInput.contains("partido", ignoreCase = true) -> "Hoy en el Mundial 2026 se enfrentan Canadá contra Bosnia a las tres de la tarde y Estados Unidos contra Paraguay a las seis de la tarde."
                    else -> "¡Te escucho! Estoy funcionando en modo demo, pero una vez que configures tu clave API de Gemini en los ajustes, podremos tener una conversación completa."
                }
            } else {
                return@withContext when {
                    userInput.contains("weather", ignoreCase = true) -> "Today's forecast is sunny and pleasant, about twenty-two degrees Celsius."
                    userInput.contains("news", ignoreCase = true) -> "In technology news, researchers have made breakthrough progress in solar panel efficiency."
                    userInput.contains("calendar", ignoreCase = true) || userInput.contains("schedule", ignoreCase = true) -> "You have no upcoming events on your calendar."
                    userInput.contains("world cup", ignoreCase = true) || userInput.contains("match", ignoreCase = true) || userInput.contains("game", ignoreCase = true) -> "Today in the 2026 World Cup, Canada plays Bosnia & Herzegovina at 3 PM and USA plays Paraguay at 6 PM."
                    else -> "I hear you! I'm running in demo mode, but once you set your Gemini API key in settings, we can have a full open-ended conversation about anything."
                }
            }
        }

        try {
            var response = session.sendMessage(userInput)
            var functionCall = response.candidates.firstOrNull()?.content?.parts
                ?.filterIsInstance<FunctionCallPart>()
                ?.firstOrNull()

            while (functionCall != null) {
                if (functionCall.name == "updateTonePreference") {
                    val newPreference = functionCall.args["newPreference"] ?: ""
                    prefs.saveTonePreference(newPreference)

                    val responsePart = FunctionResponsePart(
                        "updateTonePreference",
                        JSONObject().apply {
                            put("success", true)
                        }
                    )

                    response = session.sendMessage(content {
                        part(responsePart)
                    })
                    functionCall = response.candidates.firstOrNull()?.content?.parts
                        ?.filterIsInstance<FunctionCallPart>()
                        ?.firstOrNull()
                } else if (functionCall.name == "getWorldCupMatchesForDate") {
                    val dateString = functionCall.args["dateString"] ?: ""
                    val repo = WorldCupRepository()
                    val summary = repo.getTodayMatchesSummary(context, dateString)

                    val responsePart = FunctionResponsePart(
                        "getWorldCupMatchesForDate",
                        JSONObject().apply {
                            put("summary", summary)
                        }
                    )

                    response = session.sendMessage(content {
                        part(responsePart)
                    })
                    functionCall = response.candidates.firstOrNull()?.content?.parts
                        ?.filterIsInstance<FunctionCallPart>()
                        ?.firstOrNull()
                } else {
                    break
                }
            }

            response.text ?: "I heard you, but I couldn't generate a reply. Can you repeat that?"
        } catch (e: Exception) {
            "Sorry, I had trouble contacting the AI. Error: ${e.localizedMessage}"
        }
    }

    fun clearSession() {
        chatSession = null
    }
}


