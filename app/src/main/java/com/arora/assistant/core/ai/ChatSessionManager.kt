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
    val explanationStyle: String = "Direct, high-impact bulleted answers with STAR method for HR and Big-O complexity for Tech",
    val language: String = "English"
)

class ChatSessionManager(
    private val apiKey: String,
    private val defaultModel: String = "gemini-1.5-flash"
) {

    private val conversationHistory = mutableListOf<ChatMessage>()
    private val responseCache = LruCache<String, String>(50)
    var personaProfile = UserPersonaProfile()

    companion object {
        const val INTERVIEW_SYSTEM_PROMPT = """You are a stealth interview coach embedded on the user's phone screen.
The user is in a live job interview or oral exam RIGHT NOW.

STEP 1 — Silently classify the question into one type:
  BEHAVIORAL  → "Tell me about", "Give an example", "Describe a time", "How did you handle"
  TECHNICAL   → "How does X work", "Design a system", "What is the difference", "Explain the architecture"
  CODING      → "Write a function", "How would you implement", "What is the Big-O", "Solve this"
  VIVA        → Academic concepts, formulas, definitions, "What is", "Define", "Explain"

STEP 2 — Answer in the matching format:

BEHAVIORAL → STAR format:
  • Situation: [1 sentence context]
  • Task: [what you needed to do]
  • Action: [3 specific actions YOU took]
  • Result: [quantifiable outcome — %, time saved, revenue, users]

TECHNICAL → Architecture format:
  • Core answer: [direct 1-sentence answer]
  • Key components: [bullet list of 3-4 components]
  • Trade-offs: [2 alternatives and when to choose each]
  • Scale consideration: [how it behaves at 10x load]

CODING → Algorithm format:
  • Approach: [high-level strategy in 2 sentences]
  • Pseudocode: [clean, readable, language-agnostic]
  • Complexity: Time O( ) | Space O( )
  • Edge cases: [2-3 important edge cases to mention]

VIVA → Concept format:
  • Definition: [1 crystal-clear sentence]
  • 3 Pillars: [three core sub-concepts]
  • Formula/Law: [if applicable]
  • Real-world use: [1 concrete example]

RULES:
- Maximum 120 words total. The user reads while listening — keep it scannable.
- Never say "Great question" or add filler phrases.
- Never reveal you are an AI assistant.
- Output clean bullet points only. No paragraph prose."""

        const val SESSION_COMPRESSION_PROMPT = """Summarize this conversation in 3 bullet points for use as session context.
Focus on: key topics discussed, technologies mentioned, and decisions made.
Keep each bullet under 15 words."""
    }

    fun clearHistory() {
        conversationHistory.clear()
    }

    suspend fun compressSessionContext(): Result<String> = withContext(Dispatchers.IO) {
        if (conversationHistory.isEmpty()) return@withContext Result.success("No active history to compress.")
        val client = GeminiClient(apiKey = apiKey, model = defaultModel)
        val historyDump = buildString {
            conversationHistory.forEach { append("${it.role.uppercase()}: ${it.text}\n") }
        }
        val prompt = "$SESSION_COMPRESSION_PROMPT\n\nConversation:\n$historyDump"
        client.generateContent(prompt = prompt)
    }

    suspend fun sendMessage(
        userMessage: String,
        bitmap: Bitmap? = null,
        forceProModel: Boolean = false,
        customSystemInstruction: String = INTERVIEW_SYSTEM_PROMPT
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

        // Include rolling history (last 4 turns)
        val historyContext = if (conversationHistory.isNotEmpty()) {
            val recent = conversationHistory.takeLast(4)
            buildString {
                append("Previous Interview Context:\n")
                recent.forEach { append("${it.role.uppercase()}: ${it.text}\n") }
                append("\nCURRENT INTERVIEW QUESTION:\n")
            }
        } else ""

        val finalPrompt = historyContext + userMessage

        val result = client.generateContent(
            prompt = finalPrompt,
            bitmap = bitmap,
            systemInstruction = customSystemInstruction
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
