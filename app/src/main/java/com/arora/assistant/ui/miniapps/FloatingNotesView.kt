package com.arora.assistant.ui.miniapps

import android.content.Context
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.ui.components.GlassCard
import com.arora.assistant.ui.components.NeonButton
import com.arora.assistant.ui.theme.ElectricCyan
import com.arora.assistant.ui.theme.GlassSurfaceHigh
import com.arora.assistant.ui.theme.NeonEmerald
import com.arora.assistant.ui.theme.QuantumViolet
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun FloatingNotesView(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var noteText by remember { mutableStateOf("# Study Notes\n\n- Key concepts:\nQ: What is Arora?\nA: An intelligent overlay copilot.\n\n") }
    var isSpeaking by remember { mutableStateOf(false) }

    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Toolbar (TTS Read Aloud)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Markdown & Flashcard Editor", color = Color.Gray, fontSize = 11.sp)

            IconButton(
                onClick = {
                    if (isSpeaking) {
                        tts?.stop()
                        isSpeaking = false
                    } else {
                        tts?.speak(noteText, TextToSpeech.QUEUE_FLUSH, null, "note_tts")
                        isSpeaking = true
                    }
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.VolumeUp,
                    "Read Aloud",
                    tint = if (isSpeaking) NeonEmerald else ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

            // Note Text Area
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GlassSurfaceHigh,
                    unfocusedContainerColor = GlassSurfaceHigh,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = ElectricCyan,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            val appPreferences = remember { com.arora.assistant.core.data.AppPreferences(context) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var isSummarizing by remember { mutableStateOf(false) }

            Row(modifier = Modifier.fillMaxWidth()) {
                NeonButton(
                    text = "Save .md",
                    onClick = {
                        saveMarkdownFile(context, noteText)
                    },
                    icon = Icons.Default.Download,
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                NeonButton(
                    text = "Export Anki",
                    onClick = {
                        exportAnkiFlashcards(context, noteText)
                    },
                    icon = Icons.Default.FlashOn,
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                NeonButton(
                    text = if (isSummarizing) "..." else "⚡ AI Summary",
                    onClick = {
                        scope.launch {
                            isSummarizing = true
                            val apiKey = appPreferences.geminiApiKey.first()
                            if (apiKey.isBlank()) {
                                Toast.makeText(context, "Set Gemini API Key in Settings first", Toast.LENGTH_SHORT).show()
                                isSummarizing = false
                                return@launch
                            }
                            val client = com.arora.assistant.core.ai.GeminiClient(apiKey)
                            val summaryResult = com.arora.assistant.core.ai.SessionSummarizer.summarizeSession(context, client)
                            isSummarizing = false
                            summaryResult.onSuccess { summary ->
                                noteText = noteText + "\n\n## 📝 Session Summary\n" + summary
                                Toast.makeText(context, "Summary appended to notes", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "Summary error: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    isPrimary = true,
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
    }

private fun saveMarkdownFile(context: Context, text: String) {
    try {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AuraNotes")
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "Notes_$timestamp.md")
        file.writeText(text)
        Toast.makeText(context, "Saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun exportAnkiFlashcards(context: Context, text: String) {
    try {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AuraNotes")
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "Anki_Deck_$timestamp.tsv")
        
        // Parse Q&A pairs
        val lines = text.lines()
        val tsvBuilder = StringBuilder()
        var currentQ: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Q:") || trimmed.startsWith("Question:")) {
                currentQ = trimmed.substringAfter(":").trim()
            } else if ((trimmed.startsWith("A:") || trimmed.startsWith("Answer:")) && currentQ != null) {
                val ans = trimmed.substringAfter(":").trim()
                tsvBuilder.append("$currentQ\t$ans\n")
                currentQ = null
            }
        }

        if (tsvBuilder.isNotEmpty()) {
            file.writeText(tsvBuilder.toString())
            Toast.makeText(context, "Exported ${tsvBuilder.lines().filter { it.isNotBlank() }.size} flashcards to Anki TSV!", Toast.LENGTH_LONG).show()
        } else {
            // Fallback export whole text
            file.writeText("Note Summary\t${text.replace("\n", "<br>")}\n")
            Toast.makeText(context, "Exported deck to ${file.name}", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
