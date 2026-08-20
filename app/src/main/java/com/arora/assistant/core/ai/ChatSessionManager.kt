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
    private val groqApiKey: String = "",
    private val activeEngine: String = "groq",
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

            // 0. Strip common AI Assistant disclaimers & conversational preambles
            text = text.replace(Regex("""(?i)^as an ai assistant,?\s*"""), "")
            text = text.replace(Regex("""(?i)^as a language model,?\s*"""), "")
            text = text.replace(Regex("""(?i)^as an ai,?\s*"""), "")
            text = text.replace(Regex("""(?i)^(?:here(?:'s| is) (?:a |the )?(?:sample |draft |spoken |suggested )?(?:response|answer|pitch|way to answer|what you can say):?\s*)"""), "")
            text = text.replace(Regex("""(?i)^(?:certainly|sure|absolutely)!(?:\s*here is[^:\n]+:?)?\s*"""), "")
            text = text.replace(Regex("""(?i)^\*\*(?:response|answer|spoken answer|candidate|draft|draft 1|pitch):\*\*\s*"""), "")

            // 1. If output contains "Candidate:" quotation, extract the spoken text inside
            val candidateMatch = Regex("""(?i)(?:>\s*)?(?:\*\*)?Candidate:(?:\*\*)?\s*["“]?([^"”\n]+(?:\s+[^"”\n]+)*)["”]?""").find(text)
            if (candidateMatch != null) {
                val spoken = candidateMatch.groupValues[1].trim()
                if (spoken.length > 25) {
                    return cleanLineArtifacts(spoken.removeSurrounding("\"").removeSurrounding("“", "”"))
                }
            }

            // 2. If output contains multiple drafts / options, take only the FIRST draft before the next option
            val optionSplit = text.split(Regex("""(?i)\n\s*(?:###?\s*)?(?:draft\s*[2-9]|option\s*[2-9]|alternative\s*[2-9]|approach\s*[2-9]|why this works:|key points:|tips?:)"""))
            if (optionSplit.isNotEmpty()) {
                text = optionSplit[0].trim()
            }

            // 3. Drop all meta-commentary paragraphs (e.g. "Since the example...", "Depending on your actual goals...", "Here are three ways...")
            val paragraphs = text.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
            val cleanParagraphs = mutableListOf<String>()

            for (p in paragraphs) {
                val lower = p.lowercase()
                val isMeta = lower.startsWith("since the example") ||
                    lower.startsWith("depending on your") ||
                    lower.startsWith("here are") ||
                    lower.startsWith("here is") ||
                    lower.startsWith("option 1") ||
                    lower.startsWith("draft 1") ||
                    lower.startsWith("### option") ||
                    lower.startsWith("## option") ||
                    lower.startsWith("*   question") ||
                    lower.startsWith("* question") ||
                    lower.startsWith("*   constraint") ||
                    lower.startsWith("* constraint") ||
                    lower.startsWith("*   step") ||
                    lower.startsWith("the interviewer wants") ||
                    lower.startsWith("key points") ||
                    lower.startsWith("why this works") ||
                    lower.startsWith("tip:") ||
                    lower.startsWith("notes:")

                if (!isMeta) {
                    val cleanedP = cleanLineArtifacts(p)
                    if (cleanedP.isNotBlank() && cleanedP.length > 15) {
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
                .filterNot { line ->
                    val t = line.trim().lowercase()
                    t.startsWith("option 1") ||
                    t.startsWith("option 2") ||
                    t.startsWith("option 3") ||
                    t.startsWith("draft 1") ||
                    t.startsWith("draft 2") ||
                    t.startsWith("draft 3") ||
                    t.startsWith("*   option") ||
                    t.startsWith("* option") ||
                    t.startsWith("- option") ||
                    t.startsWith("here are a few") ||
                    t.startsWith("here are 3") ||
                    t.startsWith("here are three") ||
                    t.startsWith("here is a") ||
                    t.startsWith("here's a") ||
                    t.startsWith("tip:") ||
                    t.startsWith("* tip") ||
                    t.startsWith("*   tip") ||
                    t.startsWith("### option") ||
                    t.startsWith("## option") ||
                    t.startsWith("> **candidate:**") ||
                    t.startsWith("**candidate:**") ||
                    t.startsWith("**response:**") ||
                    t.startsWith("**answer:**")
                }
                .joinToString("\n")
                .removePrefix(">")
                .removeSurrounding("\"")
                .removeSurrounding("“", "”")
                .trim()
        }
    }

    private fun buildCandidateSystemInstruction(): String {
        return buildString {
            append("You are the job applicant / candidate in a live interview.\n")
            if (contextInfo.isNotBlank()) {
                append("CRITICAL REQUIREMENT: You are interviewing specifically for: \"${contextInfo.trim()}\". Tailor all your technical answers, past achievements, skills, and industry terminology directly to this company and role.\n")
            }
            append("STRICT INSTRUCTIONS:\n")
            append("1. Answer in the first person ('I', 'me', 'my') as the candidate speaking out loud directly to the interviewer.\n")
            append("2. Output ONLY the single exact spoken draft response (1-2 spoken paragraphs, under 100 words). Nothing else.\n")
            append("3. FORBIDDEN: Do NOT write introductions ('Here is a response:', 'Certainly!'), do NOT write multiple options or drafts (Draft 1, Option A), do NOT write bullet points, explanations, tips, advice, or AI disclaimers.\n")
            append("4. Give a natural, confident, concise answer ready to be read out loud immediately.\n")
        }
    }

    suspend fun sendMessage(
        userMessage: String,
        bitmap: Bitmap? = null,
        forceProModel: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanQuestion = userMessage.trim()
        val systemInstruction = buildCandidateSystemInstruction()

        val promptWithContext = if (contextInfo.isNotBlank()) {
            "[Target Role & Company: ${contextInfo.trim()}]\n\nQuestion: $cleanQuestion"
        } else {
            cleanQuestion
        }

        // Add current user question to history
        val currentMessages = conversationHistory.toMutableList()
        currentMessages.add(ChatMessage("user", promptWithContext))

        // Route strictly to the activated engine
        if (activeEngine == "groq") {
            if (groqApiKey.isBlank()) {
                return@withContext Result.failure(Exception("Groq is activated as active AI engine. Please enter your Groq API Key in Settings."))
            }
            val groqClient = GroqClient(groqApiKey)
            val groqRes = groqClient.generateChat(
                messages = currentMessages,
                systemInstruction = systemInstruction,
                maxTokens = 800,
                temperature = 0.3
            )
            if (groqRes.isSuccess && groqRes.getOrNull()?.isNotBlank() == true) {
                val cleanAnswer = sanitizeResponse(groqRes.getOrNull()!!)
                conversationHistory.add(ChatMessage("user", cleanQuestion))
                conversationHistory.add(ChatMessage("model", cleanAnswer))
                return@withContext Result.success(cleanAnswer)
            }
            return@withContext groqRes
        }

        // Gemini Route
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini is activated as active AI engine. Please enter your Gemini API Key in Settings."))
        }

        val client = GeminiClient(
            apiKey = apiKey,
            model = if (forceProModel) "gemini-1.5-pro" else defaultModel
        )

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
        val cleanQuestion = userMessage.trim()
        val systemInstruction = buildCandidateSystemInstruction()

        val promptWithContext = if (contextInfo.isNotBlank()) {
            "[Target Role & Company: ${contextInfo.trim()}]\n\nQuestion: $cleanQuestion"
        } else {
            cleanQuestion
        }

        val currentMessages = conversationHistory.toMutableList()
        currentMessages.add(ChatMessage("user", promptWithContext))

        // Route strictly to the activated engine
        if (activeEngine == "groq") {
            if (groqApiKey.isBlank()) {
                return@withContext Result.failure(Exception("Groq is activated as active AI engine. Please enter your Groq API Key in Settings."))
            }
            val groqClient = GroqClient(groqApiKey)
            val groqRes = groqClient.streamGenerateChat(
                messages = currentMessages,
                systemInstruction = systemInstruction,
                maxTokens = 800,
                temperature = 0.3,
                onChunk = { partial ->
                    val clean = sanitizeResponse(partial)
                    onChunk(clean)
                }
            )
            if (groqRes.isSuccess && groqRes.getOrNull()?.isNotBlank() == true) {
                val cleanAnswer = sanitizeResponse(groqRes.getOrNull()!!)
                conversationHistory.add(ChatMessage("user", cleanQuestion))
                conversationHistory.add(ChatMessage("model", cleanAnswer))
                return@withContext Result.success(cleanAnswer)
            }
            return@withContext groqRes
        }

        // Gemini Route
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini is activated as active AI engine. Please enter your Gemini API Key in Settings."))
        }

        val client = GeminiClient(
            apiKey = apiKey,
            model = if (forceProModel) "gemini-1.5-pro" else defaultModel
        )

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
