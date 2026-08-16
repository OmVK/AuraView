package com.arora.assistant.core.ai

import android.graphics.Bitmap

object InPlaceTranslator {

    private const val TRANSLATE_SYSTEM_INSTRUCTION = """
You are an expert real-time in-place document translator.
Translate the snipped text into English (or user target language).
Preserve technical terms, formatting, and layout structure.
Provide:
1. Target translation.
2. Key vocabulary & grammar breakdown.
"""

    suspend fun translateContent(
        client: GeminiClient,
        text: String,
        targetLanguage: String = "English",
        bitmap: Bitmap? = null
    ): Result<String> {
        val prompt = "Translate the following content into $targetLanguage:\n\n$text"
        return client.generateContent(
            prompt = prompt,
            bitmap = bitmap,
            systemInstruction = TRANSLATE_SYSTEM_INSTRUCTION
        )
    }
}
