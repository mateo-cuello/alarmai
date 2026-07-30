package com.alarmai.app.data.repository

import com.alarmai.app.data.local.PreferencesManager
import android.util.Log
import com.alarmai.app.data.model.GeminiModels
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request

class GeminiAgentManager @JvmOverloads constructor(
    private val prefs: PreferencesManager,
    /**
     * Test seam for the HTTP call: `(url, apiKey, jsonBody) -> responseJson`.
     * Null in production, where [makePostRequest] is used.
     */
    private val httpPost: (suspend (String, String, String) -> String)? = null,
    ) {

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
        var modelName: String,
        val systemInstructionText: String,
        val history: MutableList<Content> = mutableListOf()
    )

    private var chatSession: CustomChatSession? = null

    companion object {
        /** Shared across all calls: a per-request client leaks its pool/dispatcher threads. */
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Errors worth retrying on the next model in the chain. 404/NOT_FOUND is included so an
         * unrecognised model id degrades gracefully instead of hard-failing the morning briefing.
         */
        private fun isFallbackWorthy(message: String): Boolean =
            message.contains("429") ||
                message.contains("503") ||
                message.contains("404") ||
                message.contains("quota", ignoreCase = true) ||
                message.contains("exhausted", ignoreCase = true) ||
                message.contains("not found", ignoreCase = true) ||
                message.contains("NOT_FOUND")
    }

    
    private fun getSystemInstructionText(language: String, tone: String): String {
        val now = java.util.Date()
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
                - Estilo y tono de comunicación preferido: $tone
                
                HERRAMIENTAS DISPONIBLES:
                - Tienes la herramienta de búsqueda de Google (googleSearch) para buscar información si el usuario te hace una pregunta de seguimiento.
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
                - Preferred communication style/tone: $tone
                
                AVAILABLE TOOLS:
                - You have the Google Search tool (googleSearch) to search for information if the user asks a follow-up question.
            """.trimIndent()
        }
    }

    /** The briefing text plus the exact prompt that produced it. */
    data class BriefingResult(val prompt: String, val text: String)

    suspend fun startSession(
        apiKey: String,
        weatherData: String,
        newsData: String,
        calendarData: String,
        modelName: String = GeminiModels.DEFAULT
    ): String = startSessionDetailed(apiKey, weatherData, newsData, calendarData, modelName).text

    /**
     * Same as [startSession] but also returns the prompt that was actually sent, so callers who
     * cache the briefing (PrefetchWorker) can replay a history that matches what generated it.
     */
    suspend fun startSessionDetailed(
        apiKey: String,
        weatherData: String,
        newsData: String,
        calendarData: String,
        modelName: String = GeminiModels.DEFAULT
    ): BriefingResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            val language = prefs.getLanguage()
            val demo = if (language == "es") {
                "¡Buenos días! Esta es una simulación de tu alarma de IA. Como tu clave de API de Gemini no está configurada, estoy funcionando en modo de simulación local. El clima de hoy se ve grandioso, tu agenda está libre y la computación cuántica es noticia de portada. ¿Cómo te sientes hoy?"
            } else {
                "Good morning! This is a demo of your AI alarm. Since your Gemini API key is not configured in settings, I am running in local simulation mode. Today's weather looks great, your calendar is clear, and quantum computing made headlines. How are you feeling today?"
            }
            return@withContext BriefingResult(prompt = "", text = demo)
        }

        var promptSent = ""
        try {
            val language = prefs.getLanguage()
            val tone = prefs.getTonePreference()

            val systemInstructionText = getSystemInstructionText(language, tone)

            val initialPrompt = if (language == "es") {
                """
                    Comienza el resumen matutino.
                    - Clima: $weatherData
                    - Eventos de Calendario: $calendarData
                    - Noticias destacadas: $newsData
                    
                    Por favor, saluda al usuario cálidamente, menciona la hora (o deséale un buen día), resume el clima, calendario, y los titulares de noticias provistos de manera muy atractiva y concisa, y pregúntale cómo le gustaría empezar el día. IMPORTANTE: No des un mensaje genérico de introducción antes de las noticias ni inventes datos. Da las noticias reales obtenidas de la lista directamente.
                """.trimIndent()
            } else {
                """
                    Start the morning briefing.
                    - Weather: $weatherData
                    - Calendar Events: $calendarData
                    - Top News Headlines: $newsData
                    
                    Please greet the user warmly, state the time (or wish them a good morning), summarize the weather, calendar, and the provided news headlines in a highly engaging, concise way, and ask how they'd like to start their day. IMPORTANT: Do not give a generic introductory message before the news or invent facts. Deliver the real news obtained from the list directly.
                """.trimIndent()
            }

            promptSent = initialPrompt

            val session = CustomChatSession(
                apiKey = apiKey,
                modelName = modelName,
                systemInstructionText = systemInstructionText
            )
            chatSession = session

            BriefingResult(prompt = initialPrompt, text = sendMessageInternal(session, initialPrompt))
        } catch (e: Exception) {
            val language = prefs.getLanguage()
            val message = if (language == "es") {
                "¡Buenos días! Encontré un error al configurar la sesión de IA: ${e.localizedMessage}. ¿Cómo puedo ayudarte manualmente?"
            } else {
                "Good morning! I encountered an error setting up the AI session: ${e.localizedMessage}. How can I assist you manually?"
            }
            BriefingResult(prompt = promptSent, text = message)
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
                    userInput.contains("calendario", ignoreCase = true) || userInput.contains("agenda", ignoreCase = true) -> "No tienes eventos próximos en tu calendario."
                    else -> "¡Te escucho! Estoy funcionando en modo demo, pero una vez que configures tu clave API de Gemini en los ajustes, podremos tener una conversación completa."
                }
            } else {
                return@withContext when {
                    userInput.contains("weather", ignoreCase = true) -> "Today's forecast is sunny and pleasant, about twenty-two degrees Celsius."
                    userInput.contains("news", ignoreCase = true) -> "In technology news, researchers have made breakthrough progress in solar panel efficiency."
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

        val responseContent = try {
            callGeminiAndHandleFunctions(session)
        } catch (e: Exception) {
            // Roll the user turn back out of history. Leaving it stranded meant every later
            // retry() re-sent it, compounding the failure instead of recovering from it.
            session.history.removeAt(session.history.lastIndex)
            throw e
        }

        val textResponse = responseContent.parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }
        return textResponse.ifBlank {
            if (prefs.getLanguage() == "es") {
                "Escuché tu mensaje, pero no pude generar una respuesta. ¿Puedes repetirlo?"
            } else {
                "I heard you, but I couldn't generate a reply. Can you repeat that?"
            }
        }
    }

    /**
     * Builds the model order for a request: the configured model first, then every other known
     * model as a fallback. Using the configured model as the head (rather than looking up its
     * index) means a custom or newer id is still honoured instead of being silently replaced.
     */
    private fun modelChainFor(modelName: String): List<String> =
        listOf(modelName) + GeminiModels.CHAIN.filter { it != modelName }

    private suspend fun callGeminiAndHandleFunctions(session: CustomChatSession): Content {
        var lastException: Exception? = null

        for (currentModel in modelChainFor(session.modelName)) {
            try {
                return requestWithModel(session, currentModel)
            } catch (e: Exception) {
                lastException = e
                if (isFallbackWorthy(e.message ?: "")) {
                    // Try the next model. Note session.modelName is deliberately NOT reassigned:
                    // a transient 503 should not permanently downgrade the rest of the conversation.
                    continue
                }
                throw e
            }
        }

        throw Exception("All Gemini models failed. Last error: ${lastException?.message}")
    }

    private suspend fun requestWithModel(session: CustomChatSession, model: String): Content {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val requestBody = buildRequestBody(session.history, session.systemInstructionText)

        val responseJson = (httpPost ?: ::makePostRequest)(url, session.apiKey, requestBody.toString())
        val responseContent = parseResponseContent(responseJson) ?: throw Exception("Failed to parse response")

        // Only commit turns that carry usable text. `googleSearch` is server-side grounding, so a
        // client-visible functionCall part is a model quirk we have no handler for — appending it
        // would leave an unanswered call in history, and every later request would then be
        // rejected with 400 INVALID_ARGUMENT, dead-ending the session with no way to recover.
        val hasText = responseContent.parts.any { it is TextPart && it.text.isNotBlank() }
        if (hasText) {
            val textOnly = responseContent.copy(
                parts = responseContent.parts.filterIsInstance<TextPart>()
            )
            session.history.add(textOnly)
            return textOnly
        }

        Log.w("GeminiAgentManager", "Response had no usable text; not committing it to history")
        return responseContent
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
            // Skip unknown part types (e.g., googleSearch grounding results) gracefully
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
        
        val googleSearchObject = JSONObject().apply {
            put("googleSearch", JSONObject())
        }
        toolsArray.put(googleSearchObject)

        body.put("tools", toolsArray)

        return body
    }

    private suspend fun makePostRequest(url: String, apiKey: String, jsonBody: String): String = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonBody.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            // Header rather than a ?key= query param: the URL appears in OkHttp exception
            // messages, which get logged and surfaced in the on-screen error text.
            .addHeader("x-goog-api-key", apiKey)
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val errBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val httpCode = response.code
                var errMsg = "HTTP $httpCode"
                try {
                    val errJson = JSONObject(errBody)
                    val errorObj = errJson.optJSONObject("error")
                    if (errorObj != null) {
                        errMsg = "HTTP $httpCode: " + errorObj.optString("message", errMsg)
                    }
                } catch (e: Exception) {
                    if (errBody.isNotBlank()) {
                        errMsg = "HTTP $httpCode: $errBody"
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
