package com.arora.assistant.core.agent

import com.arora.assistant.core.ai.GeminiClient
import com.arora.assistant.core.task.AroraCommand
import com.arora.assistant.core.task.CustomTask
import com.arora.assistant.core.task.TaskStep
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UserDemonstrationEvent(
    val eventType: String,
    val targetText: String,
    val timestamp: Long = System.currentTimeMillis()
)

object AiMacroRecorder {

    private val recordedEvents = mutableListOf<UserDemonstrationEvent>()
    var isRecording = false
        private set
    private val gson = Gson()

    const val MACRO_SYNTHESIS_PROMPT = """You observed a user perform a sequence of actions on their Android phone.
Generalize these actions into a structured explanation and reusable automation plan.

Output JSON:
{
  "macro_name": "[short descriptive title]",
  "description": "[what this sequence accomplishes]",
  "summary_steps": [
    "Step 1: Description",
    "Step 2: Description"
  ]
}"""

    fun startRecording() {
        recordedEvents.clear()
        isRecording = true
    }

    fun logEvent(type: String, text: String) {
        if (isRecording && text.isNotBlank()) {
            // Deduplicate consecutive identical events
            if (recordedEvents.isNotEmpty() && recordedEvents.last().eventType == type && recordedEvents.last().targetText == text) {
                return
            }
            recordedEvents.add(UserDemonstrationEvent(type, text))
        }
    }

    fun stopRecording(): List<UserDemonstrationEvent> {
        isRecording = false
        return recordedEvents.toList()
    }

    fun getEventCount(): Int = recordedEvents.size

    suspend fun synthesizeMacro(
        client: GeminiClient,
        taskName: String
    ): Result<CustomTask> = withContext(Dispatchers.IO) {
        if (recordedEvents.isEmpty()) {
            return@withContext Result.failure(Exception("No recorded actions to compile. Tap 'Record', perform actions on screen, and tap 'Stop'."))
        }

        val eventLog = gson.toJson(recordedEvents)
        val prompt = """$MACRO_SYNTHESIS_PROMPT

Recorded actions:
$eventLog

User's description: "$taskName""""

        val result = client.generateContent(prompt)
        val summaryText = if (result.isSuccess) {
            result.getOrNull() ?: ""
        } else ""

        val steps = recordedEvents.mapIndexed { idx, ev ->
            TaskStep(
                id = idx.toString(),
                command = if (ev.eventType == "INPUT") AroraCommand.CopyToClipboard(ev.targetText) else AroraCommand.Delay(300)
            )
        }

        Result.success(
            CustomTask(
                id = java.util.UUID.randomUUID().toString(),
                title = taskName,
                triggerDescription = "Recorded ${recordedEvents.size} UI actions:\n" + recordedEvents.take(8).mapIndexed { i, e -> "${i + 1}. ${e.eventType}: ${e.targetText}" }.joinToString("\n") + if (recordedEvents.size > 8) "\n...and ${recordedEvents.size - 8} more" else "",
                steps = steps
            )
        )
    }
}
