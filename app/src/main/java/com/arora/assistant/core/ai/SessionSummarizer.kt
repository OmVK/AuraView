package com.arora.assistant.core.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SessionSummarizer {

    private const val SESSION_SYSTEM_PROMPT = """
You are an executive AI Study & Work Session Collator.
Given a chronological timeline of screen captures, apps used, and OCR texts from the user's session:
1. Provide an executive summary of what was accomplished.
2. List key topics, formulas, or code concepts studied.
3. Highlight important decisions, links, or action items for tomorrow.
4. Format cleanly in Markdown.
"""

    suspend fun summarizeSession(
        context: Context,
        client: GeminiClient,
        durationMillis: Long = 2 * 60 * 60 * 1000
    ): Result<String> = withContext(Dispatchers.IO) {
        val db = ScreenMemoryDatabase(context)
        val items = db.getRecentTimeline(durationMillis)

        if (items.isEmpty()) {
            return@withContext Result.success("No active screen memory recorded for this session yet.")
        }

        val timelineDump = buildString {
            append("Chronological Session Timeline (${items.size} screen snapshots):\n\n")
            items.forEachIndexed { idx, item ->
                append("[$idx] App: ${item.packageName} | Title: ${item.activityTitle}\n")
                val preview = item.ocrText.take(180).replace("\n", " ")
                append("Content snippet: $preview\n---\n")
            }
        }

        client.generateContent(
            prompt = timelineDump,
            systemInstruction = SESSION_SYSTEM_PROMPT
        )
    }
}
