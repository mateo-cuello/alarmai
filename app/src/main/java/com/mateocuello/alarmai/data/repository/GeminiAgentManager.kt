package com.mateocuello.alarmai.data.repository

import android.content.Context
import com.mateocuello.alarmai.data.local.PreferencesManager
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request

class GeminiAgentManager(private val context: Context, private val prefs: PreferencesManager) {

    data class Content(
        val role: String,
        val parts: List<Part>
    )

    sealed interface Part

    data class TextPart(val text: String) : Part

    data class FunctionCallPart(
        val name: String,
        val args: Map<String, Any?>,
        val thoughtSignature: String? = null
    ) : Part

    data class FunctionResponsePart(
        val name: String,
        val response: Map<String, Any?>
    ) : Part

    class CustomChatSession(
        val apiKey: String,
        val modelName: String,
        val systemInstructionText: String,
        val history: MutableList<Content> = mutableListOf()
    )

    private var chatSession: CustomChatSession? = null

    private fun getWorldCupContextText(): String {
        val instructions = try {
            context.assets.open("worldcup_context.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            ""
        }
        val fixture = try {
            context.assets.open("worldcup_2026.json").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            ""
        }
        return "$instructions\n\nComplete FIFA World Cup 2026 Fixture Schedule (JSON):\n$fixture"
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
        modelName: String = "gemini-2.5-flash"
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

            val session = CustomChatSession(
                apiKey = apiKey,
                modelName = modelName,
                systemInstructionText = systemInstructionText
            )
            chatSession = session

            sendMessageInternal(session, initialPrompt)
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

            val session = CustomChatSession(
                apiKey = apiKey,
                modelName = modelName,
                systemInstructionText = systemInstructionText
            )
            session.history.add(Content("user", listOf(TextPart(prompt))))
            session.history.add(Content("model", listOf(TextPart(response))))
            chatSession = session
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
                    userInput.contains("mundial", ignoreCase = true) || 
                    userInput.contains("copa", ignoreCase = true) || 
                    userInput.contains("partido", ignoreCase = true) || 
                    userInput.contains("fixture", ignoreCase = true) || 
                    userInput.contains("cronograma", ignoreCase = true) || 
                    userInput.contains("programación", ignoreCase = true) || 
                    ((userInput.contains("calendario", ignoreCase = true) || userInput.contains("agenda", ignoreCase = true)) && 
                     (userInput.contains("mundial", ignoreCase = true) || userInput.contains("copa", ignoreCase = true) || userInput.contains("partido", ignoreCase = true) || userInput.contains("fixture", ignoreCase = true))) -> 
                        "Hoy en el Mundial 2026 se enfrentan Canadá contra Bosnia a las tres de la tarde y Estados Unidos contra Paraguay a las seis de la tarde."
                    userInput.contains("calendario", ignoreCase = true) || userInput.contains("agenda", ignoreCase = true) -> "No tienes eventos próximos en tu calendario."
                    else -> "¡Te escucho! Estoy funcionando en modo demo, pero una vez que configures tu clave API de Gemini en los ajustes, podremos tener una conversación completa."
                }
            } else {
                return@withContext when {
                    userInput.contains("weather", ignoreCase = true) -> "Today's forecast is sunny and pleasant, about twenty-two degrees Celsius."
                    userInput.contains("news", ignoreCase = true) -> "In technology news, researchers have made breakthrough progress in solar panel efficiency."
                    userInput.contains("world cup", ignoreCase = true) || 
                    userInput.contains("worldcup", ignoreCase = true) || 
                    userInput.contains("match", ignoreCase = true) || 
                    userInput.contains("game", ignoreCase = true) || 
                    userInput.contains("fixture", ignoreCase = true) || 
                    userInput.contains("bracket", ignoreCase = true) || 
                    ((userInput.contains("calendar", ignoreCase = true) || userInput.contains("schedule", ignoreCase = true)) && 
                     (userInput.contains("world cup", ignoreCase = true) || userInput.contains("worldcup", ignoreCase = true) || userInput.contains("match", ignoreCase = true) || userInput.contains("game", ignoreCase = true) || userInput.contains("fixture", ignoreCase = true))) -> 
                        "Today in the 2026 World Cup, Canada plays Bosnia & Herzegovina at 3 PM and USA plays Paraguay at 6 PM."
                    userInput.contains("calendar", ignoreCase = true) || userInput.contains("schedule", ignoreCase = true) -> "You have no upcoming events on your calendar."
                    else -> "I hear you! I'm running in demo mode, but once you set your Gemini API key in settings, we can have a full open-ended conversation about anything."
                }
            }
        }

        try {
            sendMessageInternal(session, userInput)
        } catch (e: Exception) {
            "Sorry, I had trouble contacting the AI. Error: ${e.localizedMessage}"
        }
    }

    private suspend fun sendMessageInternal(session: CustomChatSession, userInput: String): String {
        val userContent = Content("user", listOf(TextPart(userInput)))
        session.history.add(userContent)

        val responseContent = callGeminiAndHandleFunctions(session)
        val textResponse = responseContent.parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }
        return textResponse.ifBlank {
            if (prefs.getLanguage() == "es") {
                "Escuché tu mensaje, pero no pude generar una respuesta. ¿Puedes repetirlo?"
            } else {
                "I heard you, but I couldn't generate a reply. Can you repeat that?"
            }
        }
    }

    private suspend fun callGeminiAndHandleFunctions(session: CustomChatSession): Content {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${session.modelName}:generateContent?key=${session.apiKey}"

        while (true) {
            val requestBody = buildRequestBody(session.history, session.systemInstructionText)
            val responseJson = makePostRequest(url, requestBody.toString())
            val responseContent = parseResponseContent(responseJson) ?: throw Exception("Failed to parse response")

            // Add the model's response to history
            session.history.add(responseContent)

            // Extract all function calls from the response
            val functionCalls = responseContent.parts.filterIsInstance<FunctionCallPart>()
            if (functionCalls.isEmpty()) {
                // Done!
                return responseContent
            }

            // Execute function calls
            val responseParts = mutableListOf<Part>()
            for (functionCall in functionCalls) {
                val name = functionCall.name
                if (name.endsWith("updateTonePreference")) {
                    val newPreference = functionCall.args["newPreference"]?.toString() ?: ""
                    prefs.saveTonePreference(newPreference)
                    responseParts.add(FunctionResponsePart(name, mapOf("success" to true)))
                } else if (name.endsWith("getWorldCupMatchesForDate") || name.endsWith("getWorldCupMatchForDate")) {
                    val dateString = functionCall.args["dateString"]?.toString()
                        ?: functionCall.args["date"]?.toString()
                        ?: functionCall.args["date_string"]?.toString()
                        ?: ""
                    val repo = WorldCupRepository()
                    val summary = repo.getTodayMatchesSummary(context, dateString)
                    responseParts.add(FunctionResponsePart(name, mapOf("summary" to summary)))
                }
            }

            // Add function response as user turn containing FunctionResponseParts
            val toolResponseContent = Content("user", responseParts)
            session.history.add(toolResponseContent)
        }
    }

    internal fun contentToJson(content: Content): JSONObject {
        val json = JSONObject()
        json.put("role", content.role)
        val partsArray = JSONArray()
        for (part in content.parts) {
            val partJson = JSONObject()
            when (part) {
                is TextPart -> {
                    partJson.put("text", part.text)
                }
                is FunctionCallPart -> {
                    val fcJson = JSONObject()
                    fcJson.put("name", part.name)
                    val argsJson = JSONObject()
                    for ((k, v) in part.args) {
                        argsJson.put(k, v)
                    }
                    fcJson.put("args", argsJson)
                    partJson.put("functionCall", fcJson)
                    if (!part.thoughtSignature.isNullOrEmpty()) {
                        partJson.put("thoughtSignature", part.thoughtSignature)
                    }
                }
                is FunctionResponsePart -> {
                    val frJson = JSONObject()
                    frJson.put("name", part.name)
                    val respJson = JSONObject()
                    for ((k, v) in part.response) {
                        respJson.put(k, v)
                    }
                    frJson.put("response", respJson)
                    partJson.put("functionResponse", frJson)
                }
            }
            partsArray.put(partJson)
        }
        json.put("parts", partsArray)
        return json
    }

    internal fun parseResponseContent(jsonString: String): Content? {
        val root = JSONObject(jsonString)
        val candidates = root.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val candidate = candidates.getJSONObject(0)
        val contentJson = candidate.optJSONObject("content") ?: return null
        val role = contentJson.optString("role", "model")
        val partsArray = contentJson.optJSONArray("parts") ?: return null
        val parts = mutableListOf<Part>()
        for (i in 0 until partsArray.length()) {
            val partJson = partsArray.getJSONObject(i)
            if (partJson.has("text")) {
                parts.add(TextPart(partJson.getString("text")))
            } else if (partJson.has("functionCall")) {
                val fcJson = partJson.getJSONObject("functionCall")
                val name = fcJson.getString("name")
                val argsJson = fcJson.optJSONObject("args")
                val args = mutableMapOf<String, Any?>()
                if (argsJson != null) {
                    val keys = argsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        args[key] = argsJson.get(key)
                    }
                }
                val thoughtSignature = if (partJson.has("thoughtSignature")) {
                    partJson.optString("thoughtSignature")
                } else if (partJson.has("thought_signature")) {
                    partJson.optString("thought_signature")
                } else {
                    null
                }
                parts.add(FunctionCallPart(name, args, thoughtSignature))
            }
        }
        return Content(role, parts)
    }

    private fun buildRequestBody(
        history: List<Content>,
        systemInstructionText: String
    ): JSONObject {
        val body = JSONObject()

        // Contents
        val contentsArray = JSONArray()
        for (content in history) {
            contentsArray.put(contentToJson(content))
        }
        body.put("contents", contentsArray)

        // System instruction
        val sysInstruction = JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText)))
        }
        body.put("systemInstruction", sysInstruction)

        // Tools
        val toolsArray = JSONArray()
        val toolObject = JSONObject()
        val functionDeclarations = JSONArray()

        // updateTonePreference
        val updateTone = JSONObject().apply {
            put("name", "updateTonePreference")
            put("description", "Updates the user's preferred communication tone or style of the assistant (e.g. sarcastic, formal, energetic, funny, etc.).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("newPreference", JSONObject().apply {
                        put("type", "string")
                        put("description", "The new preferred tone or style of communication requested by the user")
                    })
                })
                put("required", JSONArray().put("newPreference"))
            })
        }
        functionDeclarations.put(updateTone)

        // getWorldCupMatchesForDate
        val getWorldCupMatches = JSONObject().apply {
            put("name", "getWorldCupMatchesForDate")
            put("description", "Retrieves the scheduled FIFA World Cup 2026 matches for a specific date.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("dateString", JSONObject().apply {
                        put("type", "string")
                        put("description", "The date to query in yyyy-MM-dd format (e.g. 2026-06-12)")
                    })
                })
                put("required", JSONArray().put("dateString"))
            })
        }
        functionDeclarations.put(getWorldCupMatches)

        toolObject.put("functionDeclarations", functionDeclarations)
        toolsArray.put(toolObject)
        body.put("tools", toolsArray)

        return body
    }

    private suspend fun makePostRequest(url: String, jsonBody: String): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonBody.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val errBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                var errMsg = "HTTP ${response.code}"
                try {
                    val errJson = JSONObject(errBody)
                    val errorObj = errJson.optJSONObject("error")
                    if (errorObj != null) {
                        errMsg = errorObj.optString("message", errMsg)
                    }
                } catch (e: Exception) {
                    if (errBody.isNotBlank()) {
                        errMsg = errBody
                    }
                }
                throw Exception(errMsg)
            }
            errBody
        }
    }

    fun clearSession() {
        chatSession = null
    }
}
