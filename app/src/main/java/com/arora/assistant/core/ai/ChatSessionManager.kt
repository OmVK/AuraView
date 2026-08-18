package com.arora.assistant.core.ai

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChatMessage(
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatSessionManager(
    private val apiKey: String,
    private val defaultModel: String = "gemini-1.5-flash"
) {

    private val conversationHistory = mutableListOf<ChatMessage>()
    var contextInfo: String = ""

    fun clearHistory() {
        conversationHistory.clear()
    }

    fun getHistory(): List<ChatMessage> = conversationHistory.toList()

    companion object {
        fun sanitizeResponse(raw: String): String {
            if (raw.isBlank()) return ""

            var text = raw.trim()

            // 1. If output contains "Candidate:" quotation, extract the spoken text inside
            val candidateMatch = Regex("""(?i)(?:>\s*)?(?:\*\*)?Candidate:(?:\*\*)?\s*["“]?([^"”\n]+(?:\s+[^"”\n]+)*)["”]?""").find(text)
            if (candidateMatch != null) {
                val spoken = candidateMatch.groupValues[1].trim()
                if (spoken.length > 25) {
                    return spoken.removeSurrounding("\"").removeSurrounding("“", "”")
                }
            }

            // 2. If output contains "Draft 2" extract that draft
            val draft2Pattern = Regex("""(?i)\*?\s*\*?Draft\s*2\s*(\([^)]*\))?:?\*?\s*(.*)""", RegexOption.DOT_MATCHES_ALL)
            val draft2Match = draft2Pattern.find(text)
            if (draft2Match != null) {
                val extracted = draft2Match.groupValues[2].trim()
                if (extracted.isNotBlank()) {
                    return cleanLineArtifacts(extracted)
                }
            }

            // 3. Drop all meta-commentary paragraphs (e.g. "Since the example...", "Depending on your actual goals...", "Here are three ways...")
            val paragraphs = text.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
            val cleanParagraphs = mutableListOf<String>()

            for (p in paragraphs) {
                val lower = p.lowercase()
                val isMeta = lower.startsWith("since the example") ||
                    lower.startsWith("depending on your") ||
                    lower.startsWith("here are") ||
                    lower.startsWith("option 1") ||
                    lower.startsWith("### option") ||
                    lower.startsWith("## option") ||
                    lower.startsWith("*   question") ||
                    lower.startsWith("* question") ||
                    lower.startsWith("*   constraint") ||
                    lower.startsWith("* constraint") ||
                    lower.startsWith("*   step") ||
                    lower.startsWith("the interviewer wants")

                if (!isMeta) {
                    // Clean line-level bullet markers
                    val cleanedP = cleanLineArtifacts(p)
                    if (cleanedP.isNotBlank() && cleanedP.length > 20) {
                        cleanParagraphs.add(cleanedP)
                    }
                }
            }

            if (cleanParagraphs.isNotEmpty()) {
                return cleanParagraphs.joinToString("\n\n").trim()
            }

            return cleanLineArtifacts(text)
        }

        private fun cleanLineArtifacts(str: String): String {
            return str.lines()
                .filterNot { 
                    val t = it.trim().lowercase()
                    t.startsWith("*   *step") || 
                    t.startsWith("*   step") || 
                    t.startsWith("* step") || 
                    t.startsWith("*   tip") ||
                    t.startsWith("### option") ||
                    t.startsWith("## option") ||
                    t.startsWith("> **candidate:**") ||
                    t.startsWith("**candidate:**")
                }
                .joinToString("\n")
                .removePrefix(">")
                .removeSurrounding("\"")
                .removeSurrounding("“", "”")
                .trim()
        }
    }

    suspend fun sendMessage(
        userMessage: String,
        bitmap: Bitmap? = null,
        forceProModel: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val client = GeminiClient(
            apiKey = apiKey,
            model = if (forceProModel) "gemini-1.5-pro" else defaultModel
        )

        val cleanQuestion = userMessage.trim()

        val systemInstruction = buildString {
            append("You are the candidate sitting in a live job interview or viva exam. ")
            append("Always answer the question directly, professionally, and naturally in 1-2 spoken paragraphs in the first person ('I'). ")
            append("Never output bulleted thinking steps, options, tips, coaching advice, markdown headers, or meta commentary. ")
            append("Provide only the exact spoken answer you would say out loud right now.")
            if (contextInfo.isNotBlank()) {
                append("\nTarget Company / Role / Subject Context: ${contextInfo.trim()}")
            }
        }

        // Add current user question to history
        val currentMessages = conversationHistory.toMutableList()
        currentMessages.add(ChatMessage("user", cleanQuestion))

        val result = client.generateChat(
            messages = currentMessages,
            systemInstruction = systemInstruction,
            maxTokens = 800,
            temperature = 0.3
        )

        if (result.isSuccess) {
            val rawAnswer = result.getOrNull() ?: ""
            val cleanAnswer = sanitizeResponse(rawAnswer)
            conversationHistory.add(ChatMessage("user", cleanQuestion))
            conversationHistory.add(ChatMessage("model", cleanAnswer))
            Result.success(cleanAnswer)
        } else {
            result
        }
    }

    suspend fun sendMessageStream(
        userMessage: String,
        bitmap: Bitmap? = null,
        forceProModel: Boolean = false,
        onChunk: suspend (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val client = GeminiClient(
            apiKey = apiKey,
            model = if (forceProModel) "gemini-1.5-pro" else defaultModel
        )

        val cleanQuestion = userMessage.trim()

        val systemInstruction = buildString {
            append("You are the candidate sitting in a live job interview or viva exam. ")
            append("Always answer the question directly, professionally, and naturally in 1-2 spoken paragraphs in the first person ('I'). ")
            append("Never output bulleted thinking steps, options, tips, coaching advice, markdown headers, or meta commentary. ")
            append("Provide only the exact spoken answer you would say out loud right now.")
            if (contextInfo.isNotBlank()) {
                append("\nTarget Company / Role / Subject Context: ${contextInfo.trim()}")
            }
        }

        val currentMessages = conversationHistory.toMutableList()
        currentMessages.add(ChatMessage("user", cleanQuestion))

        val result = client.streamGenerateChat(
            messages = currentMessages,
            systemInstruction = systemInstruction,
            maxTokens = 800,
            temperature = 0.3,
            onChunk = { partial ->
                val clean = sanitizeResponse(partial)
                onChunk(clean)
            }
        )

        if (result.isSuccess) {
            val cleanAnswer = sanitizeResponse(result.getOrNull() ?: "")
            conversationHistory.add(ChatMessage("user", cleanQuestion))
            conversationHistory.add(ChatMessage("model", cleanAnswer))
            Result.success(cleanAnswer)
        } else {
            result
        }
    }
}
