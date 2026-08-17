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
        client: GeminiClient,
        messageText: String
    ): Result<String> {
        val prompt = "Analyze the tone of this message:\n\"$messageText\""
        return client.generateContent(
            prompt = prompt,
            systemInstruction = TONE_SYSTEM_PROMPT
        )
    }
}
