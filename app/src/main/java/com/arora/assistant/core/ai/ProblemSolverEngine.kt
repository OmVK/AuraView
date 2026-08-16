package com.arora.assistant.core.ai

import android.graphics.Bitmap

object ProblemSolverEngine {

    private const val SOLVER_SYSTEM_INSTRUCTION = """
You are an expert AI Study Copilot for students.
When given an image or text containing a math problem, physics question, science problem, or code snippet:
1. Identify the core question and topic.
2. Provide a clear, step-by-step reasoning breakdown.
3. Write formulas using clean LaTeX syntax (e.g. $$ formula $$).
4. Provide the final verified answer highlighted in bold.
5. If code, point out any bugs, explain time complexity, and provide the fixed code.
Keep formatting clean, structured, and easy to read on mobile screens.
"""

    suspend fun solveProblem(
        client: GeminiClient,
        bitmap: Bitmap?,
        extractedText: String?
    ): Result<String> {
        val userPrompt = buildString {
            append("Please analyze and solve this problem step-by-step.")
            if (!extractedText.isNullOrEmpty()) {
                append("\n\nExtracted Screen Text:\n").append(extractedText)
            }
        }
        return client.generateContent(
            prompt = userPrompt,
            bitmap = bitmap,
            systemInstruction = SOLVER_SYSTEM_INSTRUCTION
        )
    }
}
