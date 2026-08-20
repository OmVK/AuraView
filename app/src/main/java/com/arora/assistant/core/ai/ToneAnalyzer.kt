package com.arora.assistant.core.ai

object ToneAnalyzer {

    const val TONE_SYSTEM_PROMPT = """You are an expert Social & Communication Tone Analyzer.
Analyze the tone of the provided message.

Output format:
## Tone
[Primary tone: Aggressive / Passive-Aggressive / Sarcastic / Neutral / Friendly / Formal]

## Subtext
[What the sender really means in 1 sentence]

## Reply Strategies
1. **De-escalate:** [suggested reply]
2. **Assert boundary:** [suggested reply]
3. **Neutral/Professional:** [suggested reply]"""

    suspend fun analyzeMessageTone(
        geminiClient: GeminiClient? = null,
        groqClient: GroqClient? = null,
        messageText: String
    ): Result<String> {
        val prompt = "Analyze the tone of this message:\n\"$messageText\""

        if (groqClient != null) {
            val res = groqClient.generateContent(
                prompt = prompt,
                systemInstruction = TONE_SYSTEM_PROMPT
            )
            if (res.isSuccess && !res.getOrNull().isNullOrBlank()) {
                return res
            }
        }

        if (geminiClient != null) {
            return geminiClient.generateContent(
                prompt = prompt,
                systemInstruction = TONE_SYSTEM_PROMPT
            )
        }

        return Result.failure(Exception("No AI client configured for tone analysis"))
    }
}
