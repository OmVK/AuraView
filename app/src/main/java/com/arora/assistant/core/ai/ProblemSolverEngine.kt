package com.arora.assistant.core.ai

import android.graphics.Bitmap

object ProblemSolverEngine {

    fun cleanMathFormatting(raw: String): String {
        return raw
            // Remove LaTeX display blocks $$ ... $$
            .replace(Regex("""\$\$(.+?)\$\$""")) { it.groupValues[1].trim() }
            // Remove LaTeX inline $ ... $
            .replace(Regex("""\$([^\$\n]+?)\$""")) { it.groupValues[1].trim() }
            // Convert common LaTeX math operators to readable unicode
            .replace("\\times", "×")
            .replace("\\div", "÷")
            .replace("\\pm", "±")
            .replace("\\cdot", "·")
            .replace("\\approx", "≈")
            .replace("\\neq", "≠")
            .replace("\\leq", "≤")
            .replace("\\geq", "≥")
            .replace(Regex("""\\sqrt\{([^\}]+)\}""")) { "√(${it.groupValues[1]})" }
            .replace(Regex("""\\frac\{([^\}]+)\}\{([^\}]+)\}""")) { "(${it.groupValues[1]} / ${it.groupValues[2]})" }
            .replace(Regex("""\\mathbf\{([^\}]+)\}""")) { it.groupValues[1] }
            .replace(Regex("""\\text\{([^\}]+)\}""")) { it.groupValues[1] }
            // Strip any remaining lone dollar signs used for math formatting
            .replace(Regex("""(?<=\s|^)\$(?=\w|\d)"""), "")
            .replace(Regex("""(?<=\w|\d)\$(?=\s|$)"""), "")
    }

    suspend fun solveProblem(
        geminiClient: GeminiClient? = null,
        groqClient: GroqClient? = null,
        bitmap: Bitmap? = null,
        extractedText: String? = null
    ): Result<String> {
        val prompt = buildString {
            append("Analyze this circled image:\n")
            append("- If it contains a math equation, formula, or calculation: State the **Final Answer** directly and clearly at the top, followed by concise step-by-step calculations.\n")
            append("- If it is a physical object or product: Identify what it is, its details, and purpose.\n")
            append("- If it is code or text: Explain or summarize it.\n")
            append("IMPORTANT: Use clean, plain text math notation (+, -, *, /, ^, =, √). Do NOT use LaTeX dollar signs ($ or $$).")
            if (!extractedText.isNullOrBlank()) {
                append("\nDetected text: \"").append(extractedText.trim()).append("\"")
            }
        }

        // 1. Try Groq LPU first if available
        if (groqClient != null) {
            val groqResult = groqClient.generateContent(
                prompt = prompt,
                bitmap = bitmap,
                systemInstruction = null,
                maxTokens = 2500,
                temperature = 0.1
            )
            if (groqResult.isSuccess && !groqResult.getOrNull().isNullOrBlank()) {
                return groqResult.map { cleanMathFormatting(it) }
            }
        }

        // 2. Fallback to Gemini
        if (geminiClient != null) {
            val result = geminiClient.generateContent(
                prompt = prompt,
                bitmap = bitmap,
                systemInstruction = null,
                maxTokens = 2500,
                temperature = 0.1
            )
            return result.map { cleanMathFormatting(it) }
        }

        return Result.failure(Exception("No AI client available (Gemini or Groq)"))
    }
}
