package com.arora.assistant.core.ai

import android.graphics.Bitmap

object ScreenDiffDetector {

    private const val DIFF_SYSTEM_PROMPT = """
You are an expert AI Visual & Textual Diff Engine.
Compare Screen A (Before) and Screen B (After):
1. Identify exact differences (price changes, edited paragraphs, added error messages, new UI elements).
2. Rate the significance of change (Major / Minor / Unchanged).
3. If price tracking: indicate price increase/decrease in $ or %.
4. Format in clean bulleted Markdown.
"""

    suspend fun compareScreens(
        geminiClient: GeminiClient? = null,
        groqClient: GroqClient? = null,
        textA: String,
        textB: String,
        bitmapB: Bitmap? = null
    ): Result<String> {
        val prompt = """
Compare these two screen states:

=== SCREEN A (BEFORE) ===
$textA

=== SCREEN B (AFTER) ===
$textB
"""
        if (groqClient != null) {
            val res = groqClient.generateContent(
                prompt = prompt,
                bitmap = bitmapB,
                systemInstruction = DIFF_SYSTEM_PROMPT
            )
            if (res.isSuccess && !res.getOrNull().isNullOrBlank()) {
                return res
            }
        }

        if (geminiClient != null) {
            return geminiClient.generateContent(
                prompt = prompt,
                bitmap = bitmapB,
                systemInstruction = DIFF_SYSTEM_PROMPT
            )
        }

        return Result.failure(Exception("No AI client configured for screen diff"))
    }
}
