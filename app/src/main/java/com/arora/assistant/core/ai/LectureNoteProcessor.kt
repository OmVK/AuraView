package com.arora.assistant.core.ai

import android.graphics.Bitmap

object LectureNoteProcessor {

    private const val LECTURE_SYSTEM_INSTRUCTION = """
You are an expert AI Lecture Note Taker and Study Deck Creator.
From the captured lecture slide/video frame or transcribed transcript:
1. Generate structured Markdown study notes (Key concepts, formulas, definitions).
2. Generate 3-5 Anki-ready Q&A Flashcard pairs formatted as:
Q: [Question]
A: [Answer]
---
"""

    suspend fun processLectureFrame(
        client: GeminiClient,
        bitmap: Bitmap?,
        transcriptChunk: String?
    ): Result<String> {
        val prompt = buildString {
            append("Create comprehensive study notes and Anki flashcards for this lecture content.")
            if (!transcriptChunk.isNullOrEmpty()) {
                append("\n\nLecture Audio Transcript:\n").append(transcriptChunk)
            }
        }
        return client.generateContent(
            prompt = prompt,
            bitmap = bitmap,
            systemInstruction = LECTURE_SYSTEM_INSTRUCTION
        )
    }
}
