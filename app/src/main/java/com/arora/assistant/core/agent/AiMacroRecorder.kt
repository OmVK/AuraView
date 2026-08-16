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
    private var isRecording = false
    private val gson = Gson()

    fun startRecording() {
        recordedEvents.clear()
        isRecording = true
    }

    fun logEvent(type: String, text: String) {
        if (isRecording) {
            recordedEvents.add(UserDemonstrationEvent(type, text))
        }
    }

    fun stopRecording(): List<UserDemonstrationEvent> {
        isRecording = false
        return recordedEvents.toList()
    }

    suspend fun synthesizeMacro(
        client: GeminiClient,
        taskName: String
    ): Result<CustomTask> = withContext(Dispatchers.IO) {
        if (recordedEvents.isEmpty()) {
            return@withContext Result.failure(Exception("No recorded actions to compile"))
        }

        val eventLog = gson.toJson(recordedEvents)
        val prompt = """
You are an expert Automation Script Synthesizer.
The user demonstrated the following task: "$taskName"
Raw Event Log:
$eventLog

Generalize and compile this demonstration into a clean workflow plan.
Output a JSON with:
{
  "title": "$taskName",
  "trigger": "Gesture / User Trigger",
  "steps": [
    {"command": "Click: " + targetText},
    {"command": "Delay: 500ms"}
  ]
}
Output only JSON.
"""

        val result = client.generateContent(prompt)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        val steps = recordedEvents.mapIndexed { idx, ev ->
            TaskStep(
                id = idx.toString(),
                command = AroraCommand.LaunchApp(ev.targetText)
            )
        }

        Result.success(
            CustomTask(
                id = java.util.UUID.randomUUID().toString(),
                title = taskName,
                triggerDescription = "Compiled via AI Demonstration",
                steps = steps
            )
        )
    }
}
