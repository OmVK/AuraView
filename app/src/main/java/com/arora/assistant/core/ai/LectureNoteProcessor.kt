package com.arora.assistant.core.ai

import android.graphics.Bitmap

object LectureNoteProcessor {

    const val ANKI_SYSTEM_PROMPT = """You are an expert AI Study Deck & Notes Creator.
Convert the provided screen content or lecture frame into structured study notes and flashcards.

Output format:
## Topic: [detected topic]
### Key Concepts
- [concept 1]
- [concept 2]

### Anki Flashcards
Q: [question 1]
A: [answer 1]

Q: [question 2]
A: [answer 2]

Q: [question 3]
A: [answer 3]

RULES:
- Generate minimum 3 flashcard pairs.
- Keep answers under 20 words.
- Format cleanly in Markdown."""

    suspend fun processLectureFrame(
        client: GeminiClient,
        bitmap: Bitmap?,
        transcriptChunk: String?
    ): Result<String> {
        val prompt = buildString {
            append("Convert this screen content into structured study notes and Anki flashcards:\n\n")
            if (!transcriptChunk.isNullOrEmpty()) {
                append("Content: ").append(transcriptChunk)
            } else {
                append("Content: [See attached image]")
            }
        }
        return client.generateContent(
            prompt = prompt,
            bitmap = bitmap,
            systemInstruction = ANKI_SYSTEM_PROMPT
        )
    }
}
