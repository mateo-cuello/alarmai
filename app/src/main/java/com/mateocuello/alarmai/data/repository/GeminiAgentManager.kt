package com.mateocuello.alarmai.data.repository

import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAgentManager {
    private var chatSession: Chat? = null

    suspend fun startSession(
        apiKey: String,
        weatherData: String,
        newsData: String,
        calendarData: String,
        modelName: String = "gemini-3.5-flash"
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Good morning! This is a demo of your AI alarm. Since your Gemini API key is not configured in settings, I am running in local simulation mode. Today's weather looks great, your calendar is clear, and quantum computing made headlines. How are you feeling today?"
        }

        try {
            val systemInstructionText = """
                You are a warm, helpful, and energetic morning AI assistant. 
                Your task is to wake the user up and converse with them.
                Since your responses will be read out loud via Text-to-Speech (TTS), you MUST:
                1. Keep all responses very short, clear, and easy to understand when spoken (under 120 words).
                2. Do not use markdown (no asterisks, bullet points, headers, or emojis). Speak in plain sentences.
                3. End your statements with a friendly, open-ended question to encourage the user to reply.
            """.trimIndent()

            val model = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                systemInstruction = content { text(systemInstructionText) }
            )

            val session = model.startChat()
            chatSession = session

            val initialPrompt = """
                Start the morning briefing. Here is the daily data:
                - Weather: $weatherData
                - News: $newsData
                - Calendar Events: $calendarData
                
                Please greet the user warmly, state the time (or wish them a good morning), summarize this data in a highly engaging, concise way, and ask how they'd like to start their day.
            """.trimIndent()

            val response = session.sendMessage(initialPrompt)
            response.text ?: "Good morning! I had trouble generating your briefing. What can I do for you today?"
        } catch (e: Exception) {
            "Good morning! I encountered an error setting up the AI session: ${e.localizedMessage}. How can I assist you manually?"
        }
    }

    suspend fun sendMessage(userInput: String): String = withContext(Dispatchers.IO) {
        val session = chatSession
        if (session == null) {
            // Demo mode fallback conversation
            return@withContext when {
                userInput.contains("weather", ignoreCase = true) -> "Today's forecast is sunny and pleasant, about twenty-two degrees Celsius."
                userInput.contains("news", ignoreCase = true) -> "In technology news, researchers have made breakthrough progress in solar panel efficiency."
                userInput.contains("calendar", ignoreCase = true) || userInput.contains("schedule", ignoreCase = true) -> "You have no upcoming events on your calendar."
                else -> "I hear you! I'm running in demo mode, but once you set your Gemini API key in settings, we can have a full open-ended conversation about anything."
            }
        }

        try {
            val response = session.sendMessage(userInput)
            response.text ?: "I heard you, but I couldn't generate a reply. Can you repeat that?"
        } catch (e: Exception) {
            "Sorry, I had trouble contacting the AI. Error: ${e.localizedMessage}"
        }
    }

    fun clearSession() {
        chatSession = null
    }
}
