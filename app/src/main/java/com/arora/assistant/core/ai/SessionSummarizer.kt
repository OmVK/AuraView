package com.arora.assistant.core.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SessionSummarizer {

    const val DAILY_SESSION_REPORT_PROMPT = """You are an executive Daily Study & Work Session Collator.
Generate a daily work/study session report from the provided timeline.

Output format:
## Session Report

### What I worked on
[2-3 sentence summary of the session's main focus]

### Key items encountered
[bullet list: equations solved, code written, documents read, decisions made]

### Knowledge gained
[2-3 bullet points of what was learned]

### Recommended next steps
[2 specific next actions based on session content]

### Time breakdown estimate
[rough % split across topics if multiple apps used]"""

    suspend fun summarizeSession(
        context: Context,
        client: GeminiClient,
        durationMillis: Long = 2 * 60 * 60 * 1000,
        userNotes: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val db = ScreenMemoryDatabase(context)
        val items = db.getRecentTimeline(durationMillis)

        if (items.isEmpty()) {
            return@withContext Result.success("No active screen memory recorded for this session yet.")
        }

        val durationHours = durationMillis / (1000 * 60 * 60)

        val timelineDump = buildString {
            append("Session duration: Approximately $durationHours hours\n")
            append("Screen timeline (apps and content visited):\n")
            items.forEachIndexed { idx, item ->
                val preview = item.ocrText.take(160).replace("\n", " ")
                append("- App: ${item.packageName} (${item.activityTitle}): $preview\n")
            }
            if (!userNotes.isNullOrBlank()) {
                append("\nUser notes:\n$userNotes\n")
            }
        }

        val prompt = "$DAILY_SESSION_REPORT_PROMPT\n\n$timelineDump"

        client.generateContent(
            prompt = prompt
        )
    }
}
