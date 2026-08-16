package com.arora.assistant.core.ai

object ToneAnalyzer {

    private const val TONE_SYSTEM_PROMPT = """
You are an expert Social & Communication Tone Analyzer.
Analyze the provided message:
1. Tone Breakdown: (e.g., Sarcastic, Passive-Aggressive, Warm, Professional, Defensive).
2. Subtext & Hidden Intent: What the sender is really trying to say.
3. 3 Smart Reply Strategies:
   - Strategy A: De-escalating & Friendly
   - Strategy B: Direct & Professional
   - Strategy C: Witty & Assertive
Format in clean Markdown with clear headings.
"""

    suspend fun analyzeMessageTone(
        client: GeminiClient,
        messageText: String
    ): Result<String> {
        val prompt = "Analyze the tone and suggest reply strategies for this message:\n\n\"$messageText\""
        return client.generateContent(
            prompt = prompt,
            systemInstruction = TONE_SYSTEM_PROMPT
        )
    }
}
