package com.arora.assistant.core.ai

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChatMessage(
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class UserPersonaProfile(
    val fieldOfStudy: String = "Computer Science & Engineering",
    val preferredNotation: String = "LaTeX ($$ ... $$)",
    val explanationStyle: String = "Intuitive with step-by-step proofs",
    val language: String = "English"
)

class ChatSessionManager(
    private val apiKey: String,
    private val defaultModel: String = "gemini-1.5-flash"
) {

    private val conversationHistory = mutableListOf<ChatMessage>()
    private val responseCache = LruCache<String, String>(50)
    var personaProfile = UserPersonaProfile()

    fun clearHistory() {
        conversationHistory.clear()
    }

    suspend fun sendMessage(
        userMessage: String,
        bitmap: Bitmap? = null,
        forceProModel: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val cacheKey = userMessage.trim().lowercase()
        if (bitmap == null && responseCache.get(cacheKey) != null) {
            val cached = responseCache.get(cacheKey)
            conversationHistory.add(ChatMessage("user", userMessage))
            conversationHistory.add(ChatMessage("model", cached))
            return@withContext Result.success(cached)
        }

        val targetModel = if (forceProModel) "gemini-1.5-pro" else defaultModel
        val client = GeminiClient(apiKey = apiKey, model = targetModel)

        val systemInstruction = """
You are AuraView, an elite AI Copilot & Personal Second Brain.
User Persona Context:
- Field of Study: ${personaProfile.fieldOfStudy}
- Preferred Notation: ${personaProfile.preferredNotation}
- Style: ${personaProfile.explanationStyle}
- Preferred Language: ${personaProfile.language}

Always deliver precise, structured, and insightful answers.
"""

        // Include rolling history (last 6 turns)
        val historyContext = if (conversationHistory.isNotEmpty()) {
            val recent = conversationHistory.takeLast(6)
            buildString {
                append("Previous Conversation Context:\n")
                recent.forEach { append("${it.role.uppercase()}: ${it.text}\n") }
                append("\nCurrent Query:\n")
            }
        } else ""

        val finalPrompt = historyContext + userMessage

        val result = client.generateContent(
            prompt = finalPrompt,
            bitmap = bitmap,
            systemInstruction = systemInstruction
        )

        result.onSuccess { answer ->
            conversationHistory.add(ChatMessage("user", userMessage))
            conversationHistory.add(ChatMessage("model", answer))
            if (bitmap == null) {
                responseCache.put(cacheKey, answer)
            }
        }

        result
    }
}
