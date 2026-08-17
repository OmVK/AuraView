package com.arora.assistant.core.ai

import android.os.Environment
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class RagSearchResult(
    val fileName: String,
    val matchedSnippet: String,
    val relevanceScore: Float
)

data class RagReRankResponse(
    @SerializedName("ranked_indices") val rankedIndices: List<Int>,
    @SerializedName("best_excerpt") val bestExcerpt: String,
    @SerializedName("answer") val answer: String?
)

object PersonalDocumentRag {

    private val gson = Gson()

    const val RAG_RERANK_PROMPT = """You are a Document RAG Re-ranking and Synthesis Engine.
Re-rank the retrieved document excerpts from most to least relevant to the user's query.

Output JSON only:
{
  "ranked_indices": [1, 2, 3],
  "best_excerpt": "[copy the single most relevant sentence from the best result]",
  "answer": "[direct answer to the query if the excerpts contain it, else null]"
}"""

    suspend fun reRankResults(
        client: GeminiClient,
        query: String,
        results: List<RagSearchResult>
    ): Result<RagReRankResponse> = withContext(Dispatchers.IO) {
        if (results.isEmpty()) return@withContext Result.failure(Exception("No results to re-rank"))

        val excerptsDump = buildString {
            results.forEachIndexed { index, res ->
                append("${index + 1}. [${res.fileName}]: ${res.matchedSnippet}\n")
            }
        }

        val prompt = """$RAG_RERANK_PROMPT

The user searched for: "$query"

These document excerpts were found by keyword search:
$excerptsDump"""

        val response = client.generateContent(prompt)
        if (response.isFailure) return@withContext Result.failure(response.exceptionOrNull()!!)

        val cleanJson = response.getOrNull()?.removePrefix("```json")?.removePrefix("```")?.removeSuffix("```")?.trim() ?: "{}"
        try {
            val parsed = gson.fromJson(cleanJson, RagReRankResponse::class.java)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Searches local user study files (PDFs, Markdown notes, TXT, code files in Documents/Downloads).
     * Retrieves exact matching snippets to answer screen queries.
     */
    suspend fun searchLocalKnowledgeBase(query: String, maxResults: Int = 3): List<RagSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<RagSearchResult>()
        val searchTerms = query.lowercase().split("\\s+".toRegex()).filter { it.length > 2 }
        if (searchTerms.isEmpty()) return@withContext emptyList()

        val searchDirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AuraNotes"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.walkTopDown()
                .maxDepth(3)
                .filter { it.isFile && it.extension.lowercase() in listOf("txt", "md", "json", "kt", "py", "java", "csv") }
                .take(50)

            for (file in files) {
                try {
                    val content = file.readText().take(50000)
                    val contentLower = content.lowercase()

                    var matchCount = 0
                    for (term in searchTerms) {
                        if (contentLower.contains(term)) {
                            matchCount++
                        }
                    }

                    if (matchCount > 0) {
                        // Find first term index for preview window
                        val firstTerm = searchTerms.firstOrNull { contentLower.contains(it) } ?: searchTerms[0]
                        val index = contentLower.indexOf(firstTerm)
                        val start = (index - 60).coerceAtLeast(0)
                        val end = (index + 200).coerceAtMost(content.length)
                        val snippet = "..." + content.substring(start, end).replace("\n", " ") + "..."

                        results.add(
                            RagSearchResult(
                                fileName = file.name,
                                matchedSnippet = snippet,
                                relevanceScore = matchCount.toFloat() / searchTerms.size
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Ignore unreadable files
                }
            }
        }

        results.sortedByDescending { it.relevanceScore }.take(maxResults)
    }
}
