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
        geminiClient: GeminiClient? = null,
        groqClient: GroqClient? = null,
        text: String,
        targetLanguage: String = "English",
        bitmap: Bitmap? = null
    ): Result<String> {
        val prompt = "Translate the following content into $targetLanguage:\n\n$text"

        if (groqClient != null) {
            val res = groqClient.generateContent(
                prompt = prompt,
                bitmap = bitmap,
                systemInstruction = TRANSLATE_SYSTEM_INSTRUCTION
            )
            if (res.isSuccess && !res.getOrNull().isNullOrBlank()) {
                return res
            }
        }

        if (geminiClient != null) {
            return geminiClient.generateContent(
                prompt = prompt,
                bitmap = bitmap,
                systemInstruction = TRANSLATE_SYSTEM_INSTRUCTION
            )
        }

        return Result.failure(Exception("No AI client configured for translation"))
    }
}
