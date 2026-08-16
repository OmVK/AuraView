package com.arora.assistant.core.ai

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class RagSearchResult(
    val fileName: String,
    val matchedSnippet: String,
    val relevanceScore: Float
)

object PersonalDocumentRag {

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
